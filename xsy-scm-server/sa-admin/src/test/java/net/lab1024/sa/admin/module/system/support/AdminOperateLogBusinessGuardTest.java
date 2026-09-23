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
 * 操作日志入口的领域权限交集：列表的结构化筛选必须先通过对应领域读取权；详情按日志正文
 * 可识别的归属（商品 / 客户 / 线路）叠加对应读取权，识别不出归属的系统日志仍只按日志权限。
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

    @ParameterizedTest
    @CsvSource({
            "[{\"spuId\":12}],/api/product/spu/edit,scm:product:query",
            "[{\"customerId\":9}],/api/scm/customer/edit,scm:customer:query",
            "[{\"version\":0}],/api/scm/delivery/routes/12/plan,scm:delivery:route:query",
    })
    void detailChecksTheDomainReadPermissionItBelongsTo(String param, String url, String permission) {
        when(service.detail(1L)).thenReturn(ResponseDTO.ok(log(param, url)));
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            assertThat(controller.detail(1L).getOk()).isTrue();
            stp.verify(() -> StpUtil.checkPermission(permission));
        }
    }

    @Test
    void detailRequiresEveryRecognizedDomainPermission() {
        // 一条订单操作日志同时带 spuId 与 customerId，正文含两个领域的明细
        when(service.detail(1L)).thenReturn(ResponseDTO.ok(log("[{\"spuId\":12,\"customerId\":9}]", "/api/scm/order/edit")));
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            assertThat(controller.detail(1L).getOk()).isTrue();
            stp.verify(() -> StpUtil.checkPermission("scm:product:query"));
            stp.verify(() -> StpUtil.checkPermission("scm:customer:query"));
        }
    }

    @Test
    void detailOfUnrecognizedSystemLogKeepsOnlyLogPermission() {
        when(service.detail(1L)).thenReturn(ResponseDTO.ok(log("[{\"configKey\":\"sys.name\"}]", "/api/config/update")));
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            assertThat(controller.detail(1L).getOk()).isTrue();
            stp.verify(() -> StpUtil.checkPermission(anyString()), never());
        }
    }

    @Test
    void failedDetailLookupDoesNotTriggerAnyPermissionLookup() {
        when(service.detail(1L)).thenReturn(ResponseDTO.error(UserErrorCode.DATA_NOT_EXIST));
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            assertThat(controller.detail(1L).getOk()).isFalse();
            stp.verify(() -> StpUtil.checkPermission(anyString()), never());
        }
    }

    private OperateLogVO log(String param, String url) {
        OperateLogVO vo = new OperateLogVO();
        vo.setParam(param);
        vo.setUrl(url);
        return vo;
    }
}
