package net.lab1024.sa.admin.module.scm.product.domain.form;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.*;
/** REPLACE 用 tagIds 覆盖商品现有标签；ADD / REMOVE 只增删列出的标签。 */
@Data
public class ProductSpuBatchTagForm {
@NotEmpty @Size(max=200) @Valid private List<ProductBatchItemForm> items;
@NotNull @Size(max=50) private List<@NotNull @Positive Long> tagIds = new ArrayList<>();
@NotBlank @Pattern(regexp="REPLACE|ADD|REMOVE") private String mode;

    /** 只有 REPLACE 允许空集合，语义是清空标签；ADD / REMOVE 空集合是一次无效果命令。 */
    @AssertTrue(message="ADD 与 REMOVE 必须至少选择一个标签")
    @JsonIgnore
    public boolean isTagSelectionPresent() { return tagIds==null || "REPLACE".equals(mode) || !tagIds.isEmpty(); }
}
