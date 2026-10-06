package com.xsy.scm.purchase.domain.form;

import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.xsy.scm.common.json.ScmStrictDecimalStringDeserializer;

/**
 * 确认采购收货。
 *
 * <p>
 * <b>必须提交本收货单的全部活动行</b>（{@code PURCHASE_RECEIPT_ITEM_INCOMPLETE(40998)}），不允许只提交子集 —— 修 /G11 的「确认了但仍有 0 数量行」歧义。
 *
 * <p>
 * 标品：{@code actualWeight} / {@code weightSource} / {@code correctionReason} 必须全空；非标品：{@code actualWeight} 必填且 &gt;
 * 0，{@code weightSource} 必须 == {@code MANUAL}，有效数量取实重。
 */
@Data
public class PurchaseReceiptConfirmForm {
    @NotNull(message = "收货单 ID 不能为空")
    private Long id;
    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
    @Valid
    @NotEmpty(message = "收货明细不能为空")
    @Size(max = 500, message = "收货明细不能超过500项")
    private List<Item> items;

    /**
     * 收货单行的本次确认数据。
     */
    @Data
    public static class Item {
        @NotNull(message = "收货单明细不能为空")
        private Long receiptItemId;
        @NotNull(message = "版本号不能为空")
        @Min(value = 0, message = "版本号不能小于0")
        private Integer version;
        @NotBlank(message = "本次收货数量不能为空")
        @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
        private String receivedQuantity;
        /**
         * 非标品必填且 &gt; 0；标品必须为空（不是 {@code 0.0000}）。
         */
        @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
        private String actualWeight;
        /**
         * 非标品必须为 {@code MANUAL}；标品必须为空。
         */
        @Pattern(regexp = "MANUAL", message = "非标品称重来源当前只允许手工录入")
        private String weightSource;
        @Size(max = 500, message = "修正原因不能超过500个字符")
        private String correctionReason;
    }
}
