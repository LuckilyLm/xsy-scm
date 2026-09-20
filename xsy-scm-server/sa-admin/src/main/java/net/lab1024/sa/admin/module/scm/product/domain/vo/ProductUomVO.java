package net.lab1024.sa.admin.module.scm.product.domain.vo;
import lombok.Data;
import java.math.BigDecimal;
@Data
public class ProductUomVO {
private Long uomId;
private Integer version;
private String uomCode;
private String name;
private String category;
private Integer precisionScale;
private String status;
private Integer sortOrder;
/** 被商品销售单位与供应商采购单位引用的次数；大于 0 时禁止删除，只允许停用。 */
private Long referencedCount;
}
