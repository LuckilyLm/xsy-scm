package com.xsy.scm.order.domain.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 订单抽屉里的整单粘贴预览：只匹配商品、核对价格，不建单。
 */
@Data
public class SalesOrderPasteForm {
    @NotNull(message = "客户不能为空")
    private Long customerId;

    /** 仅用于价格解析的仓；不写入订单，最终建单仍按抽屉选仓。 */
    private Long warehouseId;

    @Valid
    @NotEmpty(message = "粘贴内容不能为空")
    @Size(max = 100, message = "单次粘贴不能超过100行")
    private List<Row> rows;

    @Data
    public static class Row {
        private String productName;
        private String specName;
        private String unit;
        private String quantity;
        private String unitPrice;
        private String remark;
    }
}
