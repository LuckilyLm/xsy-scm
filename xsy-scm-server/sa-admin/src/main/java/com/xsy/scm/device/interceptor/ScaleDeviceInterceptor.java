package com.xsy.scm.device.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.device.constant.ScaleDeviceErrorCode;
import com.xsy.scm.device.service.ScaleDeviceAuthService;
import com.xsy.scm.device.support.ScaleDeviceContext;
import com.xsy.scm.device.support.ScaleDevicePrincipal;
import com.xsy.scm.device.support.ScaleDeviceResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.system.employee.domain.entity.EmployeeEntity;
import net.lab1024.sa.admin.module.system.employee.service.EmployeeService;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 设备接入的身份闸门。
 *
 * <p>
 * 校验设备凭证与员工授权会话，并把员工写入请求上下文，使下游 {@code ScmOperator}、 {@code ScmDataScopeService} 与 {@code SortingTaskAccess}
 * 都能正常解析出真实员工。 不做这一步，任何复用员工业务命令的设备接口都会因为没有登录人而直接失败。
 *
 * <p>
 * 员工身份来自服务端会话表，不接受客户端自报员工 ID：否则秤端可以伪造成任意员工做分拣确认。
 */
@Component
@RequiredArgsConstructor
public class ScaleDeviceInterceptor implements HandlerInterceptor {

    public static final String HEADER_OPERATOR_SESSION = "X-Operator-Session";

    /** 激活本身还没有设备凭证，必须放行；其余设备接口一律要求双重身份。 */
    private static final String ACTIVATE_PATH = "/agents/activate";

    private final ScaleDeviceAuthService scaleDeviceAuthService;

    private final EmployeeService employeeService;

    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            response.setStatus(HttpStatus.NO_CONTENT.value());
            return false;
        }
        if (request.getRequestURI().endsWith(ACTIVATE_PATH)) {
            return true;
        }
        try {
            String credential = bearerToken(request);
            String operatorSession = request.getHeader(HEADER_OPERATOR_SESSION);
            ScaleDevicePrincipal principal = scaleDeviceAuthService.authenticate(credential, operatorSession);
            EmployeeEntity employee = employeeService.getById(principal.employeeId());
            if (employee == null || Boolean.TRUE.equals(employee.getDisabledFlag())) {
                throw new ScmBusinessException(ScaleDeviceErrorCode.OPERATOR_INVALID);
            }
            ScaleDeviceContext.set(principal);
            SmartRequestUtil.setRequestUser(requestEmployee(employee, request));
            return true;
        } catch (ScmBusinessException exception) {
            writeRejection(response, HttpStatus.UNAUTHORIZED, exception);
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            Exception exception) {
        ScaleDeviceContext.clear();
        SmartRequestUtil.remove();
    }

    private RequestEmployee requestEmployee(EmployeeEntity employee, HttpServletRequest request) {
        RequestEmployee user = new RequestEmployee();
        user.setEmployeeId(employee.getEmployeeId());
        user.setLoginName(employee.getLoginName());
        user.setActualName(employee.getActualName());
        user.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        user.setIp(request.getRemoteAddr());
        user.setUserAgent(request.getHeader(HttpHeaders.USER_AGENT));
        return user;
    }

    /**
     * 拒绝时用设备协议自己的响应体：{@code code} 必须是可判定的字符串错误码， 后台的 {@code ResponseDTO} 在这里会被秤端当成解析失败。
     */
    private void writeRejection(HttpServletResponse response, HttpStatus status, ScmBusinessException exception)
            throws IOException {
        String code = exception.getErrorCode() instanceof Enum<?> enumCode ? enumCode.name() : "DEVICE_REJECTED";
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(),
                ScaleDeviceResponse.error(code, exception.getErrorCode().getMsg()));
    }

    private static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        return header.substring(7).trim();
    }
}
