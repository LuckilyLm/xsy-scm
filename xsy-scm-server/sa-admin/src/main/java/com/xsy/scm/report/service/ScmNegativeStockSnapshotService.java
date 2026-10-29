package com.xsy.scm.report.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.error.ScmCommonErrorCode;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.idempotency.ScmIdempotencyService;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.scope.ScmWarehouseScopeGuard;
import com.xsy.scm.common.util.ScmDocumentNumbers;
import com.xsy.scm.report.constant.ReportErrorCode;
import com.xsy.scm.report.dao.ReportDao;
import com.xsy.scm.report.dao.ScmNegativeStockSnapshotDao;
import com.xsy.scm.report.domain.form.ScmNegativeStockReportQueryForm;
import com.xsy.scm.report.domain.form.ScmNegativeStockSnapshotForm;
import com.xsy.scm.report.domain.vo.ScmNegativeStockSnapshotVO;
import com.xsy.scm.report.support.ScmReportExportGuard;
import com.xsy.scm.warehouse.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ScmNegativeStockSnapshotService {
    private final ScmNegativeStockSnapshotDao snapshotDao;
    private final ReportDao reportDao;
    private final ScmDataScopeService dataScopeService;
    private final ScmWarehouseScopeGuard warehouseScopeGuard;
    private final WarehouseService warehouseService;
    private final ScmIdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @Transactional(isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
    public ScmNegativeStockSnapshotVO create(ScmNegativeStockSnapshotForm.Create form, String key) {
        requireCost();
        warehouseScopeGuard.require(form.getWarehouseId());
        var claim = idempotencyService.claim("NEGATIVE_STOCK_SNAPSHOT", key, form);
        if (claim.replay()) {
            return detail(idempotencyService.replay(claim, Long.class));
        }
        var warehouse = warehouseService.require(form.getWarehouseId());
        var capturedAt = OffsetDateTime.now();
        ScmNegativeStockReportQueryForm query = new ScmNegativeStockReportQueryForm();
        query.setWarehouseId(form.getWarehouseId());
        var rows = ScmReportExportGuard.exportRows(size -> reportDao.negativeStockList(new Page<>(1, size, false),
            query, dataScopeService.resolve().getWarehouseScope()));
        ScmNegativeStockSnapshotVO snapshot = new ScmNegativeStockSnapshotVO();
        snapshot.setSnapshotNo(ScmDocumentNumbers.format("NS", snapshotDao.nextNumber()));
        snapshot.setWarehouseId(form.getWarehouseId());
        snapshot.setWarehouseName(warehouse.getName());
        snapshot
            .setSnapshotMonth(capturedAt.atZoneSameInstant(ZoneId.of("Asia/Shanghai")).toLocalDate().withDayOfMonth(1));
        snapshot.setCapturedAt(capturedAt);
        snapshot.setRowCount((long) rows.size());
        snapshot.setZeroCostRowCount(
            rows.stream().filter(row -> row.getAvgCost() == null || row.getAvgCost().signum() == 0).count());
        snapshot.setEstimatedAmount(
            rows.stream().map(row -> row.getEstimatedAmount() == null ? BigDecimal.ZERO : row.getEstimatedAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        snapshot.setRemark(form.getRemark());
        snapshot.setCreatedBy(ScmOperator.current());
        try {
            snapshot.setPayloadJson(objectMapper.writeValueAsString(rows));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("负库存留存数据无法序列化", e);
        }
        snapshotDao.insert(snapshot);
        idempotencyService.complete(claim, "NEGATIVE_STOCK_SNAPSHOT", snapshot.getId(), snapshot.getId());
        return detail(snapshot.getId());
    }

    @Transactional(readOnly = true)
    public PageResult<ScmNegativeStockSnapshotVO> query(ScmNegativeStockSnapshotForm.Query form) {
        requireCost();
        SalesReportService.rejectClientSort(form);
        if (form.getMonth() == null || form.getMonth().getDayOfMonth() != 1) {
            throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
        }
        var scope = dataScopeService.resolve().getWarehouseScope();
        if (scope.isEmpty()) {
            return ScmDataScopeService.emptyPage(form);
        }
        if (form.getWarehouseId() != null) {
            warehouseScopeGuard.require(form.getWarehouseId());
        }
        Page<?> page = SmartPageUtil.convert2PageQuery(form);
        return SmartPageUtil.convert2PageResult(page, snapshotDao.query(page, form, scope));
    }

    @Transactional(readOnly = true)
    public ScmNegativeStockSnapshotVO detail(Long id) {
        requireCost();
        var snapshot = snapshotDao.find(id);
        if (snapshot == null || !dataScopeService.resolve().getWarehouseScope().allows(snapshot.getWarehouseId())) {
            throw new ScmBusinessException(ReportErrorCode.REPORT_STATEMENT_UNAVAILABLE);
        }
        try {
            snapshot.setRows(objectMapper.readTree(snapshot.getPayloadJson()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("负库存留存数据无法读取", e);
        }
        return snapshot;
    }

    private void requireCost() {
        if (!dataScopeService.resolve().isCostVisible()) {
            throw new ScmDataScopeException();
        }
    }
}
