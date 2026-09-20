package net.lab1024.sa.admin.module.scm.product.domain.form;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.OffsetDateTime;
import java.util.*;
@Data
@EqualsAndHashCode(callSuper=true)
public class ProductSpuQueryForm extends net.lab1024.sa.base.common.domain.PageParam {
@Size(max=150) private String keyword;
@Positive private Long categoryId;
@Pattern(regexp="ON_SHELF|OFF_SHELF") private String status;
@Pattern(regexp="ON_SHELF|OFF_SHELF") private String skuStatus;
@Pattern(regexp="STANDARD|NON_STANDARD") private String productType;
@Pattern(regexp="ENABLED|DISABLED|ARCHIVED") private String masterStatus;
@Pattern(regexp="AMBIENT|CHILLED|FROZEN") private String storageMethod;
/** 命中任一标签即返回（或语义），与分类的多选筛选保持一致。 */
@Size(max=50) private List<@NotNull @Positive Long> tagIds;
private Boolean hasPrimaryImage;
private Boolean hasBarcode;
private OffsetDateTime createdFrom;
private OffsetDateTime createdTo;
@Override @Min(1) public Long getPageNum() { return super.getPageNum(); }
@Override @Min(1) public Long getPageSize() { return super.getPageSize(); }
}
