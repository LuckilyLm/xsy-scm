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
import com.xsy.scm.report.constant.ReportErrorCode;
import com.xsy.scm.report.dao.ScmCustomerStatementDao;
import com.xsy.scm.report.domain.form.ScmCustomerStatementForm;
import com.xsy.scm.report.domain.vo.ScmCustomerStatementItemVO;
import com.xsy.scm.report.domain.vo.ScmCustomerStatementVO;
import com.xsy.scm.report.support.ScmReportTimeRange;
import com.xsy.scm.report.support.ScmReportTimeRangeResolver;
import lombok.RequiredArgsConstructor;

/** Snapshot only: the source Finance facts stay immutable and authoritative. */
@Service
@RequiredArgsConstructor
public class ScmCustomerStatementService {
    private static final int MAX_FACTS = 10_000;
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
        result.setReceiptNet(receipt);
        result.setRefundNet(refund);
        result.setClosingReceivable(debt);
        if (result.getPartialScope()) {
            // Applied receipts may belong to another, invisible customer/order; a partial view cannot reconcile cash.
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

    private void requireScope(ScmDataScopeContext scope) {
        if (scope.getEmployeeId() == null || scope.getWarehouseScope().isEmpty()
                || scope.getOrderSellerScope().isEmpty() || scope.getCustomerSellerScope().isEmpty()) {
            throw new ScmBusinessException(ReportErrorCode.REPORT_STATEMENT_UNAVAILABLE);
        }
    }
}
