package com.xsy.scm.purchase.domain.form;

import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.xsy.scm.common.json.ScmStrictDecimalStringDeserializer;

/** 一条采购商品规格可分配多条采购需求，按明细内的分配列表提交；同一明细与需求组合不能重复。需求单位与采购单位分别保存，单位不一致时不自动换算。 */
@Data
public class PurchaseOrderAddForm {
    @NotNull(message = "供应商不能为空")
    private Long supplierId;
    private Long purchaserId;
    @NotNull(message = "仓库不能为空")
    private Long warehouseId;
    private LocalDate plannedArrivalDate;
    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;
    @Valid
    @NotEmpty(message = "采购明细不能为空")
    @Size(max = 500, message = "采购明细不能超过500项")
    private List<Item> items;

    /**
     * 采购单行：一个 SKU，可挂 N 条需求分配。
     */
    @Data
    public static class Item {
        /**
         * 保留行必填；新增行为空。
         */
        private Long id;
        /**
         * 保留行必填（{@code PURCHASE_ITEM_VERSION_REQUIRED}）。
         */
        @Min(value = 0, message = "版本号不能小于0")
        private Integer version;
        @NotNull(message = "商品规格不能为空")
        private Long skuId;
        @NotBlank(message = "采购数量不能为空")
        @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
        private String quantity;
        @NotBlank(message = "采购单价不能为空")
        @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
        private String price;
        @Valid
        @Size(max = 100, message = "需求分配列表不能超过100项")
        private List<Allocation> allocations;
    }

    /**
     * 需求分配：{@code (purchase_order_item_id, purchase_demand_id)} 是 allocation 的身份。
     */
    @Data
    public static class Allocation {
        @NotNull(message = "采购需求不能为空")
        private Long demandId;
        @NotBlank(message = "采购数量不能为空")
        @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
        private String quantity;
        /**
         * 需求版本，必填（{@code PURCHASE_DEMAND_VERSION_REQUIRED}）。
         */
        @NotNull(message = "需求版本不能为空")
        @Min(value = 0, message = "需求版本不能小于0")
        private Integer demandVersion;
    }
}
