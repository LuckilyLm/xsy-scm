package com.xsy.scm.purchase.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.idev.excel.FastExcel;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.purchase.domain.form.PurchaseOrderAddForm;
import com.xsy.scm.purchase.domain.form.PurchaseOrderBatchDeleteForm;
import com.xsy.scm.purchase.domain.form.PurchaseOrderBatchShortCloseForm;
import com.xsy.scm.purchase.domain.form.PurchaseOrderCancelForm;
import com.xsy.scm.purchase.domain.form.PurchaseOrderDeleteForm;
import com.xsy.scm.purchase.domain.form.PurchaseOrderExportForm;
import com.xsy.scm.purchase.domain.form.PurchaseOrderQueryForm;
import com.xsy.scm.purchase.domain.form.PurchaseOrderReassignForm;
import com.xsy.scm.purchase.domain.form.PurchaseOrderShortCloseForm;
import com.xsy.scm.purchase.domain.form.PurchaseOrderUpdateForm;
import com.xsy.scm.purchase.domain.form.PurchaseOrderVersionForm;
import com.xsy.scm.purchase.domain.vo.PurchaseOperationLogVO;
import com.xsy.scm.purchase.domain.vo.PurchaseOrderItemVO;
import com.xsy.scm.purchase.domain.vo.PurchaseOrderVO;
import com.xsy.scm.purchase.service.PurchaseOrderExportSupport;
import com.xsy.scm.purchase.service.PurchaseOrderService;
import com.xsy.scm.purchase.service.PurchaseQueryService;
import com.xsy.scm.purchase.permission.PurchasePermission;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.util.SmartResponseUtil;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

/**
 * 采购单端点（W5 Target Design §7.1 的 11 个端点）。
 *
 * <p>查询类走 `scm:purchase:query`，日志单独一个 `scm:purchase:log:query`
 * （审计数据与业务数据分权，与 W4 的 `scm:order:log:query` 一致）。
 *
 * <p>`Idempotency-Key` 头一律声明为 {@code required = false}：缺失时由
 * {@code PurchaseIdempotencyService} 抛 40084，若标成必填会被框架转成 30001，
 * 与 §7.7 的错误码契约不符（同 {@code PurchaseDemandController}）。
 *
 * <p>`update` **不带**幂等头 —— 它靠行级 `@Version` 保证重复提交安全（§4.2 T2）。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/scm/purchase")
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    private final PurchaseQueryService purchaseQueryService;

    private static final int EXPORT_MAX_ROWS = 100000;

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    @PostMapping("/query")
    @SaCheckPermission(PurchasePermission.QUERY)
    public ResponseDTO<PageResult<PurchaseOrderVO>> query(@Valid @RequestBody PurchaseOrderQueryForm form) {
        return ResponseDTO.ok(purchaseQueryService.orderQuery(form));
    }

    @GetMapping("/detail/{id}")
    @SaCheckPermission(PurchasePermission.QUERY)
    public ResponseDTO<PurchaseOrderVO> detail(@PathVariable("id") Long purchaseOrderId) {
        return ResponseDTO.ok(purchaseQueryService.orderDetail(purchaseOrderId));
    }

    @GetMapping("/item/{orderId}")
    @SaCheckPermission(PurchasePermission.QUERY)
    public ResponseDTO<List<PurchaseOrderItemVO>> items(@PathVariable Long orderId) {
        return ResponseDTO.ok(purchaseQueryService.orderItems(orderId));
    }

    @GetMapping("/log/{orderId}")
    @SaCheckPermission(PurchasePermission.LOG_QUERY)
    public ResponseDTO<List<PurchaseOperationLogVO>> logs(@PathVariable Long orderId) {
        return ResponseDTO.ok(purchaseQueryService.orderLogs(orderId));
    }

    /**
     * 采购单列表导出（Wave 2B §6.4，只读）：复用 {@code scm:purchase:query}，一次取「第 1 页 + 上限行」的
     * 当前筛选结果，按前端勾选的列（{@link PurchaseOrderExportSupport} 目录裁决）落动态表头 xlsx。
     * 与列表页共用同一投影，导出内容 == 列表可见内容；<b>不触碰任何采购状态</b>（§6.8）。
     */
    @PostMapping("/export")
    @SaCheckPermission(PurchasePermission.QUERY)
    @OperateLog
    public void export(@Valid @RequestBody PurchaseOrderExportForm form, HttpServletResponse response)
            throws IOException {
        form.setPageNum(1L);
        form.setPageSize((long) EXPORT_MAX_ROWS);
        List<PurchaseOrderVO> orders = purchaseQueryService.orderQuery(form).getList();
        SmartResponseUtil.setDownloadFileHeader(response, "采购单导出.xlsx", null);
        FastExcel.write(response.getOutputStream())
                .head(PurchaseOrderExportSupport.head(form.getExportColumns()))
                .autoCloseStream(Boolean.FALSE)
                .sheet("采购单")
                .doWrite(PurchaseOrderExportSupport.rows(form.getExportColumns(), orders));
    }

    // ------------------------------------------------------------------
    // 命令
    // ------------------------------------------------------------------

    @PostMapping("/create")
    @SaCheckPermission(PurchasePermission.ADD)
    @OperateLog
    public ResponseDTO<PurchaseOrderVO> create(
            @Valid @RequestBody PurchaseOrderAddForm form,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseDTO.ok(purchaseOrderService.create(form, idempotencyKey));
    }

    @PostMapping("/update")
    @SaCheckPermission(PurchasePermission.UPDATE)
    @OperateLog
    public ResponseDTO<PurchaseOrderVO> update(@Valid @RequestBody PurchaseOrderUpdateForm form) {
        return ResponseDTO.ok(purchaseOrderService.update(form));
    }

    /**
     * 改派采购归属（{@code scm:purchase:assign}，与「新建时指定别人」同一项权利）。
     *
     * <p>单独一个端点，而不是把改派混在 {@code /update} 里：归属同时是**数据范围依据**，
     * 谁把它换成了谁必须是一个显式、单独可授权、可审计的动作。
     * 不接幂等头，与 {@code /update} 同一取向 —— 重复提交由 {@code id + version} 乐观锁挡住。
     */
    @PostMapping("/reassign")
    @SaCheckPermission(PurchasePermission.ASSIGN)
    @OperateLog
    public ResponseDTO<PurchaseOrderVO> reassign(@Valid @RequestBody PurchaseOrderReassignForm form) {
        return ResponseDTO.ok(purchaseOrderService.reassign(form));
    }

    @PostMapping("/submit")
    @SaCheckPermission(PurchasePermission.SUBMIT)
    @OperateLog
    public ResponseDTO<PurchaseOrderVO> submit(
            @Valid @RequestBody PurchaseOrderVersionForm form,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseDTO.ok(purchaseOrderService.submit(form, idempotencyKey));
    }

    @PostMapping("/cancel")
    @SaCheckPermission(PurchasePermission.CANCEL)
    @OperateLog
    public ResponseDTO<PurchaseOrderVO> cancel(
            @Valid @RequestBody PurchaseOrderCancelForm form,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseDTO.ok(purchaseOrderService.cancel(form, idempotencyKey));
    }

    @PostMapping("/short-close")
    @SaCheckPermission(PurchasePermission.SHORT_CLOSE)
    @OperateLog
    public ResponseDTO<PurchaseOrderVO> shortClose(
            @Valid @RequestBody PurchaseOrderShortCloseForm form,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseDTO.ok(purchaseOrderService.shortClose(form, idempotencyKey));
    }

    /**
     * 批量少收关单（Wave 2B §6.3）：整批共享原因，在同一事务内逐单套用与单单完全相同的合法性 / 版本校验，
     * 任一单非法即整批回滚。复用 {@code scm:purchase:short-close} 权限；不接幂等头（批量本身原子）。
     */
    @PostMapping("/batch/short-close")
    @SaCheckPermission(PurchasePermission.SHORT_CLOSE)
    @OperateLog
    public ResponseDTO<String> batchShortClose(@Valid @RequestBody PurchaseOrderBatchShortCloseForm form) {
        purchaseOrderService.batchShortClose(form);
        return ResponseDTO.ok();
    }

    @PostMapping("/delete")
    @SaCheckPermission(PurchasePermission.DELETE)
    @OperateLog
    public ResponseDTO<String> delete(@Valid @RequestBody PurchaseOrderDeleteForm form) {
        purchaseOrderService.delete(form);
        return ResponseDTO.ok();
    }

    @PostMapping("/batch-delete")
    @SaCheckPermission(PurchasePermission.DELETE)
    @OperateLog
    public ResponseDTO<String> batchDelete(@Valid @RequestBody PurchaseOrderBatchDeleteForm form) {
        purchaseOrderService.batchDelete(form);
        return ResponseDTO.ok();
    }
}
