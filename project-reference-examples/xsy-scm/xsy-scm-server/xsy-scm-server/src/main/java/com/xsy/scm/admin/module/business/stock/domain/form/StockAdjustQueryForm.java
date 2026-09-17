package com.xsy.scm.admin.module.business.stock.domain.form;

import com.xsy.scm.admin.module.business.stock.constant.AdjustStatusEnum;
import com.xsy.scm.admin.module.business.stock.constant.AdjustTypeEnum;
import com.xsy.scm.base.common.domain.PageParam;
import com.xsy.scm.base.common.swagger.SchemaEnum;
import com.xsy.scm.base.common.validator.enumeration.CheckEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 库存调整单 分页查询表单
 *
 * @author xsy-scm
 */
@Data
public class StockAdjustQueryForm extends PageParam {

    @Schema(description = "调整单号 模糊搜索")
    @Size(max = 30, message = "搜索词最多30字符")
    private String adjustNo;

    @SchemaEnum(AdjustTypeEnum.class)
    @CheckEnum(message = "调整类型错误", value = AdjustTypeEnum.class, required = false)
    private Integer adjustType;

    @SchemaEnum(AdjustStatusEnum.class)
    @CheckEnum(message = "调整单状态错误", value = AdjustStatusEnum.class, required = false)
    private Integer status;

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "规格ID")
    private Long skuId;

    @Schema(hidden = true)
    private Boolean deletedFlag;
}
