package com.xsy.scm.trace.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.error.ScmErrorCode;

/**
 * 追溯域错误码：批次档案与收货批次事实的失败原因。
 *
 * <p>
 * 批次数量与收货量的对账属于采购收货命令的校验，错误码定义在采购域；这里只放批次自身的问题。
 */
@Getter
@RequiredArgsConstructor
public enum TraceErrorCode implements ScmErrorCode {

    TRACE_LOT_NOT_FOUND(41500, "批次不存在"),

    TRACE_LOT_SOURCE_CODE_INVALID(41501, "来源生产批号长度不能超过 64 个字符"),

    TRACE_LOT_QUANTITY_INVALID(41502, "批次数量必须为大于零的四位定点数"),

    TRACE_LOT_REQUIRED(41503, "批次仓的收货必须逐行登记批次"),

    /** 41504：包装码是实物的身份，不能超过该批次当前库存。 */
    TRACE_UNIT_EXCEED_BALANCE(41504, "发码总量不能超过该批次当前库存"),

    TRACE_UNIT_NOT_FOUND(41505, "包装码不存在"),

    TRACE_UNIT_QUANTITY_INVALID(41506, "每包数量必须为大于零的四位定点数"),

    /** 41507：一码一物，绑定后不能改绑。 */
    TRACE_UNIT_ALREADY_BOUND(41507, "该包装码已经绑定过分拣任务行，不能重复绑定"),

    TRACE_UNIT_SKU_MISMATCH(41508, "包装码所属批次与分拣行的商品规格不一致"),

    TRACE_UNIT_BIND_EXCEED(41509, "该分拣行已绑定的数量将超过实拣数量"),

    TRACE_SORTING_ITEM_NOT_FOUND(41510, "分拣任务行不存在"),

    TRACE_UNIT_VOID(41511, "包装码已作废，不能再绑定");

    private final int code;

    private final String msg;
}
