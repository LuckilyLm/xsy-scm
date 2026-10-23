package com.xsy.scm.purchase.domain.form;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.xsy.scm.common.json.ScmStrictDecimalStringDeserializer;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

import lombok.Data;

public final class PurchaseInquiryForm {
    private PurchaseInquiryForm() {
    }

    @Data
    public static class Create {
        @NotNull(message = "净需求批次不能为空")
        @Positive(message = "净需求批次编号必须大于零")
        private Long batchId;
        @NotNull(message = "商品规格不能为空")
        @Positive(message = "商品规格编号必须大于零")
        private Long skuId;
        @NotBlank(message = "需求单位不能为空")
        @Size(max = 32, message = "需求单位不能超过32个字符")
        private String demandUnit;
        @NotNull(message = "报价截止时间不能为空")
        private OffsetDateTime deadlineAt;
    }

    @Data
    public static class Quote {
        @NotNull(message = "询价单不能为空")
        @Positive(message = "询价单编号必须大于零")
        private Long inquiryId;
        @NotNull(message = "供应商不能为空")
        @Positive(message = "供应商编号必须大于零")
        private Long supplierId;
        @NotNull(message = "询价单版本不能为空")
        @Min(value = 0, message = "版本不能小于零")
        private Integer version;
        @NotBlank(message = "报价单价不能为空")
        @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
        private String unitPrice;
        @NotNull(message = "报价有效期不能为空")
        private OffsetDateTime validUntil;
        @Size(max = 500, message = "备注不能超过500个字符")
        private String remark;
    }

    @Data
    public static class Accept {
        @NotNull(message = "询价单不能为空")
        @Positive(message = "询价单编号必须大于零")
        private Long inquiryId;
        @NotNull(message = "报价记录不能为空")
        @Positive(message = "报价记录编号必须大于零")
        private Long quoteId;
        @NotNull(message = "询价单版本不能为空")
        @Min(value = 0, message = "版本不能小于零")
        private Integer version;
    }

    @Data
    public static class Batch {
        @NotNull(message = "净需求批次不能为空")
        @Positive(message = "净需求批次编号必须大于零")
        private Long batchId;
        @Min(value = 1, message = "页码不能小于1")
        private Integer page;
    }

    @Data
    public static class History {
        @NotNull(message = "询价单不能为空")
        @Positive(message = "询价单编号必须大于零")
        private Long inquiryId;
        @Positive(message = "历史游标必须大于零")
        private Long beforeId;
    }
}
