package net.lab1024.sa.admin.module.scm.common;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 正式非管理员角色矩阵的落库取证（V56 + V57 增量，对应 P0-H 三条裁决）。
 *
 * <p>本类只回答一个问题：<b>库里的授权矩阵是否等于裁决写的口径</b>。运行时行为（越权读/写各自的
 * 表现）由 {@code ScmPurchaseDataScopePgIT}、{@code WarehouseDataScopePgIT}、
 * {@code ScmScreenDataScopePgIT} 与各域的范围 IT 取证，这里不重复断言；
 * 反过来，浏览器里「某个角色登录后看到什么」由 {@code e2e/scm-data-scope.spec.ts} 覆盖。
 *
 * <p>三条必须被钉住的口径：
 * <ul>
 *   <li><b>库存数量权限 ≠ 库存成本权限</b>：采购员与仓管员都有余额查询权，都没有
 *       {@code scm:report:cost:query}；均价与账面金额因此在读侧被抹成 {@code null}。</li>
 *   <li><b>全量范围一律是显式授权</b>：财务的「全部仓库」是一条
 *       {@code scm:inventory:scope:all:query}，不是代码里的「是财务就跳过范围」分支。</li>
 *   <li><b>业务审批 ≠ 资金操作</b>：销售主管有退货批准/驳回与退款完成，没有任何付款/资金类权限点。</li>
 * </ul>
 *
 * <p>按 {@code role_code} + 权限码断言，不硬编码 {@code role_id}：长驻开发库里 E2E 夹具会临时
 * 插入真实角色行，只有按业务编码关联才不会依赖插入顺序。
 */
@DisplayName("P0-H 正式角色授权矩阵（PG IT）")
class ScmBusinessRoleMatrixPgIT extends ScmW5PgITBase {

    private static final String COST_QUERY = "scm:report:cost:query";
    private static final String WAREHOUSE_ALL = "scm:inventory:scope:all:query";
    private static final String PURCHASE_ALL = "scm:purchase:scope:all:query";
    private static final String CUSTOMER_ALL = "scm:customer:scope:all:query";
    private static final String BALANCE_QUERY = "scm:inventory:balance:query";
    private static final String SCREEN_QUERY = "scm:screen:query";

    @Test
    @DisplayName("九个正式业务角色都存在，且授权矩阵非空")
    void formalRolesExistWithNonEmptyGrants() {
        List<String> roleCodes = jdbc.queryForList(
                "SELECT role_code FROM t_role WHERE role_code LIKE 'SCM\\_%' ORDER BY role_code", String.class);
        assertThat(roleCodes).containsExactly("SCM_DISPATCHER", "SCM_DRIVER", "SCM_FINANCE", "SCM_PURCHASER",
                "SCM_PURCHASER_LEAD", "SCM_SALES", "SCM_SALES_LEAD", "SCM_STOREKEEPER", "SCM_STOREKEEPER_LEAD");
        // t_role 上根本没有 administrator_flag 这一列：超管位是员工的属性，不是角色的属性，
        // 所以「正式角色必须用 administrator_flag=false 的账号验收」只能由 E2E 的登录账号保证。
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 't_role'
                  AND column_name = 'administrator_flag'""", Integer.class)).isZero();
        assertThat(jdbc.queryForList("""
                SELECT r.role_code FROM t_role r
                WHERE r.role_code LIKE 'SCM\\_%'
                  AND NOT EXISTS (SELECT 1 FROM t_role_menu rm WHERE rm.role_id = r.role_id)
                ORDER BY r.role_code""", String.class))
                .as("没有任何正式角色是空壳")
                .isEmpty();
    }

    @Test
    @DisplayName("销售主管拿到退货/退款业务审批，普通销售员一点都没有")
    void salesLeadHoldsReturnAndRefundApprovalOnly() {
        assertThat(holds("SCM_SALES_LEAD", "scm:order:return:approve")).isTrue();
        assertThat(holds("SCM_SALES_LEAD", "scm:order:return:reject")).isTrue();
        assertThat(holds("SCM_SALES_LEAD", "scm:order:refund:complete")).isTrue();
        assertThat(holds("SCM_SALES", "scm:order:return:approve")).isFalse();
        assertThat(holds("SCM_SALES", "scm:order:refund:complete")).isFalse();
        // 「业务审批」不等于「资金操作」：本仓库现在没有任何付款/核销类权限点，
        // 销售主管因此也拿不到成本列 —— Finance R1 的退款付款必须另走财务权限。
        assertThat(holds("SCM_SALES_LEAD", COST_QUERY)).isFalse();
        assertThat(holds("SCM_SALES", COST_QUERY)).isFalse();
    }

    @Test
    @DisplayName("财务的全部仓库范围是一条显式授权权限，并且仍有独立成本权")
    void financeWidensWarehouseThroughScopePermissionOnly() {
        assertThat(holds("SCM_FINANCE", WAREHOUSE_ALL)).isTrue();
        assertThat(holds("SCM_FINANCE", COST_QUERY)).isTrue();
        assertThat(holds("SCM_FINANCE", CUSTOMER_ALL)).isTrue();
        // 财务不是「天生全量」：把这条权限收回，范围解析就回到授权行，不需要改任何业务代码
        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_menu WHERE api_perms = ?", Integer.class,
                WAREHOUSE_ALL)).isEqualTo(1);
    }

    @Test
    @DisplayName("库存数量权与库存成本权分离：采购员与仓管员都有数量权，都没有成本权与全仓范围")
    void quantityRightsAreSeparateFromCostRights() {
        assertThat(holds("SCM_PURCHASER", BALANCE_QUERY)).isTrue();
        assertThat(holds("SCM_PURCHASER", COST_QUERY)).isFalse();
        assertThat(holds("SCM_PURCHASER", WAREHOUSE_ALL)).isFalse();
        assertThat(holds("SCM_STOREKEEPER", BALANCE_QUERY)).isTrue();
        assertThat(holds("SCM_STOREKEEPER", COST_QUERY)).isFalse();
        // 成本权与全部仓库范围只在仓库主管与财务身上
        assertThat(holds("SCM_STOREKEEPER_LEAD", COST_QUERY)).isTrue();
        assertThat(holds("SCM_STOREKEEPER_LEAD", WAREHOUSE_ALL)).isTrue();
    }

    @Test
    @DisplayName("采购主管有全量采购范围但没有全仓范围：交集的另一条边界不会因归属而放宽")
    void purchaseLeadCannotBypassWarehouseDimension() {
        assertThat(holds("SCM_PURCHASER_LEAD", PURCHASE_ALL)).isTrue();
        assertThat(holds("SCM_PURCHASER_LEAD", WAREHOUSE_ALL)).isFalse();
        assertThat(holds("SCM_PURCHASER_LEAD", COST_QUERY)).isFalse();
        assertThat(holds("SCM_PURCHASER", PURCHASE_ALL)).isFalse();
    }

    @Test
    @DisplayName("数据大屏入口仍只授超管：范围已经接好，但授权决定单独留给人裁决")
    void screenPermissionStaysAdministratorOnly() {
        assertThat(holds("SCM_FINANCE", SCREEN_QUERY)).isFalse();
        assertThat(holds("SCM_STOREKEEPER_LEAD", SCREEN_QUERY)).isFalse();
        assertThat(holds("SCM_DISPATCHER", SCREEN_QUERY)).isFalse();
        // 角色表没有超管位这一列，所以「谁持有大屏」只能按角色编码判定：正式业务角色一个都没有
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM t_role_menu rm
                JOIN t_menu m ON m.menu_id = rm.menu_id
                JOIN t_role r ON r.role_id = rm.role_id
                WHERE m.api_perms = ? AND r.role_code LIKE 'SCM\\_%'""", Integer.class, SCREEN_QUERY)).isZero();
    }

    /** 角色是否持有某个权限码（菜单上的 api_perms 与本仓库的 web_perms 同值，取 api_perms）。 */
    private boolean holds(String roleCode, String permission) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM t_role_menu rm
                JOIN t_role r ON r.role_id = rm.role_id
                JOIN t_menu m ON m.menu_id = rm.menu_id
                WHERE r.role_code = ? AND m.api_perms = ?""", Integer.class, roleCode, permission);
        return count != null && count > 0;
    }
}
