package net.lab1024.sa.admin.module.scm.product.domain.vo;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;
@Data
@EqualsAndHashCode(callSuper=true)
public class ProductCategoryTreeVO extends ProductCategoryVO {
private List<ProductCategoryTreeVO> children;
}
