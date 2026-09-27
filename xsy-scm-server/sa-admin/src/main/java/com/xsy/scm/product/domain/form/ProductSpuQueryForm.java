package com.xsy.scm.product.domain.form;

import com.xsy.scm.common.constant.ScmProductTypeEnum;
import com.xsy.scm.common.constant.ScmShelfStatusEnum;
import com.xsy.scm.common.validation.ScmEnumValue;
import com.xsy.scm.product.constant.ScmProductMasterStatusEnum;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProductSpuQueryForm extends net.lab1024.sa.base.common.domain.PageParam {
    @Size(max = 150, message = "查询关键词不能超过150个字符")
    private String keyword;
    @Positive(message = "分类 ID必须大于0")
    private Long categoryId;
    @ScmEnumValue(enumClass = ScmShelfStatusEnum.class, message = "商品销售状态无效")
    private String status;
    @ScmEnumValue(enumClass = ScmShelfStatusEnum.class, message = "SKU 状态无效")
    private String skuStatus;
    @ScmEnumValue(enumClass = ScmProductTypeEnum.class, message = "商品类型无效")
    private String productType;
    @ScmEnumValue(enumClass = ScmProductMasterStatusEnum.class, message = "商品主档状态无效")
    private String masterStatus;
    @Pattern(regexp = "AMBIENT|CHILLED|FROZEN", message = "储存方式取值无效")
    private String storageMethod;
    /**
     * 命中任一标签即返回（或语义），与分类的多选筛选保持一致。
     */
    @Size(max = 50, message = "标签 ID 列表不能超过50项")
    private List<@NotNull(message = "标签 ID 列表不能为空") @Positive(message = "标签 ID 列表必须大于0") Long> tagIds;
    private Boolean hasPrimaryImage;
    private Boolean hasBarcode;
    private OffsetDateTime createdFrom;
    private OffsetDateTime createdTo;

    @Override
    @Min(value = 1, message = "页码不能小于1")
    public Long getPageNum() {
        return super.getPageNum();
    }

    @Override
    @Min(value = 1, message = "每页条数不能小于1")
    public Long getPageSize() {
        return super.getPageSize();
    }
}
