package com.xsy.scm.admin.module.business.purchase.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 供应商分拣模式
 *
 * <p>对标蔬东坡 17.1「供应商分拣增加按采购任务实时分配模式」：</p>
 * <ul>
 *     <li>默认供应商：按「客户指定 &gt; 客户类型指定 &gt; 商品默认」分拣，订单生成后不再受采购任务影响；</li>
 *     <li>采购单生成后：必须等采购单正式生成后，供应商才能按采购单分配项分拣；</li>
 *     <li>按采购任务实时分配：订单商品与采购任务一对一，采购任务变更实时同步供应商分拣端。</li>
 * </ul>
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum SupplierSortModeEnum implements BaseEnum {

    /**
     * 1 默认供应商
     */
    DEFAULT_SUPPLIER(1, "默认供应商"),

    /**
     * 2 采购单生成后
     */
    AFTER_PURCHASE(2, "采购单生成后"),

    /**
     * 3 按采购任务实时分配
     */
    REALTIME_ASSIGN(3, "按采购任务实时分配"),

    ;

    private final Integer value;

    private final String desc;
}
