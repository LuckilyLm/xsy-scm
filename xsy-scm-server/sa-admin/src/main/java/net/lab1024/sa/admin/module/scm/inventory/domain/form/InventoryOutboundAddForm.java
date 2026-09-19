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
 * 新建 / 编辑草稿出库单。
 *
 * <p>创建与编辑共用同一表单：出库单只有草稿态可改，因此「新建」与「编辑草稿」
 * 的字段集合完全一致，拆成两个类只会制造两份需要同步维护的校验规则。
 *
 * <p><b>数量口径</b>：{@code quantity} 必须为正、最多 4 位小数（{@code NUMERIC(18,4)}）。
 * 出库方向由流水类型表达，数量本身恒为正 —— 不接受负数表示「出库」。
 */
@Data
public class InventoryOutboundAddForm {

    @NotNull
    private Long warehouseId;

    @Size(max = 500)
    private String remark;

    @NotEmpty
    @Valid
    private List<Item> items;

    /** 出库明细行。 */
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
