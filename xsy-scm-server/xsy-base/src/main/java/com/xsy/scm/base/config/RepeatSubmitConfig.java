package com.xsy.scm.base.config;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import com.xsy.scm.base.common.constant.StringConst;
import com.xsy.scm.base.common.util.XsyRequestUtil;
import com.xsy.scm.base.module.support.repeatsubmit.RepeatSubmitAspect;
import com.xsy.scm.base.module.support.repeatsubmit.ticket.RepeatSubmitRedisTicket;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * 重复提交配置
 *
 */
@Configuration
public class RepeatSubmitConfig {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Bean
    public RepeatSubmitAspect repeatSubmitAspect() {
        RepeatSubmitRedisTicket ticket = new RepeatSubmitRedisTicket(redisTemplate, this::ticket);
        return new RepeatSubmitAspect(ticket);
    }

    /**
     * 获取指明某个用户的凭证
     */
    private String ticket(HttpServletRequest request) {
        Long userId = XsyRequestUtil.getRequestUserId();
        if (null == userId) {
            return StringConst.EMPTY;
        }
        return request.getServletPath() + "_" + userId;
    }
}
