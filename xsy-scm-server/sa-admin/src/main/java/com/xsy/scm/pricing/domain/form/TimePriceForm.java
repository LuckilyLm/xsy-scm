package com.xsy.scm.pricing.domain.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import lombok.Data;

/**
 * 新增 / 修改时价。
 */
@Data
public class TimePriceForm {

    /**
     * 修改时必填。
     */
    private Long id;

    /**
     * 乐观锁版本；修改时必填。
     */
    private Integer version;

    @NotNull(message = "商品规格不能为空")
    private Long skuId;

    @NotNull(message = "时价不能为空")
    @DecimalMin(value = "0", message = "时价不能为负")
    @Digits(integer = 14, fraction = 4, message = "时价最多四位小数")
    private BigDecimal unitPrice;

    @NotNull(message = "生效开始时间不能为空")
    private OffsetDateTime startAt;

    @NotNull(message = "生效结束时间不能为空")
    private OffsetDateTime endAt;

    @Size(max = 200, message = "备注不能超过200个字符")
    private String remark;
}
