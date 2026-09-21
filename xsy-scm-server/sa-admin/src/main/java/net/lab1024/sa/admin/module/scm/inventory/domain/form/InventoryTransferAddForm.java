package net.lab1024.sa.admin.module.scm.inventory.domain.form;

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

    @NotNull
    private Long fromWarehouseId;

    @NotNull
    private Long toWarehouseId;

    @Size(max = 500)
    private String remark;

    @NotEmpty
    @Valid
    private List<Item> items;

    /**
     * 调拨明细行。
     */
    @Data
    public static class Item {

        @NotNull
        private Long skuId;

        @NotNull
        @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 14, fraction = 4)
        private BigDecimal quantity;

        @Size(max = 500)
        private String remark;
    }
}
