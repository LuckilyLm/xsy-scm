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
 * 新建 / 编辑草稿盘点单。
 *
 * <p>创建与编辑共用同一表单：盘点单只有草稿态可改，因此两者字段集合完全一致，
 * 拆成两个类只会制造两份需要同步维护的校验规则（与出库单同一取向）。
 *
 * <p><b>为什么不接受 {@code bookQuantity}</b>：账面量由服务端在保存时从余额行读取并快照。
 * 让客户端提交账面量等于把「账」交给调用方定义 —— 那样盘点就能凭空制造差异。
 *
 * <p><b>{@code actualQuantity} 允许为 0</b>（确实一件不剩），但不允许为负：
 * 负数实盘量没有物理含义，它只会被用来制造异常的方向。
 */
@Data
public class InventoryStocktakeAddForm {

    @NotNull
    private Long warehouseId;

    @Size(max = 500)
    private String remark;

    @NotEmpty
    @Valid
    private List<Item> items;

    /**
     * 盘点明细行。
     */
    @Data
    public static class Item {

        @NotNull
        private Long skuId;

        /**
         * 实盘量：允许 0，不允许负。
         */
        @NotNull
        @DecimalMin(value = "0", inclusive = true)
        @Digits(integer = 14, fraction = 4)
        private BigDecimal actualQuantity;

        @Size(max = 500)
        private String remark;
    }
}
