package net.lab1024.sa.admin.module.scm.report.support;

import java.util.Collection;
import java.util.function.Consumer;

import cn.dev33.satoken.stp.StpUtil;

/**
 * 报表的字段级权限收口（Finance R0 计划 §32）。
 *
 * <p>成本权限必须<b>独立存在</b>：普通仓管要看数量流水，但不必然能看单位成本与账面金额。
 * 同一个流水查询服务两类人，因此成本列只能在服务端按字段抹掉，不能靠前端隐藏列——
 * 前端隐藏挡不住直接调接口。
 *
 * <p>抹除是<b>失败关闭</b>的：取不到权限（未登录、依赖未装配）时按「无权限」处理，
 * 与 F0 读侧守卫同一取向。宁可少给数字，不可多给。
 *
 * <p>已经解析过数据范围的调用方应直接读 {@code ScmDataScopeContext#isCostVisible()}
 * （同一个权限码、同一次解析），不要在一次请求里重复问 Sa-Token；本类的
 * {@link #canViewCost()} 留给没有范围上下文的调用方。
 */
public final class ScmReportAccess {

    /** 成本查看权限码；必须与 V50 种下的 {@code t_menu.api_perms} 逐字一致。 */
    public static final String COST_QUERY_PERM = "scm:report:cost:query";

    private ScmReportAccess() {
    }

    /**
     * 当前调用者是否可查看成本字段。{@code administratorFlag} 由 Sa-Token 的权限加载器放行，
     * 不需要在这里特判。
     */
    public static boolean canViewCost() {
        try {
            return StpUtil.hasPermission(COST_QUERY_PERM);
        } catch (RuntimeException notLoggedIn) {
            return false;
        }
    }

    /**
     * 无成本权限时清空所有成本字段：把 {@code rows} 每个元素的 {@code reader}/{@code writer}
     * 组成的字段对置为 {@code null}。
     *
     * <p>用 {@code null} 而不是 {@code 0}：0 是「成本确实是零」这一事实，
     * null 才是「调用者无权知道」。前端把 null 渲染成 {@code —}，两者在页面上必须可辨。
     */
    public static <T> void maskCost(Collection<T> rows, boolean costVisible,
                                    Consumer<T> costClearer) {
        if (costVisible || rows == null) {
            return;
        }
        rows.forEach(costClearer);
    }
}
