package com.xsy.scm.common.json;

import com.xsy.scm.admin.module.system.employee.dao.EmployeeDao;
import com.xsy.scm.admin.module.system.employee.domain.entity.EmployeeEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 操作人展示规则：页面上只出现人名或身份，任何 {@code userType:userId} 都不原样落地。
 */
class ScmOperatorNameResolverTest {

    /** 员工表里只有 7 号员工；其余编号视为账号已被物理删除。 */
    private ScmOperatorNameResolver resolver() {
        EmployeeEntity employee = new EmployeeEntity();
        employee.setEmployeeId(7L);
        employee.setActualName("张三");
        EmployeeDao employeeDao = mock(EmployeeDao.class);
        when(employeeDao.selectList(any())).thenReturn(List.of(employee));
        return new ScmOperatorNameResolver(employeeDao);
    }

    @Test
    @DisplayName("员工编号换成姓名")
    void employeeIdBecomesName() {
        assertThat(resolver().display("1:7")).isEqualTo("张三");
    }

    @Test
    @DisplayName("员工表里查不到时给「已删除员工」，不露原始编号")
    void missingEmployeeDoesNotLeakRawId() {
        assertThat(resolver().display("1:999")).isEqualTo(ScmOperatorNameResolver.UNKNOWN_EMPLOYEE);
    }

    @Test
    @DisplayName("商城客户只给身份，不露账号编号")
    void mallCustomerShowsIdentityOnly() {
        assertThat(resolver().display("2:44")).isEqualTo(ScmOperatorNameResolver.MALL_CUSTOMER);
    }

    @Test
    @DisplayName("已经是人名或系统标识的值原样返回")
    void plainValuesStayUntouched() {
        assertThat(resolver().display("系统管理员")).isEqualTo("系统管理员");
        assertThat(resolver().display("SYSTEM")).isEqualTo("SYSTEM");
        assertThat(resolver().display(null)).isNull();
    }
}
