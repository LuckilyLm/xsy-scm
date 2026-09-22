package net.lab1024.sa.admin.module.system.support;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import net.lab1024.sa.base.common.controller.SupportBaseController;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.RequestUser;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import net.lab1024.sa.base.constant.SwaggerTagConst;
import net.lab1024.sa.base.module.support.operatelog.OperateLogService;
import net.lab1024.sa.base.module.support.operatelog.domain.OperateLogQueryForm;
import net.lab1024.sa.base.module.support.operatelog.domain.OperateLogVO;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 操作日志
 *
 */
@RestController
@Tag(name = SwaggerTagConst.Support.OPERATE_LOG)
public class AdminOperateLogController extends SupportBaseController {

    /**
     * 业务对象筛选白名单 → 该领域对象读取权限。结构化筛选要求同时具备操作日志读取权
     * （方法级 {@code @SaCheckPermission}）与对应领域读取权，避免以商品查询权限拿到完整通用日志。
     */
    private static final Map<String, String> BUSINESS_READ_PERMISSIONS = Map.of(
            "PRODUCT", "scm:product:query",
            "CUSTOMER", "scm:customer:query",
            "DELIVERY_ROUTE", "scm:delivery:route:query"
    );

    @Resource
    private OperateLogService operateLogService;

    @Operation(summary = "分页查询")
    @PostMapping("/operateLog/page/query")
    @SaCheckPermission("support:operateLog:query")
    public ResponseDTO<PageResult<OperateLogVO>> queryByPage(@RequestBody OperateLogQueryForm queryForm) {
        ResponseDTO<PageResult<OperateLogVO>> guardResult = checkBusinessFilterPermission(queryForm);
        if (guardResult != null) {
            return guardResult;
        }
        return operateLogService.queryByPage(queryForm);
    }

    @Operation(summary = "详情")
    @GetMapping("/operateLog/detail/{operateLogId}")
    @SaCheckPermission("support:operateLog:detail")
    public ResponseDTO<OperateLogVO> detail(@PathVariable Long operateLogId) {
        return operateLogService.detail(operateLogId);
    }

    @Operation(summary = "分页查询当前登录人信息")
    @PostMapping("/operateLog/page/query/login")
    public ResponseDTO<PageResult<OperateLogVO>> queryByPageLogin(@RequestBody OperateLogQueryForm queryForm) {
        RequestUser requestUser = SmartRequestUtil.getRequestUser();
        queryForm.setOperateUserId(requestUser.getUserId());
        queryForm.setOperateUserType(requestUser.getUserType().getValue());
        return operateLogService.queryByPage(queryForm);
    }

    /**
     * 业务对象筛选的授权前置：仅在使用结构化筛选时要求领域读取权限。
     *
     * @return 校验不通过时的错误响应；通过或未使用业务筛选时返回 {@code null}
     */
    private ResponseDTO<PageResult<OperateLogVO>> checkBusinessFilterPermission(OperateLogQueryForm queryForm) {
        String businessType = queryForm.getBusinessType();
        Long businessId = queryForm.getBusinessId();
        boolean hasType = businessType != null && !businessType.isEmpty();
        if (!hasType && businessId == null) {
            return null;
        }
        if (!hasType) {
            return ResponseDTO.userErrorParam("业务类型与业务ID必须同时提供");
        }
        String readPermission = BUSINESS_READ_PERMISSIONS.get(businessType);
        if (readPermission == null) {
            return ResponseDTO.userErrorParam("不支持的业务类型");
        }
        // 缺该领域读取权时抛出 NotPermissionException，由全局异常处理返回 30005；administratorFlag 自动放行
        StpUtil.checkPermission(readPermission);
        return null;
    }

}
