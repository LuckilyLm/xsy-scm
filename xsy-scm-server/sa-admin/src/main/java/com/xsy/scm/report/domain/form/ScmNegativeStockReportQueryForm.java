package com.xsy.scm.report.domain.form;

import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;

/**
 * 负库存损耗报表查询条件。
 *
 * <p>
 * 刻意没有日期区间：负库存是「当前时点余额」的状态，按日期筛选只会得到一张假装是历史月份的表。 需要历史留存时另建月度快照，不在这里用余额硬拼。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ScmNegativeStockReportQueryForm extends PageParam {

    private Long warehouseId;

    private Long skuId;

    /** 商品名称 / SKU 编码模糊匹配。 */
    private String keyword;
}
