package com.xsy.scm.pricing.domain.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;

import java.time.OffsetDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerGroupPriceQueryForm extends PageParam {
    @Positive(message = "客户分组 ID 必须大于0")
    private Long groupId;
    @Positive(message = "商品 ID 必须大于0")
    private Long spuId;
    @Size(max = 150, message = "关键字不能超过150个字符")
    private String keyword;
    private OffsetDateTime effectiveFrom;
    private OffsetDateTime effectiveTo;

    @Override
    @Min(value = 1, message = "页码必须至少为1")
    public Long getPageNum() {
        return super.getPageNum();
    }

    @Override
    @Min(value = 1, message = "每页条数必须在1至100之间")
    @Max(value = 100, message = "每页条数必须在1至100之间")
    public Long getPageSize() {
        return super.getPageSize();
    }
}
