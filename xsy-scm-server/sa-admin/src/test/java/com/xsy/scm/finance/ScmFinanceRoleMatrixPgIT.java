package com.xsy.scm.finance;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.finance.controller.FinancePayableController;
import com.xsy.scm.finance.controller.FinanceReadController;
import com.xsy.scm.finance.controller.FinanceReceiptController;
import com.xsy.scm.finance.controller.FinancePaymentController;
import com.xsy.scm.finance.controller.FinanceWriteOffController;
import com.xsy.scm.finance.domain.form.FinancePayableQueryForm;
import com.xsy.scm.finance.domain.form.FinancePaymentQueryForm;
import com.xsy.scm.finance.domain.form.FinanceRefundOptionQueryForm;
import com.xsy.scm.finance.domain.form.FinanceReceivableQueryForm;
import com.xsy.scm.finance.domain.form.FinanceReceiptQueryForm;
import com.xsy.scm.finance.domain.form.FinanceWriteOffQueryForm;
import com.xsy.scm.finance.permission.FinancePermission;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.admin.module.system.login.manager.LoginManager;
import net.lab1024.sa.admin.module.system.menu.domain.vo.MenuVO;
import net.lab1024.sa.admin.module.system.role.service.RoleMenuService;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.domain.UserPermission;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Real role-to-menu and controller-contract evidence for the finance pages and permissions. */
@DisplayName("Finance R1 页面与功能权限矩阵（PG IT）")
class ScmFinanceRoleMatrixPgIT extends ScmW5PgITBase {

    private static final List<Long> PAGE_IDS = List.of(1501L, 1502L, 1503L, 1504L, 1505L);

    private static final List<String> FINANCE_PERMISSIONS = List.of(
            FinancePermission.RECEIVABLE_QUERY,
            FinancePermission.PAYABLE_QUERY,
            FinancePermission.RECEIPT_QUERY,
            FinancePermission.PAYMENT_QUERY,
            FinancePermission.WRITE_OFF_QUERY,
            FinancePermission.RECEIPT_ADD,
            FinancePermission.PAYMENT_ADD,
            FinancePermission.WRITE_OFF_ADD,
            FinancePermission.PAYABLE_RED,
            FinancePermission.WRITE_OFF_REVERSE,
            FinancePermission.RECEIPT_REVERSE,
            FinancePermission.PAYMENT_REVERSE,
            FinancePermission.EXPORT);

    @Autowired
    private LoginManager loginManager;

    @Autowired
    private RoleMenuService roleMenuService;

    private Long employeeWithRole(String tag, String roleCode) {
        String loginName = (prefix + "-" + tag).toUpperCase();
        jdbc.update("INSERT INTO t_employee (employee_uid, login_name, login_pwd, actual_name, department_id,"
                        + " administrator_flag, deleted_flag) VALUES (?, ?, ?, ?, 1, FALSE, FALSE)",
                UUID.randomUUID().toString().replace("-", ""), loginName, "$argon2id$it-placeholder", "财务矩阵" + tag);
        Long employeeId = jdbc.queryForObject(
                "SELECT employee_id FROM t_employee WHERE login_name = ?", Long.class, loginName);
        assertThat(jdbc.queryForObject(
                "SELECT administrator_flag FROM t_employee WHERE employee_id = ?", Boolean.class, employeeId))
                .as("权限取证账号必须是普通账号")
                .isFalse();
        jdbc.update("INSERT INTO t_role_employee (role_id, employee_id)"
                        + " SELECT r.role_id, ? FROM t_role r WHERE r.role_code = ?",
                employeeId, roleCode);
        evictMybatisCache();
        return employeeId;
    }

    private UserPermission permissionsOf(Long employeeId) {
        RequestEmployee employee = new RequestEmployee();
        employee.setEmployeeId(employeeId);
        employee.setActualName("F1-6 权限矩阵");
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        employee.setAdministratorFlag(false);
        SmartRequestUtil.setRequestUser(employee);
        return loginManager.loadUserPermission(employeeId);
    }

    private List<Long> menuIdsOfRole(String roleCode) {
        Long roleId = jdbc.queryForObject("SELECT role_id FROM t_role WHERE role_code = ?", Long.class, roleCode);
        return roleMenuService.getMenuList(List.of(roleId), false).stream().map(MenuVO::getMenuId).toList();
    }

    @Test
    @DisplayName("SCM_FINANCE 可访问五个 Finance 页面及全部 13 个受保护能力，司机均不可见")
    void financeRoleHasFinancePagesAndActions() {
        Long financeEmployee = employeeWithRole("FIN", "SCM_FINANCE");
        Long driverEmployee = employeeWithRole("DRV", "SCM_DRIVER");

        assertThat(menuIdsOfRole("SCM_FINANCE")).containsAll(PAGE_IDS);
        assertThat(menuIdsOfRole("SCM_DRIVER")).doesNotContainAnyElementsOf(PAGE_IDS);
        assertThat(permissionsOf(financeEmployee).getPermissionList()).containsAll(FINANCE_PERMISSIONS);
        assertThat(permissionsOf(driverEmployee).getPermissionList()).doesNotContainAnyElementsOf(FINANCE_PERMISSIONS);
    }

    @Test
    @DisplayName("五类查询与导出端点的注解分别对应查询权限及查询 AND 导出权限")
    void readEndpointsUseTheirPublishedPermissions() throws Exception {
        assertPermission(FinanceReadController.class, "receivableQuery", FinanceReceivableQueryForm.class,
                FinancePermission.RECEIVABLE_QUERY);
        assertPermission(FinanceReadController.class, "payableQuery", FinancePayableQueryForm.class,
                FinancePermission.PAYABLE_QUERY);
        assertPermission(FinanceReadController.class, "receiptQuery", FinanceReceiptQueryForm.class,
                FinancePermission.RECEIPT_QUERY);
        assertPermission(FinanceReadController.class, "paymentQuery", FinancePaymentQueryForm.class,
                FinancePermission.PAYMENT_QUERY);
        assertPermission(FinanceWriteOffController.class, "query", FinanceWriteOffQueryForm.class,
                FinancePermission.WRITE_OFF_QUERY);

        assertExportPermission(FinanceReadController.class, "exportReceivables", FinanceReceivableQueryForm.class,
                FinancePermission.RECEIVABLE_QUERY);
        assertExportPermission(FinanceReadController.class, "exportPayables", FinancePayableQueryForm.class,
                FinancePermission.PAYABLE_QUERY);
        assertExportPermission(FinanceReadController.class, "exportReceipts", FinanceReceiptQueryForm.class,
                FinancePermission.RECEIPT_QUERY);
        assertExportPermission(FinanceReadController.class, "exportPayments", FinancePaymentQueryForm.class,
                FinancePermission.PAYMENT_QUERY);
        assertExportPermission(FinanceWriteOffController.class, "export", FinanceWriteOffQueryForm.class,
                FinancePermission.WRITE_OFF_QUERY);
    }

    @Test
    @DisplayName("手工红字、收付款登记與反向端点仍各自使用独立权限")
    void writeEndpointsKeepIndependentPermissions() throws Exception {
        assertPermission(FinancePayableController.class, "red",
                com.xsy.scm.finance.domain.form.FinancePayableRedForm.class, FinancePermission.PAYABLE_RED);
        assertPermission(FinanceReceiptController.class, "add",
                com.xsy.scm.finance.domain.form.FinanceReceiptAddForm.class, FinancePermission.RECEIPT_ADD);
        assertPermission(FinanceReceiptController.class, "reverse",
                com.xsy.scm.finance.domain.form.FinanceReceiptReverseForm.class, FinancePermission.RECEIPT_REVERSE);
        assertPermission(FinancePaymentController.class, "add",
                com.xsy.scm.finance.domain.form.FinancePaymentAddForm.class, FinancePermission.PAYMENT_ADD);
        assertPermission(FinancePaymentController.class, "reverse",
                com.xsy.scm.finance.domain.form.FinancePaymentReverseForm.class, FinancePermission.PAYMENT_REVERSE);
        assertPermission(FinancePaymentController.class, "refundOptions",
                FinanceRefundOptionQueryForm.class, FinancePermission.PAYMENT_ADD);
    }

    private static void assertPermission(Class<?> controller, String methodName, Class<?> parameterType,
            String permission) throws Exception {
        Method method = java.util.Arrays.stream(controller.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .filter(candidate -> java.util.Arrays.asList(candidate.getParameterTypes()).contains(parameterType))
                .findFirst().orElseThrow();
        SaCheckPermission annotation = method.getAnnotation(SaCheckPermission.class);
        assertThat(annotation).as("%s#%s 必须受权限保护", controller.getSimpleName(), methodName).isNotNull();
        assertThat(annotation.value()).containsExactly(permission);
    }

    private static void assertExportPermission(Class<?> controller, String methodName, Class<?> formType,
            String queryPermission) throws Exception {
        Method method = java.util.Arrays.stream(controller.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .filter(candidate -> java.util.Arrays.asList(candidate.getParameterTypes()).contains(formType))
                .findFirst().orElseThrow();
        SaCheckPermission annotation = method.getAnnotation(SaCheckPermission.class);
        assertThat(annotation).as("%s#%s 必须受双权限保护", controller.getSimpleName(), methodName).isNotNull();
        assertThat(annotation.value()).containsExactly(queryPermission, FinancePermission.EXPORT);
        assertThat(annotation.mode()).isEqualTo(SaMode.AND);
    }
}
