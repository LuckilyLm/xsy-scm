package net.lab1024.sa.admin.module.system.support;

import cn.dev33.satoken.stp.StpUtil;
import net.lab1024.sa.base.common.code.UserErrorCode;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.OperateLogService;
import net.lab1024.sa.base.module.support.operatelog.domain.OperateLogQueryForm;
import net.lab1024.sa.base.module.support.operatelog.domain.OperateLogVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 操作日志入口的业务筛选授权前置：结构化筛选必须先通过对应领域读取权校验才允许落到查询服务。
 */
class AdminOperateLogBusinessGuardTest {

    final OperateLogService service = mock(OperateLogService.class);
    final AdminOperateLogController controller = controller();

    private AdminOperateLogController controller() {
        AdminOperateLogController c = new AdminOperateLogController();
        ReflectionTestUtils.setField(c, "operateLogService", service);
        return c;
    }

    private OperateLogQueryForm form(String businessType, Long businessId) {
        OperateLogQueryForm f = new OperateLogQueryForm();
        f.setPageNum(1L);
        f.setPageSize(20L);
        f.setBusinessType(businessType);
        f.setBusinessId(businessId);
        return f;
    }

    @ParameterizedTest
    @CsvSource({
            "PRODUCT, scm:product:query",
            "CUSTOMER, scm:customer:query",
            "DELIVERY_ROUTE, scm:delivery:route:query",
    })
    void structuredFilterChecksTheMatchingDomainReadPermissionBeforeQuerying(String businessType, String permission) {
        when(service.queryByPage(any())).thenReturn(ResponseDTO.ok(new PageResult<OperateLogVO>()));
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            assertThat(controller.queryByPage(form(businessType, 12L)).getOk()).isTrue();
            stp.verify(() -> StpUtil.checkPermission(permission));
            verify(service).queryByPage(any());
        }
    }

    @Test
    void unsupportedBusinessTypeIsRejectedWithoutQueryingOrPermissionLookup() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            ResponseDTO<PageResult<OperateLogVO>> result = controller.queryByPage(form("WAREHOUSE", 12L));
            assertThat(result.getOk()).isFalse();
            assertThat(result.getCode()).isEqualTo(UserErrorCode.PARAM_ERROR.getCode());
            stp.verify(() -> StpUtil.checkPermission(anyString()), never());
            verifyNoInteractions(service);
        }
    }

    @Test
    void businessIdWithoutTypeIsRejectedWithoutQuerying() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            ResponseDTO<PageResult<OperateLogVO>> result = controller.queryByPage(form(null, 12L));
            assertThat(result.getCode()).isEqualTo(UserErrorCode.PARAM_ERROR.getCode());
            stp.verify(() -> StpUtil.checkPermission(anyString()), never());
            verifyNoInteractions(service);
        }
    }

    @Test
    void plainLogQueryWithoutBusinessFilterSkipsPermissionLookupAndQueries() {
        when(service.queryByPage(any())).thenReturn(ResponseDTO.ok(new PageResult<OperateLogVO>()));
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            assertThat(controller.queryByPage(form(null, null)).getOk()).isTrue();
            stp.verify(() -> StpUtil.checkPermission(anyString()), never());
            verify(service).queryByPage(any());
        }
    }
}
