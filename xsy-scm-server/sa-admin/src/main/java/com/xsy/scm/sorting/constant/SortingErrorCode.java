package com.xsy.scm.sorting.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.error.ScmErrorCode;

/**
 * 分拣域错误码，与配送域的 41100–41119 分开。
 *
 * <p>
 * 「不属于你的任务」不在这里：由 {@code ScmDataScopeException} 统一回 30005，避免暴露 id 是否存在。
 */
@Getter
@RequiredArgsConstructor
public enum SortingErrorCode implements ScmErrorCode {
    TASK_NOT_FOUND(41120, "分拣任务不存在"),
    STATE_INVALID(41121, "分拣任务当前状态不允许此操作"),
    ITEM_NOT_IN_TASK(41122, "提交的分拣明细不属于该任务"),
    ORDER_LINE_TAKEN(41123, "该订单行已被其它分拣任务占用，请刷新后重试"),
    ORDER_NOT_SORTABLE(41124, "只有已确认订单的有效明细可以进入分拣"),
    RESULT_INCOMPLETE(41125, "仍有未处理的分拣明细，任务不能完成"),
    ASSIGNEE_INVALID(41126, "受指派员工不存在或已停用"),
    WAREHOUSE_INVALID(41127, "仓库不存在或未启用"),

    /** 本任务的订单行已有 {@code CONFIRMED} 出库记录，库存已扣减，不允许再改分拣结果。 */
    OUTBOUND_EXISTS(41128, "该分拣任务对应的订单已发车出库，不能重开"),

    /** 建单时显式指定的供应商来源不存在或已停用。 */
    SUPPLIER_INVALID(41129, "供应商不存在或已停用"),

    /** 秤读数不存在，或已被接受 / 驳回。 */
    SCALE_EVENT_NOT_FOUND(41130, "秤读数不存在或已被处理"),

    /** 只有待处理的读数可以接受或驳回。 */
    SCALE_EVENT_STATE_INVALID(41131, "秤读数当前状态不允许此操作"),

    /** 读数未标记为稳定：不稳定读数不能作为分拣结果。 */
    SCALE_EVENT_NOT_STABLE(41133, "读数未稳定，请等待稳定后再接受"),

    /** 读数单位与订单商品规格单位不一致。 */
    SCALE_EVENT_UNIT_MISMATCH(41870, "电子秤读数单位与订单商品规格单位不一致，请核对设备配置"),

    /** 非标品需要操作员裁定正常、短少、缺货或超出，设备不得自行裁定。 */
    SCALE_EVENT_RESULT_REQUIRED(41871, "非标品称重必须选择分拣结果"),

    /** 同一设备事件标识不得被用于另一笔重量或另一张任务。 */
    SCALE_EVENT_KEY_CONFLICT(41872, "电子秤事件编号已用于另一笔读数，请检查设备上报"),

    STANDARD_CONFIRM_INVALID(41873, "所选明细不能按计划量确认，请刷新预览或使用人工录入"),

    /** 只有净重可以计入分拣量；毛重在未去皮时不得入库。 */
    SCALE_BASKET_NOT_NET_WEIGHT(41874, "只有净重可以计入分拣量，请先完成去皮"),

    /** 设备确认的筐集合与服务端读到的集合不一致，说明期间有新增或撤销。 */
    SCALE_BASKET_MANIFEST_MISMATCH(41875, "称重集合已变化，请刷新累计后重新确认"),

    /** 筐记录的任务或明细版本与当前不一致：任务已改派、重开或明细已变更。 */
    SCALE_BASKET_CONTEXT_CHANGED(41876, "任务或明细版本已变化，请重新领取作业"),

    /** 该明细当前没有待完成的筐。 */
    SCALE_BASKET_EMPTY(41877, "当前没有待完成的筐记录，请先记录本筐"),

    /** 该明细已经完成过称重，更正需走分拣域的重开流程。 */
    SCALE_BASKET_ALREADY_COMPLETED(41878, "该商品已完成称重，请勿重复确认"),

    /** 读数为负：属于设备侧无效采集，不放宽数据库约束掩盖。 */
    SCALE_MEASUREMENT_NEGATIVE(41879, "读数不能为负值，请检查去皮与置零后重新采集");

    private final int code;
    private final String msg;
}
