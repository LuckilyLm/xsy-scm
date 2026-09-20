package net.lab1024.sa.admin.module.scm.product.domain.form;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
/** 辅助资料列表查询条件；字典规模有限，不启用分页。 */
@Data
public class ProductAssistantQueryForm {
@Size(max=64) private String keyword;
@Pattern(regexp="ENABLED|DISABLED") private String status;
}
