package net.lab1024.sa.admin.module.scm.inventory.domain.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 新建 / 编辑待审核报损报溢单。
 *
 * <p>创建与编辑共用同一表单：只有「待审核」可改，因此两者字段集合完全一致
 * （与出库单、盘点单同一取向）。
 *
 * <p><b>{@code adjustType} 是单据级方向</b>，行上不表达方向：一张单要么全报损、要么全报溢。
 * 正则白名单与 {@code ScmInventoryLossGainTypeEnum} / {@code ck_inventory_loss_gain_type} 同源。
 *
 * <p><b>{@code reason} 必填</b>：报损是「把货从账上抹掉」，没有原因的单据审批人无从判断，
 * 「留痕」也就没有内容。DB 层另有 {@code ck_inventory_loss_gain_reason} 再挡一次空串。
 */
@Data
public class InventoryLossGainAddForm {

    /** 调整类型：{@code LOSS} 报损 / {@code OVERFLOW} 报溢。 */
    @NotBlank
    @Pattern(regexp = "LOSS|OVERFLOW")
    private String adjustType;

    @NotNull
    private Long warehouseId;

    @NotBlank
    @Size(max = 200)
    private String reason;

    @Size(max = 500)
    private String remark;

    @NotEmpty
    @Valid
    private List<Item> items;

    /** 明细行。数量恒为正 —— 方向由单据的 {@code adjustType} 决定。 */
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
