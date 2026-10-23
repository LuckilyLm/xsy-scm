package com.xsy.scm.finance.domain.dto;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 生成应付的收货时间和操作人来自已确认收货记录，财务不重新生成时间戳；金额按已保存的采购收货数据确定。
 */
@Data
public class FinancePayableSourceDto {

    private Long purchaseReceiptId;

    private Long purchaseOrderId;

    private Long supplierId;

    /**
     * 收货记录上的供应商名称，只用于展示与导出，不参与关联。
     */
    private String supplierNameSnapshot;

    /**
     * 应付的事件时点：{@code DIRECT} 与 {@code WAREHOUSE_CONFIRM} 一致，上架不决定应付时点。刻意取库里的列而不是在服务层取 {@code now()}： 重试与补生成时「现在」不是「当时」。
     */
    private OffsetDateTime confirmedAt;
}
