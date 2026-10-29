package com.xsy.scm.report.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.xsy.scm.report.constant.ScmReportPermission;
import com.xsy.scm.report.domain.form.ScmNegativeStockSnapshotForm;
import com.xsy.scm.report.domain.vo.ScmNegativeStockSnapshotVO;
import com.xsy.scm.report.service.ScmNegativeStockSnapshotService;
import com.xsy.scm.report.support.ScmReportExcel;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/scm/report/inventory/negative-stock/snapshot")
@RequiredArgsConstructor
public class ScmNegativeStockSnapshotController {
    private final ScmNegativeStockSnapshotService snapshotService;
    @PostMapping("/create")
    @SaCheckPermission(value = {ScmReportPermission.INVENTORY_SNAPSHOT_CREATE, ScmReportPermission.INVENTORY_QUERY,
        ScmReportPermission.COST_QUERY}, mode = SaMode.AND)
    @OperateLog
    public ResponseDTO<ScmNegativeStockSnapshotVO> create(@Valid @RequestBody ScmNegativeStockSnapshotForm.Create form,
        @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseDTO.ok(snapshotService.create(form, key));
    }
    @PostMapping("/query")
    @SaCheckPermission(value = {ScmReportPermission.INVENTORY_QUERY, ScmReportPermission.COST_QUERY}, mode = SaMode.AND)
    public ResponseDTO<PageResult<ScmNegativeStockSnapshotVO>> query(
        @Valid @RequestBody ScmNegativeStockSnapshotForm.Query form) {
        return ResponseDTO.ok(snapshotService.query(form));
    }
    @GetMapping("/{id}")
    @SaCheckPermission(value = {ScmReportPermission.INVENTORY_QUERY, ScmReportPermission.COST_QUERY}, mode = SaMode.AND)
    public ResponseDTO<ScmNegativeStockSnapshotVO> detail(@PathVariable Long id) {
        return ResponseDTO.ok(snapshotService.detail(id));
    }
    @PostMapping("/{id}/export")
    @SaCheckPermission(
        value = {ScmReportPermission.INVENTORY_QUERY, ScmReportPermission.COST_QUERY, ScmReportPermission.EXPORT},
        mode = SaMode.AND)
    @OperateLog
    public void export(@PathVariable Long id, HttpServletResponse response) throws IOException {
        var snapshot = snapshotService.detail(id);
        List<String> titles = List.of("留存编号", "留存时间", "仓库", "商品", "规格", "单位", "负数量", "余额均价", "估算金额");
        List<List<Object>> rows = new ArrayList<>();
        for (var row : snapshot.getRows()) {
            rows.add(ScmReportExcel.row(titles, snapshot.getSnapshotNo(), snapshot.getCapturedAt(),
                snapshot.getWarehouseName(), row.path("productName").asText(""), row.path("specName").asText(""),
                row.path("unit").asText(""), row.path("quantity").asText(""), row.path("avgCost").asText(""),
                row.path("estimatedAmount").asText("")));
        }
        ScmReportExcel.write(response, "负库存月度留存.xlsx", "月度留存", titles, rows);
    }
}
