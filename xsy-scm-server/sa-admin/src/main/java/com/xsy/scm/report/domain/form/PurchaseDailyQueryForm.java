package com.xsy.scm.report.domain.form;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;

@Data
@EqualsAndHashCode(callSuper = true)
public class PurchaseDailyQueryForm extends PageParam {
    @NotNull(message = "请选择清单日期")
    private LocalDate reportDate;
    private Long warehouseId;
    @Size(max = 100, message = "商品关键字不能超过100个字符")
    private String keyword;

    @Override
    @Min(value = 1, message = "页码必须大于零")
    public Long getPageNum() {
        return super.getPageNum();
    }

    @Override
    @Min(value = 1, message = "每页数量必须大于零")
    public Long getPageSize() {
        return super.getPageSize();
    }
}
