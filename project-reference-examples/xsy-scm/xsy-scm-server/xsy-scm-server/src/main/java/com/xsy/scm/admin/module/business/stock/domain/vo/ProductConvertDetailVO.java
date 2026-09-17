package com.xsy.scm.admin.module.business.stock.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 商品转换单详情 返回对象
 *
 * @author xsy-scm
 */
@Data
public class ProductConvertDetailVO extends ProductConvertVO {

    @Schema(description = "转换明细")
    private List<ProductConvertItemVO> items;
}
