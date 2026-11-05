package com.xsy.scm.admin.module.system.support;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import com.xsy.scm.base.common.controller.SupportBaseController;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.RequestUser;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.util.XsyRequestUtil;
import com.xsy.scm.base.constant.SwaggerTagConst;
import com.xsy.scm.base.module.support.loginlog.LoginLogService;
import com.xsy.scm.base.module.support.loginlog.domain.LoginLogQueryForm;
import com.xsy.scm.base.module.support.loginlog.domain.LoginLogVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录日志
 *
 */
@RestController
@Tag(name = SwaggerTagConst.Support.LOGIN_LOG)
public class AdminLoginLogController extends SupportBaseController {

    @Resource
    private LoginLogService loginLogService;

    @Operation(summary = "分页查询")
    @PostMapping("/loginLog/page/query")
    @SaCheckPermission("support:loginLog:query")
    public ResponseDTO<PageResult<LoginLogVO>> queryByPage(@RequestBody LoginLogQueryForm queryForm) {
        return loginLogService.queryByPage(queryForm);
    }

    @Operation(summary = "分页查询当前登录人信息")
    @PostMapping("/loginLog/page/query/login")
    public ResponseDTO<PageResult<LoginLogVO>> queryByPageLogin(@RequestBody LoginLogQueryForm queryForm) {
        RequestUser requestUser = XsyRequestUtil.getRequestUser();
        queryForm.setUserId(requestUser.getUserId());
        queryForm.setUserType(requestUser.getUserType().getValue());
        return loginLogService.queryByPage(queryForm);
    }

}
