package com.xsy.scm.sorting.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.error.ScmErrorCode;

/**
 * 分拣域使用 41120–41128 错误码，与配送域的 41100–41119 错误码保持分离。
 *
 * <p>
 * 「不属于你的任务」一律不走这里的业务码：那等于回答「这个 id 存在但你不该看」， 让探测主键与探测权限可分辨，故由 {@code ScmDataScopeException} 统一回 30005。
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

    /**
     * 本任务的订单行已有 {@code CONFIRMED} 出库记录，库存已扣减，不允许再改分拣结果。
     */
    OUTBOUND_EXISTS(41128, "该分拣任务对应的订单已发车出库，不能重开"),

    /** 建单时显式指定的供应商来源不存在或已停用。 */
    SUPPLIER_INVALID(41129, "供应商不存在或已停用"),

    /** 秤读数不存在，或已被接受 / 驳回。 */
    SCALE_EVENT_NOT_FOUND(41130, "秤读数不存在或已被处理"),

    /** 只有待处理的读数可以接受或驳回。 */
    SCALE_EVENT_STATE_INVALID(41131, "秤读数当前状态不允许此操作"),

    /**
     * 一键分拣只处理标准品：非标品的数量是称重结果，必须人工录入并说明差异，
     * 由一键操作推断会绕过「人工确认」这条链路。
     */
    SCALE_EVENT_ITEM_NOT_STANDARD(41132, "该明细不是标准品，不能一键分拣，请人工录入"),

    /** 读数未标记为稳定：不稳定读数不能作为分拣结果。 */
    SCALE_EVENT_NOT_STABLE(41133, "读数未稳定，请等待稳定后再接受");

    private final int code;
    private final String msg;
}
