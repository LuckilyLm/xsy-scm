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

    /** 41504：包装码是实物的身份，仍在库内的已发码量不能超过该批次当前库存；已出库离场的包装不占额度。 */
    TRACE_UNIT_EXCEED_BALANCE(41504, "在库包装量与本次发码之和不能超过该批次当前库存"),

    TRACE_UNIT_NOT_FOUND(41505, "包装码不存在"),

    TRACE_UNIT_QUANTITY_INVALID(41506, "每包数量必须为大于零的四位定点数"),

    /** 41507：一码一物，绑定后不能改绑。 */
    TRACE_UNIT_ALREADY_BOUND(41507, "该包装码已经绑定过分拣任务行，不能重复绑定"),

    TRACE_UNIT_SKU_MISMATCH(41508, "包装码所属批次与分拣行的商品规格不一致"),

    TRACE_UNIT_BIND_EXCEED(41509, "该分拣行已绑定的数量将超过实拣数量"),

    TRACE_SORTING_ITEM_NOT_FOUND(41510, "分拣任务行不存在"),

    TRACE_UNIT_VOID(41511, "包装码已作废，不能再绑定"),

    /** 41512：已绑定的码不能作废，绑定事实不撤销。 */
    TRACE_UNIT_VOID_BOUND(41512, "已绑定分拣任务行的包装码不能作废，请先走退货或更正流程"),

    TRACE_UNIT_ALREADY_VOID(41513, "包装码已作废，不能重复作废"),

    TRACE_UNIT_VOID_REASON_REQUIRED(41514, "作废原因不能为空"),

    /** 41515：只有进行中或已完成的分拣任务才能承接实物。 */
    TRACE_SORTING_TASK_STATE_INVALID(41515, "该分拣任务未开始或已取消，不能绑定包装码"),

    /** 41516：包装码的批次必须来自分拣任务所在仓库。 */
    TRACE_UNIT_WAREHOUSE_MISMATCH(41516, "包装码所属批次与分拣任务不在同一仓库，不能绑定"),

    /** 41517：实拣量是销售单位、包装码量是库存记账单位，缺少冻结换算时两种量不能直接比较。 */
    TRACE_UNIT_UNIT_MISMATCH(41517, "包装码的计量单位与分拣行销售单位之间没有可用的冻结换算，不能绑定"),

    /** 41520：更正申请不存在。 */
    TRACE_CORRECTION_NOT_FOUND(41520, "更正申请不存在"),

    /** 41521：同一个码只允许一条待审批的更正申请。 */
    TRACE_CORRECTION_PENDING_EXISTS(41521, "该包装码已有一条待审批的更正申请"),

    /** 41522：申请与审批不能是同一个人。 */
    TRACE_CORRECTION_SELF_APPROVAL(41522, "不能审批自己提交的更正申请"),

    TRACE_CORRECTION_STATE_INVALID(41523, "更正申请已处理，不能重复审批"),

    TRACE_CORRECTION_NO_BINDING(41524, "该包装码当前没有可撤销的绑定");

    private final int code;

    private final String msg;
}
