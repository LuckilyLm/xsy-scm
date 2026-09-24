package net.lab1024.sa.admin.module.scm.sorting.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.error.ScmErrorCode;

/**
 * 分拣域错误码。码段按本轮全库 {@code grep} 的 41xxx 实际占用取号（配送段止于 41115），
 * 新增前先现查，不相信自己注释里的「本段空闲」。
 *
 * <p>「不属于你的任务」一律不走这里的业务码：那等于回答「这个 id 存在但你不该看」，
 * 让探测主键与探测权限可分辨，故由 {@code ScmDataScopeException} 统一回 30005。
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
    WAREHOUSE_INVALID(41127, "仓库不存在或已停用");

    private final int code;
    private final String msg;
}
