package com.xsy.scm.purchase.domain.form;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.xsy.scm.common.validation.ScmEnumValue;
import com.xsy.scm.purchase.constant.ScmReceiptModeEnum;

/**
 * 新建采购收货单。收货行由服务端按采购单的全部活动行自动生成（草稿不产生任何副作用）， 因此表单只需要采购单 id、入库方式与备注。
 *
 * <p>
 * 不允许直接填状态，状态由 {@code confirm} 命令驱动；入库方式必选、无默认值。
 */
@Data
public class PurchaseReceiptCreateForm {
    @NotNull(message = "采购单不能为空")
    private Long purchaseOrderId;
    @NotNull(message = "收货入库方式不能为空")
    @ScmEnumValue(enumClass = ScmReceiptModeEnum.class, message = "收货入库方式无效")
    private String receiptMode;
    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;
}
