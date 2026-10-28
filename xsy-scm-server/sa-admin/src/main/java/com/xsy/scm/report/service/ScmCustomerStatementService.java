package com.xsy.scm.report.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.finance.constant.ScmFinanceBusinessTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceEntryTypeEnum;
import com.xsy.scm.print.constant.ScmStatementFactType;
import com.xsy.scm.report.constant.ReportErrorCode;
import com.xsy.scm.report.dao.ScmCustomerStatementDao;
import com.xsy.scm.report.domain.form.ScmCustomerStatementForm;
import com.xsy.scm.report.domain.vo.ScmCustomerStatementItemVO;
import com.xsy.scm.report.domain.vo.ScmCustomerStatementVO;
import com.xsy.scm.report.support.ScmReportTimeRange;
import com.xsy.scm.report.support.ScmReportTimeRangeResolver;
import lombok.RequiredArgsConstructor;

/**
 * 只生成对账单版本；源头的财务记录保持不变，仍是权威数据。
 */
@Service
@RequiredArgsConstructor
public class ScmCustomerStatementService {
    private static final int MAX_FACTS = 10_000;

    /**
     * 抹零事实类型，与 {@code ScmStatementFactType} 及对账 SQL 的 {@code fact_type} 取值一致。
     *
     * <p>
     * 单列出来是因为抹零没有资金流入：它是应收减少，不是核销分配，不能并进 {@code writeOffNet}。
     */
    private static final String FACT_WAIVER = ScmStatementFactType.WAIVER.name();
    private static final String FACT_WAIVER_REVERSE = ScmStatementFactType.WAIVER_REVERSE.name();
    private final ScmCustomerStatementDao statementDao;
    private final ScmDataScopeService dataScopeService;

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ScmCustomerStatementVO freeze(ScmCustomerStatementForm form) {
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        ScmDataScopeContext scope = dataScopeService.resolve();
        requireScope(scope);
        List<ScmCustomerStatementItemVO> facts = statementDao.selectEvents(form, range.endAt(), scope, null,
            MAX_FACTS + 1);
        if (facts.size() > MAX_FACTS) {
            throw new ScmBusinessException(ReportErrorCode.REPORT_STATEMENT_TOO_LARGE);
        }
        if (facts.isEmpty()) {
            throw new ScmBusinessException(ReportErrorCode.REPORT_STATEMENT_EMPTY);
        }
        ScmCustomerStatementVO result = new ScmCustomerStatementVO();
        String settlementName = statementDao.selectSettlementName(form.getSettlementCustomerId());
        if (settlementName == null) {
            throw new ScmBusinessException(ReportErrorCode.REPORT_STATEMENT_UNAVAILABLE);
        }
        result.setSettlementCustomerId(form.getSettlementCustomerId());
        result.setSettlementCustomerName(settlementName);
        result.setCustomerId(form.getCustomerId());
        result.setStartDate(form.getStartDate());
        result.setEndDate(form.getEndDate());
        result.setPartialScope(form.getCustomerId() != null || !scope.getWarehouseScope().isAll()
            || !scope.getOrderSellerScope().isAll() || !scope.getCustomerSellerScope().isAll());
        result.setGeneratedByEmployeeId(scope.getEmployeeId());
        result.setGeneratedAt(OffsetDateTime.now());
        BigDecimal debt = BigDecimal.ZERO;
        BigDecimal unallocated = BigDecimal.ZERO;
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal red = BigDecimal.ZERO;
        BigDecimal writeOff = BigDecimal.ZERO;
        BigDecimal waiver = BigDecimal.ZERO;
        BigDecimal receipt = BigDecimal.ZERO;
        BigDecimal refund = BigDecimal.ZERO;
        List<ScmCustomerStatementItemVO> lines = new ArrayList<>();
        for (ScmCustomerStatementItemVO fact : facts) {
            BigDecimal debtChange = fact.getReceivableDelta();
            BigDecimal fundsChange = fact.getReceiptDelta().subtract(fact.getWriteOffDelta());
            if (fact.getEventAt().isBefore(range.startAt())) {
                debt = debt.add(debtChange);
                unallocated = unallocated.add(fundsChange);
                continue;
            }
            if (result.getOpeningReceivable() == null) {
                result.setOpeningReceivable(debt);
                result.setOpeningUnallocated(unallocated);
            }
            debt = debt.add(debtChange);
            unallocated = unallocated.add(fundsChange);
            if (ScmFinanceBusinessTypeEnum.RECEIVABLE.name().equals(fact.getFactType())) {
                revenue = revenue.add(debtChange);
            } else if (ScmFinanceEntryTypeEnum.RED.name().equals(fact.getFactType())) {
                red = red.subtract(debtChange);
            }
            if (isWaiver(fact.getFactType())) {
                // 抹零是「应收直接被免掉」，用应收变动的反号表达净额；它与核销变动无关
                waiver = waiver.subtract(debtChange);
            }
            writeOff = writeOff.add(fact.getWriteOffDelta());
            receipt = receipt.add(fact.getReceiptDelta());
            refund = refund.add(fact.getRefundDelta());
            fact.setLineNo(lines.size() + 1);
            fact.setReceivableBalance(debt);
            lines.add(fact);
        }
        if (result.getOpeningReceivable() == null) {
            result.setOpeningReceivable(debt);
            result.setOpeningUnallocated(unallocated);
        }
        result.setReceivableIncrease(revenue);
        result.setReceivableRed(red);
        result.setWriteOffNet(writeOff);
        result.setWaiverNet(waiver);
        result.setReceiptNet(receipt);
        result.setRefundNet(refund);
        result.setClosingReceivable(debt);
        if (result.getPartialScope()) {
            // 已核销的收款可能属于其他不可见的客户 / 订单，局部范围不对未核销余额下结论。
            result.setOpeningUnallocated(null);
            result.setClosingUnallocated(null);
        } else {
            result.setClosingUnallocated(unallocated);
        }
        statementDao.insertStatement(result);
        for (ScmCustomerStatementItemVO fact : facts) {
            statementDao.insertSource(result.getId(), fact.getFactType(), fact.getFactId());
        }
        for (ScmCustomerStatementItemVO line : lines) {
            line.setStatementId(result.getId());
            statementDao.insertItem(line);
        }
        result.setItems(lines);
        return result;
    }

    @Transactional(readOnly = true)
    public ScmCustomerStatementVO detail(Long id) {
        ScmDataScopeContext scope = dataScopeService.resolve();
        requireScope(scope);
        ScmCustomerStatementVO version = statementDao.selectStatement(id, scope.getEmployeeId());
        if (version == null) {
            throw new ScmBusinessException(ReportErrorCode.REPORT_STATEMENT_UNAVAILABLE);
        }
        version.setItems(statementDao.selectItems(id));
        version.setWaiverNet(waiverNet(version.getItems()));
        verifyCurrentScope(version, scope);
        return version;
    }

    /**
     * 打印专用读取：不要求当前员工是生成人，但必须仍能按当前数据范围看到这份对账单的全部源事实。
     *
     * <p>
     * 普通详情接口仍只返回生成人自己的版本；这里放宽的只是「谁生成的」，不是「谁能看」。
     */
    @Transactional(readOnly = true)
    public ScmCustomerStatementVO loadForPrint(Long id) {
        ScmDataScopeContext scope = dataScopeService.resolve();
        requireScope(scope);
        ScmCustomerStatementVO version = statementDao.selectStatementById(id);
        if (version == null) {
            throw new ScmBusinessException(ReportErrorCode.REPORT_STATEMENT_UNAVAILABLE);
        }
        version.setItems(statementDao.selectItems(id));
        version.setWaiverNet(waiverNet(version.getItems()));
        verifyCurrentScope(version, scope);
        return version;
    }

    @Transactional(readOnly = true)
    public List<ScmCustomerStatementVO> history(Long settlementCustomerId) {
        ScmDataScopeContext scope = dataScopeService.resolve();
        requireScope(scope);
        List<ScmCustomerStatementVO> visible = new ArrayList<>();
        for (ScmCustomerStatementVO version : statementDao.selectHistory(settlementCustomerId, scope.getEmployeeId())) {
            if (isStillVisible(version, scope)) {
                visible.add(version);
            }
        }
        return visible;
    }

    private void verifyCurrentScope(ScmCustomerStatementVO version, ScmDataScopeContext scope) {
        if (!isStillVisible(version, scope)) {
            throw new ScmBusinessException(ReportErrorCode.REPORT_STATEMENT_UNAVAILABLE);
        }
    }

    private boolean isStillVisible(ScmCustomerStatementVO version, ScmDataScopeContext scope) {
        ScmCustomerStatementForm form = new ScmCustomerStatementForm();
        form.setSettlementCustomerId(version.getSettlementCustomerId());
        form.setCustomerId(version.getCustomerId());
        form.setStartDate(version.getStartDate());
        form.setEndDate(version.getEndDate());
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        List<ScmCustomerStatementItemVO> now = statementDao.selectEvents(form, range.endAt(), scope,
            version.getGeneratedAt(), MAX_FACTS + 1);
        if (now.size() > MAX_FACTS) {
            return false;
        }
        Set<String> visibleFacts = new HashSet<>();
        now.forEach(row -> visibleFacts.add(row.getFactType() + ":" + row.getFactId()));
        return statementDao.selectSources(version.getId()).stream()
            .allMatch(row -> visibleFacts.contains(row.getFactType() + ":" + row.getFactId()));
    }

    /**
     * 从明细事实汇总抹零净额：抹零行的应收变动是负数，取反就是本期免掉的金额。
     */
    private static BigDecimal waiverNet(List<ScmCustomerStatementItemVO> items) {
        if (items == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal net = BigDecimal.ZERO;
        for (ScmCustomerStatementItemVO item : items) {
            if (isWaiver(item.getFactType()) && item.getReceivableDelta() != null) {
                net = net.subtract(item.getReceivableDelta());
            }
        }
        return net;
    }

    private static boolean isWaiver(String factType) {
        return FACT_WAIVER.equals(factType) || FACT_WAIVER_REVERSE.equals(factType);
    }

    private void requireScope(ScmDataScopeContext scope) {
        if (scope.getEmployeeId() == null || scope.getWarehouseScope().isEmpty()
            || scope.getOrderSellerScope().isEmpty() || scope.getCustomerSellerScope().isEmpty()) {
            throw new ScmBusinessException(ReportErrorCode.REPORT_STATEMENT_UNAVAILABLE);
        }
    }
}
