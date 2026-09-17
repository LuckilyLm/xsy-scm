package com.xsy.scm.admin.module.business.trace.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 检测报告匹配模式
 *
 * <p>对标蔬东坡 17.5：新增【生产批号】维度，与库存批次解耦，商品无需启用库存批次
 * 也可通过生产批号关联检测报告。</p>
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum InspectMatchModeEnum implements BaseEnum {

    /**
     * 1 绑定采购单
     */
    PURCHASE(1, "绑定采购单"),

    /**
     * 2 绑定生产批号（推荐，不依赖库存批次）
     */
    PRODUCE_BATCH(2, "绑定生产批号"),

    ;

    private final Integer value;

    private final String desc;
}
