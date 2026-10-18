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

/** 确认收货必须提交该收货单的全部活动明细，不能只提交部分行。服务端校验每行实际数量、单位及版本后再统一确认。 */
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
        /**
         * 本行登记的批次：批次仓必填，且数量之和必须等于本次收货量；非批次仓必须为空。
         */
        @Valid
        @Size(max = 100, message = "同一收货行最多登记100个批次")
        private List<Lot> lots;
    }

    /**
     * 收货行上的一个批次：来源生产批号由供应商或现场提供，允许为空；数量必填。
     */
    @Data
    public static class Lot {
        @Size(max = 64, message = "来源生产批号不能超过64个字符")
        private String sourceLotNo;
        @NotBlank(message = "批次数量不能为空")
        @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
        private String quantity;
    }
}
