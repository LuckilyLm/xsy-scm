package com.xsy.scm.report.domain.form;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;
import com.xsy.scm.common.validation.ScmEnumValue;
import com.xsy.scm.report.constant.ScmInventoryMovementFilterEnum;
import com.xsy.scm.report.constant.ScmInventorySourceDocumentFilterEnum;
import com.xsy.scm.report.support.ScmReportDateFilter;

/**
 * 库存流水按发生日期筛选；当前库存金额是当前时点数据，与选定日期区间无关。非分页概览不要求分页参数。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ScmInventoryReportQueryForm extends PageParam implements ScmReportDateFilter {

    @NotNull(message = "起始日期不能为空")
    private LocalDate startDate;

    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;

    private Long warehouseId;

    private Long skuId;

    /** 商品名称 / SKU 编码模糊匹配。 */
    private String keyword;

    /** 流水类型；取值与 {@code ck_inventory_movement_type} 同源，由前端枚举提供候选。 */
    @ScmEnumValue(enumClass = ScmInventoryMovementFilterEnum.class, message = "流水类型不合法")
    private String movementType;

    @ScmEnumValue(enumClass = ScmInventorySourceDocumentFilterEnum.class, message = "来源单据类型不合法")
    private String sourceDocumentType;
}
