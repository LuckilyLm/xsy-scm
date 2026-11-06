package com.xsy.scm.admin.module.system.support;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import com.xsy.scm.base.common.controller.SupportBaseController;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.RequestUser;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.util.XsyRequestUtil;
import com.xsy.scm.base.constant.SwaggerTagConst;
import com.xsy.scm.base.module.support.operatelog.OperateLogBusinessType;
import com.xsy.scm.base.module.support.operatelog.OperateLogService;
import com.xsy.scm.base.module.support.operatelog.domain.OperateLogQueryForm;
import com.xsy.scm.base.module.support.operatelog.domain.OperateLogVO;
import com.xsy.scm.customer.permission.CustomerPermission;
import com.xsy.scm.delivery.permission.DeliveryPermission;
import com.xsy.scm.product.permission.ProductPermission;
import org.springframework.web.bind.annotation.*;

/**
 * 操作日志
 *
 */
@RestController
@Tag(name = SwaggerTagConst.Support.OPERATE_LOG)
public class AdminOperateLogController extends SupportBaseController {

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
        ResponseDTO<OperateLogVO> responseDTO = operateLogService.detail(operateLogId);
        if (!Boolean.TRUE.equals(responseDTO.getOk())) {
            return responseDTO;
        }
        // 日志正文含领域对象明细，能识别归属时必须再要求该领域读取权；识别不出的一律按通用日志权限处理
        OperateLogVO log = responseDTO.getData();
        for (String businessType : OperateLogBusinessType.recognize(log.getParam(), log.getUrl())) {
            requireBusinessReadPermission(businessType);
        }
        return responseDTO;
    }

    @Operation(summary = "分页查询当前登录人信息")
    @PostMapping("/operateLog/page/query/login")
    public ResponseDTO<PageResult<OperateLogVO>> queryByPageLogin(@RequestBody OperateLogQueryForm queryForm) {
        ResponseDTO<PageResult<OperateLogVO>> guardResult = checkBusinessFilterPermission(queryForm);
        if (guardResult != null) {
            return guardResult;
        }
        RequestUser requestUser = XsyRequestUtil.getRequestUser();
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
        if (!requireBusinessReadPermission(businessType)) {
            return ResponseDTO.userErrorParam("不支持的业务类型");
        }
        return null;
    }

    /**
     * 按业务类型校验领域读取权；缺权限时抛出 NotPermissionException，由全局异常处理返回 30005。
     *
     * @return 业务类型是否受支持
     */
    private static boolean requireBusinessReadPermission(String businessType) {
        switch (businessType) {
            case OperateLogBusinessType.PRODUCT -> StpUtil.checkPermission(ProductPermission.QUERY);
            case OperateLogBusinessType.CUSTOMER -> StpUtil.checkPermission(CustomerPermission.QUERY);
            case OperateLogBusinessType.DELIVERY_ROUTE -> StpUtil.checkPermission(DeliveryPermission.ROUTE_QUERY);
            default -> {
                return false;
            }
        }
        return true;
    }

}
