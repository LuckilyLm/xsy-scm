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
import com.xsy.scm.report.dao.ScmSupplierStatementDao;
import com.xsy.scm.report.domain.form.ScmSupplierStatementForm;
import com.xsy.scm.report.domain.vo.ScmSupplierStatementItemVO;
import com.xsy.scm.report.domain.vo.ScmSupplierStatementVO;
import com.xsy.scm.report.support.ScmReportTimeRange;
import com.xsy.scm.report.support.ScmReportTimeRangeResolver;
import lombok.RequiredArgsConstructor;

/** Read-side snapshot of supplier payables and independently recorded supplier payments. */
@Service
@RequiredArgsConstructor
public class ScmSupplierStatementService {
    private static final int MAX_FACTS = 10_000;
    private final ScmSupplierStatementDao statementDao;
    private final ScmDataScopeService dataScopeService;

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ScmSupplierStatementVO freeze(ScmSupplierStatementForm form) {
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        ScmDataScopeContext scope = dataScopeService.resolve();
        requireScope(form.getWarehouseId(), scope);
        OffsetDateTime snapshotAt = statementDao.selectSnapshotAt();
        List<ScmSupplierStatementItemVO> facts = statementDao.selectEvents(form, range.endAt(), scope, snapshotAt,
                MAX_FACTS + 1);
        if (facts.size() > MAX_FACTS) {
            throw new ScmBusinessException(ReportErrorCode.REPORT_STATEMENT_TOO_LARGE);
        }
        if (facts.isEmpty()) {
            throw new ScmBusinessException(ReportErrorCode.REPORT_STATEMENT_EMPTY);
        }
        String supplierName = statementDao.selectSupplierName(form.getSupplierId());
        if (supplierName == null) {
            throw new ScmBusinessException(ReportErrorCode.REPORT_STATEMENT_UNAVAILABLE);
        }
        ScmSupplierStatementVO result = new ScmSupplierStatementVO();
        result.setSupplierId(form.getSupplierId());
        result.setSupplierName(supplierName);
        result.setWarehouseId(form.getWarehouseId());
        result.setStartDate(form.getStartDate());
        result.setEndDate(form.getEndDate());
        result.setPartialScope(form.getWarehouseId() != null || !scope.getWarehouseScope().isAll()
                || !scope.getPurchaserScope().isAll());
        result.setGeneratedByEmployeeId(scope.getEmployeeId());
        result.setGeneratedAt(snapshotAt);
        BigDecimal payable = BigDecimal.ZERO;
        BigDecimal unallocated = BigDecimal.ZERO;
        BigDecimal increase = BigDecimal.ZERO;
        BigDecimal red = BigDecimal.ZERO;
        BigDecimal payment = BigDecimal.ZERO;
        BigDecimal writeOff = BigDecimal.ZERO;
        List<ScmSupplierStatementItemVO> lines = new ArrayList<>();
        for (ScmSupplierStatementItemVO fact : facts) {
            BigDecimal payableChange = fact.getPayableDelta();
            BigDecimal fundsChange = fact.getPaymentDelta().subtract(fact.getWriteOffDelta());
            if (fact.getEventAt().isBefore(range.startAt())) {
                payable = payable.add(payableChange);
                unallocated = unallocated.add(fundsChange);
                continue;
            }
            if (result.getOpeningPayable() == null) {
                result.setOpeningPayable(payable);
                result.setOpeningUnallocated(unallocated);
            }
            payable = payable.add(payableChange);
            unallocated = unallocated.add(fundsChange);
            if (ScmFinanceBusinessTypeEnum.PAYABLE.name().equals(fact.getFactType())) {
                increase = increase.add(payableChange);
            } else if (ScmFinanceEntryTypeEnum.RED.name().equals(fact.getFactType())) {
                red = red.subtract(payableChange);
            }
            writeOff = writeOff.add(fact.getWriteOffDelta());
            payment = payment.add(fact.getPaymentDelta());
            fact.setLineNo(lines.size() + 1);
            fact.setPayableBalance(payable);
            lines.add(fact);
        }
        if (result.getOpeningPayable() == null) {
            result.setOpeningPayable(payable);
            result.setOpeningUnallocated(unallocated);
        }
        result.setPayableIncrease(increase);
        result.setPayableRed(red);
        result.setWriteOffNet(writeOff);
        result.setPaymentNet(payment);
        result.setClosingPayable(payable);
        if (result.getPartialScope()) {
            // Payments have no purchaser or warehouse ownership. Do not call a partial allocation a cash balance.
            result.setOpeningUnallocated(null);
            result.setClosingUnallocated(null);
        } else {
            result.setClosingUnallocated(unallocated);
        }
        statementDao.insertStatement(result);
        for (ScmSupplierStatementItemVO fact : facts) {
            statementDao.insertSource(result.getId(), fact.getFactType(), fact.getFactId());
        }
        for (ScmSupplierStatementItemVO line : lines) {
            line.setStatementId(result.getId());
            statementDao.insertItem(line);
        }
        result.setItems(lines);
        return result;
    }

    @Transactional(readOnly = true)
    public ScmSupplierStatementVO detail(Long id) {
        ScmDataScopeContext scope = dataScopeService.resolve();
        ScmSupplierStatementVO version = statementDao.selectStatement(id, scope.getEmployeeId());
        if (version == null) {
            throw new ScmBusinessException(ReportErrorCode.REPORT_STATEMENT_UNAVAILABLE);
        }
        requireScope(version.getWarehouseId(), scope);
        if (!isStillVisible(version, scope)) {
            throw new ScmBusinessException(ReportErrorCode.REPORT_STATEMENT_UNAVAILABLE);
        }
        version.setItems(statementDao.selectItems(id));
        return version;
    }

    @Transactional(readOnly = true)
    public List<ScmSupplierStatementVO> history(Long supplierId) {
        ScmDataScopeContext scope = dataScopeService.resolve();
        requireScope(null, scope);
        List<ScmSupplierStatementVO> visible = new ArrayList<>();
        long offset = 0;
        while (visible.size() < 50) {
            List<ScmSupplierStatementVO> page = statementDao.selectHistory(supplierId, scope.getEmployeeId(), offset);
            for (ScmSupplierStatementVO version : page) {
                if ((version.getWarehouseId() == null || scope.getWarehouseScope().allows(version.getWarehouseId()))
                        && isStillVisible(version, scope)) {
                    visible.add(version);
                    if (visible.size() == 50) {
                        break;
                    }
                }
            }
            if (page.size() < 50) {
                break;
            }
            offset += page.size();
        }
        return visible;
    }

    private boolean isStillVisible(ScmSupplierStatementVO version, ScmDataScopeContext scope) {
        ScmSupplierStatementForm form = new ScmSupplierStatementForm();
        form.setSupplierId(version.getSupplierId());
        form.setWarehouseId(version.getWarehouseId());
        form.setStartDate(version.getStartDate());
        form.setEndDate(version.getEndDate());
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        List<ScmSupplierStatementItemVO> now = statementDao.selectEvents(form, range.endAt(), scope,
                version.getGeneratedAt(), MAX_FACTS + 1);
        if (now.size() > MAX_FACTS) {
            return false;
        }
        Set<String> visibleFacts = new HashSet<>();
        now.forEach(row -> visibleFacts.add(row.getFactType() + ":" + row.getFactId()));
        return statementDao.selectSources(version.getId()).stream()
                .allMatch(row -> visibleFacts.contains(row.getFactType() + ":" + row.getFactId()));
    }

    private void requireScope(Long warehouseId, ScmDataScopeContext scope) {
        if (scope.getEmployeeId() == null || scope.getWarehouseScope().isEmpty()
                || scope.getPurchaserScope().isEmpty()
                || (warehouseId != null && !scope.getWarehouseScope().allows(warehouseId))) {
            throw new ScmBusinessException(ReportErrorCode.REPORT_STATEMENT_UNAVAILABLE);
        }
    }
}
