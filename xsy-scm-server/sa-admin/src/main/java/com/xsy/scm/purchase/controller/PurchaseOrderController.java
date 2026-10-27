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
import com.xsy.scm.purchase.domain.form.PurchaseOrderLockForm;
import com.xsy.scm.purchase.domain.form.PurchasePriceForm;
import com.xsy.scm.purchase.domain.form.PurchaseOrderQueryForm;
import com.xsy.scm.purchase.domain.form.PurchaseOrderReassignForm;
import com.xsy.scm.purchase.domain.form.PurchaseOrderShortCloseForm;
import com.xsy.scm.purchase.domain.form.PurchaseOrderUpdateForm;
import com.xsy.scm.purchase.domain.form.PurchaseOrderVersionForm;
import com.xsy.scm.purchase.domain.vo.PurchaseOperationLogVO;
import com.xsy.scm.purchase.domain.vo.PurchaseOrderItemVO;
import com.xsy.scm.purchase.domain.vo.PurchaseOrderVO;
import com.xsy.scm.purchase.service.PurchaseOrderExportSupport;
import com.xsy.scm.purchase.service.PurchaseOrderLockService;
import com.xsy.scm.purchase.service.PurchaseOrderService;
import com.xsy.scm.purchase.service.PurchasePriceService;
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
 * 采购查询和操作日志使用不同权限。创建等幂等命令由业务层校验 Idempotency-Key；修改依赖行版本检查，不额外使用幂等键。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/scm/purchase")
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    private final PurchaseQueryService purchaseQueryService;

    private final PurchasePriceService purchasePriceService;

    private final PurchaseOrderLockService purchaseOrderLockService;

    private static final int EXPORT_MAX_ROWS = 100000;

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
     * 采购单列表导出（只读）：复用 {@code scm:purchase:query}，一次取「第 1 页 + 上限行」的当前筛选结果，按前端勾选列和 {@link PurchaseOrderExportSupport}
     * 的列目录生成动态表头 xlsx。与列表页共用同一投影，导出内容与列表可见内容一致，不修改任何采购状态。
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
        FastExcel.write(response.getOutputStream()).head(PurchaseOrderExportSupport.head(form.getExportColumns()))
            .autoCloseStream(Boolean.FALSE).sheet("采购单")
            .doWrite(PurchaseOrderExportSupport.rows(form.getExportColumns(), orders));
    }

    /**
     * 补价：待定价行补上价格。窗口内、未锁单；带幂等键，重放不会写第二条审计。
     */
    @PostMapping("/item/{itemId}/price/fill")
    @SaCheckPermission(PurchasePermission.PRICE_FILL)
    @OperateLog
    public ResponseDTO<PurchaseOrderVO> fillPrice(@PathVariable("itemId") Long itemId,
        @Valid @RequestBody PurchasePriceForm form,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseDTO.ok(purchasePriceService.fill(itemId, form, idempotencyKey));
    }

    /**
     * 改价：首次收货确认前调整价格；需要改价权限、必须填原因，锁单后仍可使用（这就是「锁后改价」的专门口径）。
     */
    @PostMapping("/item/{itemId}/price/adjust")
    @SaCheckPermission(PurchasePermission.PRICE_ADJUST)
    @OperateLog
    public ResponseDTO<PurchaseOrderVO> adjustPrice(@PathVariable("itemId") Long itemId,
        @Valid @RequestBody PurchasePriceForm form,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseDTO.ok(purchasePriceService.adjust(itemId, form, idempotencyKey));
    }

    @PostMapping("/lock")
    @SaCheckPermission(PurchasePermission.LOCK)
    @OperateLog
    public ResponseDTO<PurchaseOrderVO> lock(@Valid @RequestBody PurchaseOrderLockForm form) {
        return ResponseDTO.ok(purchaseOrderLockService.lock(form));
    }

    /** 解锁必须说明原因：把已对账的单重新打开是需要解释的动作。 */
    @PostMapping("/unlock")
    @SaCheckPermission(PurchasePermission.UNLOCK)
    @OperateLog
    public ResponseDTO<PurchaseOrderVO> unlock(@Valid @RequestBody PurchaseOrderLockForm form) {
        return ResponseDTO.ok(purchaseOrderLockService.unlock(form));
    }

    @PostMapping("/create")
    @SaCheckPermission(PurchasePermission.ADD)
    @OperateLog
    public ResponseDTO<PurchaseOrderVO> create(@Valid @RequestBody PurchaseOrderAddForm form,
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
     * 改派采购归属（{@code scm:purchase:assign}，与「新建时指定别人」是同一项权利）。
     *
     * <p>
     * 单独一个端点，不混在 {@code /update} 里：归属决定数据范围，改派必须是一个显式、可单独授权、可审计的动作。 不接幂等头，重复提交由 {@code id + version} 乐观锁挡住。
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
    public ResponseDTO<PurchaseOrderVO> submit(@Valid @RequestBody PurchaseOrderVersionForm form,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseDTO.ok(purchaseOrderService.submit(form, idempotencyKey));
    }

    @PostMapping("/cancel")
    @SaCheckPermission(PurchasePermission.CANCEL)
    @OperateLog
    public ResponseDTO<PurchaseOrderVO> cancel(@Valid @RequestBody PurchaseOrderCancelForm form,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseDTO.ok(purchaseOrderService.cancel(form, idempotencyKey));
    }

    @PostMapping("/short-close")
    @SaCheckPermission(PurchasePermission.SHORT_CLOSE)
    @OperateLog
    public ResponseDTO<PurchaseOrderVO> shortClose(@Valid @RequestBody PurchaseOrderShortCloseForm form,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseDTO.ok(purchaseOrderService.shortClose(form, idempotencyKey));
    }

    /**
     * 批量少收关单：整批共享原因，同一事务内逐单执行与单张单相同的校验，任一单非法即整批回滚。 不接幂等头，批量本身原子。
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
