package com.xsy.scm.inventory.domain.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 新建 / 编辑草稿调拨单。
 *
 * <p>创建与编辑共用同一表单：调拨单只有草稿态可改，因此「新建」与「编辑草稿」
 * 的字段集合完全一致。
 *
 * <p><b>数量口径</b>：{@code quantity} 必须为正、最多 4 位小数（{@code NUMERIC(18,4)}）。
 * 方向由「发出 / 收货」动作表达，数量本身恒为正。
 *
 * <p><b>源仓与目标仓相同</b>不在表单校验里判（那需要跨字段校验），由服务层给出
 * 可归因的 41042 —— 表单校验的错误码是 40000，信息量更少。
 */
@Data
public class InventoryTransferAddForm {

    @NotNull(message = "调出仓库不能为空")
    private Long fromWarehouseId;

    @NotNull(message = "调入仓库不能为空")
    private Long toWarehouseId;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;

    @NotEmpty(message = "调拨明细不能为空")
    @Valid
    private List<Item> items;

    /**
     * 调拨明细行。
     */
    @Data
    public static class Item {

        @NotNull(message = "SKU 不能为空")
        private Long skuId;

        @NotNull(message = "调拨数量不能为空")
        @DecimalMin(value = "0", inclusive = false, message = "调拨数量必须大于0")
        @Digits(integer = 14, fraction = 4, message = "调拨数量最多14位整数和4位小数")
        private BigDecimal quantity;

        @Size(max = 500, message = "备注不能超过500个字符")
        private String remark;
    }
}
