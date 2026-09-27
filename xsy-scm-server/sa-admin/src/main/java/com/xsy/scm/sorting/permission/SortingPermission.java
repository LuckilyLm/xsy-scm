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

    private SortingPermission() {
    }
}
