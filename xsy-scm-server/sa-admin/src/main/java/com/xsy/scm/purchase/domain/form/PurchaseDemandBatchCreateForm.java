package com.xsy.scm.purchase.domain.form;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
public class PurchaseDemandBatchCreateForm {
    @NotNull(message = "开始时间不能为空")
    private OffsetDateTime startAt;
    @NotNull(message = "结束时间不能为空")
    private OffsetDateTime endAt;
    @NotNull(message = "仓库不能为空")
    private Long warehouseId;
    private Long supplierId;
    private Long purchaserId;
    private Long categoryId;
    @Size(max = 64, message = "搜索关键词不能超过64个字符")
    private String keyword;
}
