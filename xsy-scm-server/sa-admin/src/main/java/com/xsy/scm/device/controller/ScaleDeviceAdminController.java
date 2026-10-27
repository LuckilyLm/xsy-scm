package com.xsy.scm.device.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.device.domain.form.ScaleDeviceQueryForm;
import com.xsy.scm.device.domain.form.ScaleDeviceRegisterForm;
import com.xsy.scm.device.domain.form.ScaleOperatorSessionForm;
import com.xsy.scm.device.domain.vo.ScaleDeviceRegisterVO;
import com.xsy.scm.device.domain.vo.ScaleDeviceSessionVO;
import com.xsy.scm.device.domain.vo.ScaleDeviceVO;
import com.xsy.scm.device.domain.vo.ScaleOperatorSessionVO;
import com.xsy.scm.device.permission.ScaleDevicePermission;
import com.xsy.scm.device.service.ScaleDeviceAuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 电子秤设备管理（员工侧）。
 *
 * <p>
 * 和设备接入接口分开授权：这里都是后台员工操作、会记日志，设备凭证调不了，所以设备不能给自己发配对码或延长授权。
 */
@RestController
@RequestMapping("/scm/scale/device")
@Tag(name = "SCM 电子秤设备管理")
@RequiredArgsConstructor
public class ScaleDeviceAdminController {

    private final ScaleDeviceAuthService scaleDeviceAuthService;

    /**
     * 设备台账：绑定仓库、在线状态、心跳与当前有效会话数。按仓库数据范围收窄。
     */
    @PostMapping("/query")
    @SaCheckPermission(ScaleDevicePermission.QUERY)
    public ResponseDTO<PageResult<ScaleDeviceVO>> query(@Valid @RequestBody ScaleDeviceQueryForm form) {
        return ResponseDTO.ok(scaleDeviceAuthService.query(form));
    }

    /**
     * 某台秤最近的员工会话。不返回令牌摘要，只回答「谁、从何时到何时、是否仍有效」。
     */
    @GetMapping("/{id}/sessions")
    @SaCheckPermission(ScaleDevicePermission.QUERY)
    public ResponseDTO<List<ScaleDeviceSessionVO>> sessions(@PathVariable("id") Long id) {
        return ResponseDTO.ok(scaleDeviceAuthService.sessions(id));
    }

    /**
     * 结束单条员工会话：秤端下次请求即失效，不必到设备上操作。
     */
    @PostMapping("/{id}/session/{sessionId}/end")
    @SaCheckPermission(ScaleDevicePermission.SESSION_ISSUE)
    @OperateLog
    public ResponseDTO<String> endSession(@PathVariable("id") Long id, @PathVariable("sessionId") Long sessionId) {
        scaleDeviceAuthService.endSession(id, sessionId);
        return ResponseDTO.ok();
    }

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
