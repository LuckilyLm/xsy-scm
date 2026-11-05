package com.xsy.scm.product.domain.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 图片中心批量维护入参集合：绑定 / 移除 / 设主图 / 排序，均以 SPU 为上下文。
 */
public final class ProductImageCenterForms {
    private ProductImageCenterForms() {
    }

    @Data
    public static class BindItem {
        @NotNull(message = "商品ID不能为空")
        @Positive(message = "商品ID必须大于0")
        private Long spuId;
        @NotNull(message = "图片文件标识不能为空")
        private String fileKey;
        private Boolean primaryFlag = false;
        private Integer sortOrder = 0;
    }

    @Data
    public static class BatchBindForm {
        @NotEmpty(message = "批量项目列表不能为空")
        @Valid
        private List<BindItem> items = new ArrayList<>();
    }

    @Data
    public static class BatchRemoveForm {
        @NotNull(message = "商品ID不能为空")
        @Positive(message = "商品ID必须大于0")
        private Long spuId;
        @NotEmpty(message = "图片 ID 列表不能为空")
        private List<
            @NotNull(message = "图片 ID 不能为空") @Positive(message = "图片 ID 必须大于0") Long> imageIds = new ArrayList<>();
    }

    @Data
    public static class SetPrimaryForm {
        @NotNull(message = "商品ID不能为空")
        @Positive(message = "商品ID必须大于0")
        private Long spuId;
        @NotNull(message = "图片 ID不能为空")
        @Positive(message = "图片 ID必须大于0")
        private Long imageId;
    }

    /**
     * 批量匹配只需要上传文件名里的商品编码；上限与前端一次选图数量一致，避免超长 IN 列表。
     */
    @Data
    public static class MatchTargetsForm {
        @NotEmpty(message = "商品编码列表不能为空")
        @Size(max = 500, message = "一次最多匹配 500 个商品编码")
        private List<@NotBlank(message = "商品编码不能为空") @Size(max = 64,
            message = "商品编码不能超过 64 个字符") String> codes = new ArrayList<>();
    }

    @Data
    public static class ReorderForm {
        @NotNull(message = "商品ID不能为空")
        @Positive(message = "商品ID必须大于0")
        private Long spuId;
        /**
         * 该 SPU 全部现存图片的目标顺序，必须与集合一一对应。
         */
        @NotEmpty(message = "排序图片 ID 列表不能为空")
        private List<
            @NotNull(message = "排序ID必填") @Positive(message = "图片ID须大于0") Long> orderedImageIds = new ArrayList<>();
    }
}
