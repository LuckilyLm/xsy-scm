package com.xsy.scm.device.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.device.domain.form.ScaleDeviceRegisterForm;
import com.xsy.scm.device.domain.form.ScaleOperatorSessionForm;
import com.xsy.scm.device.domain.vo.ScaleDeviceRegisterVO;
import com.xsy.scm.device.domain.vo.ScaleOperatorSessionVO;
import com.xsy.scm.device.permission.ScaleDevicePermission;
import com.xsy.scm.device.service.ScaleDeviceAuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 电子秤设备管理（员工侧）。
 *
 * <p>
 * 与设备接入接口分开授权：这里的每个动作都由后台员工发起并记入操作日志， 设备凭证无法调用，因此设备不能自己给自己发配对码或延长授权。
 */
@RestController
@RequestMapping("/scm/scale/device")
@Tag(name = "SCM 电子秤设备管理")
@RequiredArgsConstructor
public class ScaleDeviceAdminController {

    private final ScaleDeviceAuthService scaleDeviceAuthService;

    /**
     * 登记设备并生成一次性配对码。配对码明文只在本次响应里出现。
     */
    @PostMapping("/register")
    @SaCheckPermission(ScaleDevicePermission.BIND)
    @OperateLog
    public ResponseDTO<ScaleDeviceRegisterVO> register(@Valid @RequestBody ScaleDeviceRegisterForm form) {
        return ResponseDTO.ok(scaleDeviceAuthService.register(form));
    }

    /**
     * 为设备上的员工签发操作会话。令牌只返回一次，过期后需重新签发。
     */
    @PostMapping("/{id}/session")
    @SaCheckPermission(ScaleDevicePermission.SESSION_ISSUE)
    @OperateLog
    public ResponseDTO<ScaleOperatorSessionVO> issueSession(@PathVariable("id") Long id,
        @Valid @RequestBody ScaleOperatorSessionForm form) {
        return ResponseDTO.ok(scaleDeviceAuthService.issueOperatorSession(id, form));
    }

    /**
     * 撤销绑定：设备立即失去接入能力，该设备上的员工会话同时结束。历史读数保留。
     */
    @PostMapping("/{id}/revoke")
    @SaCheckPermission(ScaleDevicePermission.BIND)
    @OperateLog
    public ResponseDTO<String> revoke(@PathVariable("id") Long id) {
        scaleDeviceAuthService.revoke(id);
        return ResponseDTO.ok();
    }
}
