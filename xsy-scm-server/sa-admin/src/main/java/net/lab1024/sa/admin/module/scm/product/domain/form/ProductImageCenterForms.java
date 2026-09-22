package net.lab1024.sa.admin.module.scm.product.domain.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 图片中心批量维护入参集合：绑定 / 移除 / 设主图 / 排序，均以 SPU 为上下文。 */
public final class ProductImageCenterForms {
    private ProductImageCenterForms() {
    }

    @Data
    public static class BindItem {
        @NotNull @Positive
        private Long spuId;
        @NotNull
        private String fileKey;
        private Boolean primaryFlag = false;
        private Integer sortOrder = 0;
    }

    @Data
    public static class BatchBindForm {
        @NotEmpty @Valid
        private List<BindItem> items = new ArrayList<>();
    }

    @Data
    public static class BatchRemoveForm {
        @NotNull @Positive
        private Long spuId;
        @NotEmpty
        private List<@NotNull @Positive Long> imageIds = new ArrayList<>();
    }

    @Data
    public static class SetPrimaryForm {
        @NotNull @Positive
        private Long spuId;
        @NotNull @Positive
        private Long imageId;
    }

    @Data
    public static class ReorderForm {
        @NotNull @Positive
        private Long spuId;
        /** 该 SPU 全部现存图片的目标顺序，必须与集合一一对应。 */
        @NotEmpty
        private List<@NotNull @Positive Long> orderedImageIds = new ArrayList<>();
    }
}
