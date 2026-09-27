package com.xsy.scm.purchase.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 改派采购归属（{@code scm:purchase:assign}）。
 *
 * <p>
 * {@code version} 是乐观锁谓词：改派必须基于自己读到的那张单， 否则会把别人刚做的编辑（或上一次改派）静默覆盖。
 *
 * <p>
 * {@code purchaserId} 允许为 {@code null}，语义是「收回归属、留作未分配」， 未分配单据只有全量采购范围的岗位可见；不是「不改动」。
 */
@Data
public class PurchaseOrderReassignForm {

    @NotNull(message = "采购单 ID 不能为空")
    private Long id;

    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;

    private Long purchaserId;

    @Size(max = 500, message = "改派原因不能超过500个字符")
    private String reason;
}
