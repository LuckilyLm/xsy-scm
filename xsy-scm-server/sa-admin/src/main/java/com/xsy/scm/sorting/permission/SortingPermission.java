package com.xsy.scm.sorting.permission;

/** Stable permission identifiers published by the sorting API. */
public final class SortingPermission {

    public static final String TASK_QUERY = "scm:sorting:task:query";

    public static final String SUMMARY_QUERY = "scm:sorting:summary:query";

    public static final String TASK_ADD = "scm:sorting:task:add";

    public static final String TASK_PRINT = "scm:sorting:task:print";

    public static final String TASK_ASSIGN = "scm:sorting:task:assign";

    public static final String ITEM_UPDATE = "scm:sorting:item:update";

    public static final String TASK_COMPLETE = "scm:sorting:task:complete";

    public static final String TASK_CANCEL = "scm:sorting:task:cancel";

    public static final String TASK_REOPEN = "scm:sorting:task:reopen";

    /**
     * 电子秤：查询 / 上报 / 接受分开授权。
     *
     * <p>
     * 上报只是把设备读数记下来，接受才写进分拣结果 —— 合并成一个权限会让「把秤接进来」
     * 顺手获得「用秤上的数字直接改分拣结果」的能力。
     */
    public static final String SCALE_QUERY = "scm:sorting:scale:query";

    public static final String SCALE_REPORT = "scm:sorting:scale:report";

    public static final String SCALE_ACCEPT = "scm:sorting:scale:accept";

    private SortingPermission() {
    }
}
