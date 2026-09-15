package net.lab1024.sa.admin.module.scm.customer.domain.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;

/** 客户类型列表查询条件。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerTypeQueryForm extends PageParam {

    /** 关键字：类型编码 / 类型名称。 */
    @Size(max = 100)
    private String keyword;

    @Pattern(regexp = "ENABLED|DISABLED")
    private String status;

    @Override
    @Min(1)
    public Long getPageNum() {
        return super.getPageNum();
    }

    @Override
    @Min(1)
    @Max(100)
    public Long getPageSize() {
        return super.getPageSize();
    }
}
