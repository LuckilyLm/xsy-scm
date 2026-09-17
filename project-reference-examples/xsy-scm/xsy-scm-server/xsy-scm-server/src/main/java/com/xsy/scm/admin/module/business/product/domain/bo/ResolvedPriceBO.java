package com.xsy.scm.admin.module.business.product.domain.bo;

import com.xsy.scm.admin.module.business.product.constant.PriceTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 取价解析结果：命中的成交价 + 取价类型
 *
 * @author xsy-scm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResolvedPriceBO {

    /**
     * 成交价（不含税）
     */
    private BigDecimal price;

    /**
     * 取价类型
     */
    private PriceTypeEnum priceType;
}
