package com.xsy.scm.report.domain.form;

import lombok.Data;
import lombok.EqualsAndHashCode;
import com.xsy.scm.base.common.domain.PageParam;

/**
 * 负库存损耗报表查询条件。
 *
 * <p>
 * 刻意没有日期区间：负库存是「当前时点余额」的状态，按日期筛选只会得到一张假装是历史月份的表。 需要历史留存时另建月度保存的数据，不在这里用余额硬拼。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ScmNegativeStockReportQueryForm extends PageParam {

    private Long warehouseId;

    private Long skuId;

    /** 商品名称 / SKU 编码模糊匹配。 */
    private String keyword;

    /**
     * 非分页报表仅使用业务筛选；忽略客户端分页字段，避免受当前列表页影响。 父类分页值只用于满足通用表单校验，概览和汇总查询不会用它截取结果。
     */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"pageNum", "pageSize", "searchCount", "sortItemList"})
    @io.swagger.v3.oas.annotations.media.Schema(name = "ScmNegativeStockReportFilter")
    public static class Filter extends ScmNegativeStockReportQueryForm {
        /** 为复用的分页约束提供有效内部值，不改变业务筛选。 */
        public Filter() {
            setPageNum(1L);
            setPageSize(1L);
        }
    }
}
