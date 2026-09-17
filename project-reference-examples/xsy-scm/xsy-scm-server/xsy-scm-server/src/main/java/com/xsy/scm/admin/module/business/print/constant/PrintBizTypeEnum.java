package com.xsy.scm.admin.module.business.print.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 打印模板 业务类型
 *
 * 数据库字段：t_print_template.biz_type
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum PrintBizTypeEnum implements BaseEnum {

    /**
     * 1 采购单
     */
    PURCHASE(1, "采购单"),

    /**
     * 2 发货单
     */
    DELIVERY(2, "发货单"),

    /**
     * 3 分拣小票
     */
    SORT_TICKET(3, "分拣小票"),

    /**
     * 4 询价报价单
     */
    INQUIRY(4, "询价报价单"),

    ;

    private final Integer value;

    private final String desc;
}
