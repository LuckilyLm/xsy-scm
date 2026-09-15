package net.lab1024.sa.admin.module.scm.product.domain.form;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.*;
import java.math.BigDecimal;
@Data
@EqualsAndHashCode(callSuper=true)
public class ProductSpuQueryForm extends net.lab1024.sa.base.common.domain.PageParam {
@Size(max=150) private String keyword;
@Positive private Long categoryId;
@Pattern(regexp="ON_SHELF|OFF_SHELF") private String status;
@Pattern(regexp="ON_SHELF|OFF_SHELF") private String skuStatus;
@Pattern(regexp="STANDARD|NON_STANDARD") private String productType;
@Override @Min(1) public Long getPageNum() { return super.getPageNum(); }
@Override @Min(1) public Long getPageSize() { return super.getPageSize(); }
}
