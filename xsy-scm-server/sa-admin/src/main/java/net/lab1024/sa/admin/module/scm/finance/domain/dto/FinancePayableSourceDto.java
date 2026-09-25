package net.lab1024.sa.admin.module.scm.finance.domain.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 应付生成所需的**收货确认事实**（单头维度），由 {@code FinancePayableSourceDao} 只读取得。
 *
 * <p>字段全部来自 {@code purchase_receipt} 的已落库列，没有一个是在服务层现算或现取的：
 * {@code confirmedAt} 必须是这次收货确认留下的事实本身，否则「业务已确认、财务时刻却是后来补的」
 * 这种账就无法解释（与 W6 库存流水取 {@code confirmed_at} 同一纪律）。
 */
@Data
public class FinancePayableSourceDto {

    private Long purchaseReceiptId;

    private Long purchaseOrderId;

    private Long supplierId;

    /**
     * 收货事实上的供应商名称快照，只服务展示与导出，不参与关联。
     */
    private String supplierNameSnapshot;

    /**
     * 应付的事件时点（第一批 Q9）：{@code DIRECT} 与 {@code WAREHOUSE_CONFIRM} 同口径，
     * putaway 不决定应付时点。刻意取库里的列而不是在服务层现取 {@code now()} ——
     * 重试与补生成时「现在」不是「当时」。
     */
    private OffsetDateTime confirmedAt;
}
