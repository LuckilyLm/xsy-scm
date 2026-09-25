package net.lab1024.sa.admin.module.scm.finance;

import cn.dev33.satoken.annotation.SaCheckPermission;
import net.lab1024.sa.admin.module.scm.common.ScmW5PgITBase;
import net.lab1024.sa.admin.module.scm.finance.constant.FinanceConstant;
import net.lab1024.sa.admin.module.scm.finance.controller.FinanceReceiptController;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.admin.module.system.login.manager.LoginManager;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code scm:finance:receipt:add} 的正式权限取证（F1-3A）。
 *
 * <p><b>要证明的是「产品权限真的发布了」，不是「mock 里通过了」。</b>
 * 因此三条断言分别覆盖交付链的三段，没有一条依赖 {@code MockedStatic<StpUtil>}：
 * <ol>
 *   <li>V66 把能力点种进了 {@code t_menu}，并按 {@code role_code} 授给 SCM_FINANCE（配置事实）；</li>
 *   <li>真实登录用户的权限集合里出现 / 不出现这个串（{@link LoginManager} 走的就是
 *       {@code t_role → t_role_menu → t_menu.api_perms} 那条生产路径，是产品机制本身）；</li>
 *   <li>Controller 上的 {@code @SaCheckPermission} 声明的正是同一个串（防止权限串发布在库里、
 *       端点却挂着另一个串或压根没挂）。</li>
 * </ol>
 *
 * <p>取证账号一律 {@code administrator_flag = false}：超管绕过权限校验，用它取证等于什么都没测。
 */
@DisplayName("收款登记权限发布（F1-3A，PG IT）")
class ScmFinanceReceiptPermissionPgIT extends ScmW5PgITBase {

    @Autowired
    private LoginManager loginManager;

    private Long employeeWithRole(String tag, String roleCode) {
        String loginName = (prefix + "-" + tag).toUpperCase();
        jdbc.update("INSERT INTO t_employee (employee_uid, login_name, login_pwd, actual_name, department_id,"
                        + " administrator_flag, deleted_flag) VALUES (?, ?, ?, ?, 1, FALSE, FALSE)",
                UUID.randomUUID().toString().replace("-", ""), loginName, "$argon2id$it-placeholder", "财务岗" + tag);
        Long employeeId = jdbc.queryForObject(
                "SELECT employee_id FROM t_employee WHERE login_name = ?", Long.class, loginName);
        assertThat(jdbc.queryForObject(
                "SELECT administrator_flag FROM t_employee WHERE employee_id = ?", Boolean.class, employeeId))
                .as("权限取证禁止超管位").isFalse();
        jdbc.update("INSERT INTO t_role_employee (role_id, employee_id)"
                        + " SELECT r.role_id, ? FROM t_role r WHERE r.role_code = ?",
                employeeId, roleCode);
        evictMybatisCache();
        return employeeId;
    }

    private List<String> permissionsOf(Long employeeId) {
        var employee = new RequestEmployee();
        employee.setEmployeeId(employeeId);
        employee.setActualName("F1-3A 权限取证");
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        employee.setAdministratorFlag(false);
        SmartRequestUtil.setRequestUser(employee);
        // 走真实的角色 → 菜单 → api_perms 装配路径（生产登录用的就是它），不 mock StpUtil
        return loginManager.loadUserPermission(employeeId).getPermissionList();
    }

    @Test
    @DisplayName("SCM_FINANCE 持有收款登记权限；SCM_DRIVER 不持有")
    void financeRoleHoldsReceiptAddPermission() {
        Long financeEmployee = employeeWithRole("FIN", "SCM_FINANCE");
        Long driverEmployee = employeeWithRole("DRV", "SCM_DRIVER");

        assertThat(permissionsOf(financeEmployee)).contains(FinanceConstant.RECEIPT_ADD_PERM);
        assertThat(permissionsOf(driverEmployee)).doesNotContain(FinanceConstant.RECEIPT_ADD_PERM);
    }

    @Test
    @DisplayName("库里发布的串与 Controller 注解声明的串逐字一致")
    void publishedPermissionStringMatchesTheControllerAnnotation() throws Exception {
        Method endpoint = FinanceReceiptController.class
                .getDeclaredMethod("add",
                        net.lab1024.sa.admin.module.scm.finance.domain.form.FinanceReceiptAddForm.class,
                        String.class);
        SaCheckPermission annotation = endpoint.getAnnotation(SaCheckPermission.class);
        assertThat(annotation).as("收款登记端点必须挂功能权限注解").isNotNull();
        assertThat(annotation.value()).containsExactly(FinanceConstant.RECEIPT_ADD_PERM);

        // 交付链第一段：t_menu 里发布的串必须与注解一致，且只有 SCM_FINANCE 与超管拿到
        assertThat(jdbc.queryForList(
                "SELECT api_perms FROM t_menu WHERE api_perms = ?", String.class,
                FinanceConstant.RECEIPT_ADD_PERM))
                .containsExactly(FinanceConstant.RECEIPT_ADD_PERM);
        assertThat(jdbc.queryForList(
                "SELECT r.role_code FROM t_role_menu rm JOIN t_role r ON r.role_id = rm.role_id"
                        + " JOIN t_menu m ON m.menu_id = rm.menu_id WHERE m.api_perms = ?"
                        + " AND r.role_code <> 'SUPER_ADMIN' ORDER BY r.role_code",
                String.class, FinanceConstant.RECEIPT_ADD_PERM))
                .containsExactly("SCM_FINANCE");
    }

    @Test
    @DisplayName("本阶段没有提前发布任何其它财务权限或财务页面菜单")
    void nothingElseIsPublishedYet() {
        assertThat(jdbc.queryForList(
                "SELECT DISTINCT api_perms FROM t_menu WHERE api_perms LIKE 'scm:finance:%' ORDER BY api_perms",
                String.class))
                .containsExactly(FinanceConstant.RECEIPT_ADD_PERM);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM t_menu WHERE menu_id BETWEEN 1500 AND 1599 AND menu_type = 2",
                Integer.class))
                .as("页面菜单随 F1-6 的 .vue 一起发布，本阶段一个都没有").isZero();
    }
}
