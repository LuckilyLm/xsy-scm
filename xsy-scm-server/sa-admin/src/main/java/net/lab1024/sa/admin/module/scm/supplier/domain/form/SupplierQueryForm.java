package net.lab1024.sa.admin.module.scm.supplier.domain.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;

/**
 * 供应商列表查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SupplierQueryForm extends PageParam {

    /**
     * 关键字：供应商编码 / 名称 / 联系人 / 联系电话。
     */
    @Size(max = 150)
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
