package com.xsy.scm.product.domain.form;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * REPLACE 用 tagIds 覆盖商品现有标签；ADD / REMOVE 只增删列出的标签。
 */
@Data
public class ProductSpuBatchTagForm {
    @NotEmpty(message = "批量项目列表不能为空")
    @Size(max = 200, message = "批量项目列表不能超过200项")
    @Valid
    private List<ProductBatchItemForm> items;
    @NotNull(message = "标签 ID 列表不能为空")
    @Size(max = 50, message = "标签 ID 列表不能超过50项")
    private List<
            @NotNull(message = "标签 ID 列表不能为空")
            @Positive(message = "标签 ID 列表必须大于0")
            Long> tagIds = new ArrayList<>();
    @NotBlank(message = "标签维护方式不能为空")
    @Pattern(regexp = "REPLACE|ADD|REMOVE", message = "标签维护方式取值无效")
    private String mode;

    /**
     * 只有 REPLACE 允许空集合，语义是清空标签；ADD / REMOVE 空集合是一次无效果命令。
     */
    @AssertTrue(message = "ADD 与 REMOVE 必须至少选择一个标签")
    @JsonIgnore
    public boolean isTagSelectionPresent() {
        return tagIds == null || "REPLACE".equals(mode) || !tagIds.isEmpty();
    }
}
