package net.lab1024.sa.admin.module.scm.customer.domain.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;

/**
 * 客户列表查询条件。
 *
 * <p>分页边界在这里收紧到 1–100（legacy 与 SmartAdmin 基线只保证 1–500）。边界在 DTO 上声明，
 * 避免每个调用方各写一遍。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerQueryForm extends PageParam {

    /** 关键字：客户编码 / 客户名称 / 联系人 / 联系电话。 */
    @Size(max = 150)
    private String keyword;

    @Positive
    private Long customerTypeId;

    @Pattern(regexp = "POTENTIAL|COOPERATING|SUSPENDED|BLACKLIST")
    private String status;

    @Pattern(regexp = "INDEPENDENT|GROUP")
    private String settleMode;

    /** 按上级集团客户反查下属单位。 */
    @Positive
    private Long parentCustomerId;

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
