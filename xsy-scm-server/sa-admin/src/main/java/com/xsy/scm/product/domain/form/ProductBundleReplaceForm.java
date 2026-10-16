package com.xsy.scm.product.domain.form;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.xsy.scm.common.json.ScmStrictDecimalStringDeserializer;
import com.xsy.scm.common.util.ScmDecimalStrings;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class ProductBundleReplaceForm {
    @NotNull(message = "组合商品规格不能为空")
    private Long bundleSkuId;
    @NotNull(message = "组合商品规格版本不能为空")
    @Min(value = 0, message = "组合商品规格版本不能小于0")
    private Integer version;
    @NotEmpty(message = "组合商品至少包含一个组成规格")
    @Size(max = 50, message = "组合商品最多包含50个组成规格")
    private List<@Valid Component> components;

    @Data
    public static class Component {
        @NotNull(message = "组成商品规格不能为空")
        private Long skuId;
        @NotNull(message = "每套组成数量不能为空")
        @Pattern(regexp = ScmDecimalStrings.PATTERN, message = "每套组成数量格式不正确")
        @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
        private String quantityPerBundleSaleUnit;
    }
}
