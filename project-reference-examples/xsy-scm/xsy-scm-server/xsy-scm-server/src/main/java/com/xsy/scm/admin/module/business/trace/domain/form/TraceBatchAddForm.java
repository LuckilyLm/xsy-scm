package com.xsy.scm.admin.module.business.trace.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 溯源批次 添加表单
 *
 * @author xsy-scm
 */
@Data
public class TraceBatchAddForm {

    @Schema(description = "商品ID")
    @NotNull(message = "商品ID不能为空")
    private Long productId;

    @Schema(description = "规格ID")
    private Long skuId;

    @Schema(description = "供应商ID")
    private Long supplierId;

    @Schema(description = "厂商ID")
    private Long manufacturerId;

    @Schema(description = "生产批号")
    @NotBlank(message = "生产批号不能为空")
    private String produceBatchNo;

    @Schema(description = "产地")
    private String originPlace;

    @Schema(description = "生产/采收日期")
    @NotNull(message = "生产日期不能为空")
    private LocalDate produceDate;

    @Schema(description = "保质期单位：1 天，2 月")
    private Integer shelfLifeUnit;

    @Schema(description = "保质期数值")
    private Integer shelfLifeValue;
}
