package net.lab1024.sa.admin.interceptor;

import cn.dev33.satoken.exception.SaTokenException;
import cn.dev33.satoken.stp.StpUtil;
import com.xsy.scm.mall.account.service.MallAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.annoation.NoNeedLogin;
import net.lab1024.sa.base.common.code.UserErrorCode;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import net.lab1024.sa.base.common.util.SmartResponseUtil;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class MallCustomerInterceptor implements HandlerInterceptor {
    private final MallAuthService authService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            response.setStatus(HttpStatus.NO_CONTENT.value());
            return false;
        }
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        if (method.getMethodAnnotation(NoNeedLogin.class) != null) {
            return true;
        }
        try {
            Object loginId = StpUtil.getLoginIdDefaultNull();
            var customer = authService.requestCustomer(loginId == null ? null : String.valueOf(loginId), request);
            if (customer == null) {
                SmartResponseUtil.write(response, ResponseDTO.error(UserErrorCode.LOGIN_STATE_INVALID));
                return false;
            }
            SmartRequestUtil.setRequestUser(customer);
            StpUtil.updateLastActiveToNow();
            return true;
        } catch (SaTokenException exception) {
            SmartResponseUtil.write(response, ResponseDTO.error(UserErrorCode.LOGIN_STATE_INVALID));
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        SmartRequestUtil.remove();
    }
}
