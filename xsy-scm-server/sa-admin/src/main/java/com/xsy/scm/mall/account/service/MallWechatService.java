package com.xsy.scm.mall.account.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.mall.account.constant.MallErrorCode;
import java.time.Duration;
import java.util.Map;
import net.lab1024.sa.base.module.support.redis.RedisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 微信官方手机号取号，凭据只驻留后端；不将客户端自报手机号作为认证依据。 */
@Service
public class MallWechatService {
    private final RedisService redis;
    private final String appId;
    private final String appSecret;
    private final RestClient wechatClient;

    /**
     * @param redis
     *            既有环境隔离的缓存服务，不可为空
     * @param appId
     *            当前小程序AppID，空值时快捷登录失败关闭
     * @param appSecret
     *            当前小程序服务端密钥，空值时快捷登录失败关闭
     */
    @Autowired
    public MallWechatService(RedisService redis, @Value("${XSY_WECHAT_APP_ID:}") String appId,
        @Value("${XSY_WECHAT_APP_SECRET:}") String appSecret) {
        this.redis = redis;
        this.appId = appId;
        this.appSecret = appSecret;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(6));
        // 微信网关不接受分块请求体；先缓冲小型JSON以发送明确的Content-Length。
        this.wechatClient = RestClient.builder().baseUrl("https://api.weixin.qq.com")
            .requestFactory(new BufferingClientHttpRequestFactory(factory)).build();
    }

    MallWechatService(RedisService redis, String appId, String appSecret, RestClient wechatClient) {
        this.redis = redis;
        this.appId = appId;
        this.appSecret = appSecret;
        this.wechatClient = wechatClient;
    }

    /**
     * @param code
     *            getPhoneNumber事件的一次性code，不是wx.login的code；空值或超长拒绝
     * @return 微信确认属于当前小程序的国内纯手机号
     * @throws ScmBusinessException
     *             授权无效或微信服务不可用；不携带上游原始凭据与响应
     */
    public String phone(String code) {
        if (code == null || code.isBlank() || code.length() > 512) {
            throw new ScmBusinessException(MallErrorCode.WECHAT_CODE_INVALID);
        }
        if (appId.isBlank() || appSecret.isBlank()) {
            throw new ScmBusinessException(MallErrorCode.WECHAT_UNAVAILABLE);
        }
        try {
            String token = accessToken(null);
            JsonNode result = phoneInfo(code, token);
            int error = result.path("errcode").asInt(-1);
            // 仅在微信明确拒绝访问令牌时重试，网络超时或授权code无效不重复消费code。
            if (error == 40001 || error == 40014 || error == 42001) {
                result = phoneInfo(code, accessToken(token));
                error = result.path("errcode").asInt(-1);
            }
            if (error == 40029 || error == 40163) {
                throw new ScmBusinessException(MallErrorCode.WECHAT_CODE_INVALID);
            }
            if (error != 0) {
                throw new ScmBusinessException(MallErrorCode.WECHAT_UNAVAILABLE);
            }
            JsonNode info = result.path("phone_info");
            String phone = info.path("purePhoneNumber").asText("");
            if (!appId.equals(info.path("watermark").path("appid").asText())
                || !"86".equals(info.path("countryCode").asText()) || !phone.matches("1\\d{10}")) {
                throw new ScmBusinessException(MallErrorCode.WECHAT_CODE_INVALID);
            }
            return phone;
        } catch (RestClientException exception) {
            // 上游异常可能携带access_token或响应体，不能透传到统一异常日志。
            throw new ScmBusinessException(MallErrorCode.WECHAT_UNAVAILABLE);
        }
    }

    private JsonNode phoneInfo(String code, String token) {
        JsonNode result = wechatClient.post().uri("/wxa/business/getuserphonenumber?access_token={token}", token)
            .body(Map.of("code", code)).retrieve().body(JsonNode.class);
        if (result == null) {
            throw new ScmBusinessException(MallErrorCode.WECHAT_UNAVAILABLE);
        }
        return result;
    }

    private synchronized String accessToken(String rejectedToken) {
        String key = redis.generateRedisKey("scm:mall:wechat:access-token:", appId);
        String cached = redis.get(key);
        if (cached != null && !cached.isBlank() && !cached.equals(rejectedToken)) {
            return cached;
        }
        // stable_token在多实例正常取号时复用同一令牌，不因各实例缓存未命中互相挤掉会话。
        JsonNode result = wechatClient.post().uri("/cgi-bin/stable_token").body(Map.of("grant_type",
            "client_credential", "appid", appId, "secret", appSecret, "force_refresh", rejectedToken != null))
            .retrieve().body(JsonNode.class);
        if (result == null || result.path("errcode").asInt(0) != 0 || result.path("access_token").asText("").isBlank()
            || result.path("expires_in").asLong() <= 60) {
            throw new ScmBusinessException(MallErrorCode.WECHAT_UNAVAILABLE);
        }
        String token = result.path("access_token").asText();
        redis.set(key, token, result.path("expires_in").asLong() - 60);
        return token;
    }
}
