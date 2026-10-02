package com.xsy.scm.report.domain.form;

import com.xsy.scm.report.constant.ScmFinanceAgingAccountTypeEnum;
import com.xsy.scm.report.constant.ScmFinanceAgingBucketEnum;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
public class ScmFinanceAgingQueryForm extends PageParam {
    @NotNull(message = "往来类型不能为空")
    private ScmFinanceAgingAccountTypeEnum accountType;
    @NotNull(message = "账龄截止日不能为空")
    private LocalDate asOfDate;
    private ScmFinanceAgingBucketEnum agingBucket;
    @Positive(message = "客户编号必须大于0")
    private Long customerId;
    @Positive(message = "结算客户编号必须大于0")
    private Long settlementCustomerId;
    @Positive(message = "供应商编号必须大于0")
    private Long supplierId;
    @Positive(message = "仓库编号必须大于0")
    private Long warehouseId;
    @Size(max = 120, message = "关键字不能超过120个字符")
    private String keyword;

    public boolean isReceivable() {
        return accountType == ScmFinanceAgingAccountTypeEnum.RECEIVABLE;
    }

    public String getAgingBucketCode() {
        return agingBucket == null ? null : agingBucket.name();
    }
}
