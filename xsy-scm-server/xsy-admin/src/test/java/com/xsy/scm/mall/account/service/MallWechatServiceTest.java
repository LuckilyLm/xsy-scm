package com.xsy.scm.mall.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.mall.account.constant.MallErrorCode;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import com.xsy.scm.base.module.support.redis.RedisService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

class MallWechatServiceTest {
    private final RedisService redis = mock(RedisService.class);
    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://api.weixin.qq.com");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final MallWechatService service = new MallWechatService(redis, "wx-test", "test-secret", builder.build());

    private void cache(String token) {
        when(redis.generateRedisKey("scm:mall:wechat:access-token:", "wx-test")).thenReturn("test-token-key");
        when(redis.get("test-token-key")).thenReturn(token);
    }

    private String phone(String appId) {
        return "{\"errcode\":0,\"phone_info\":{\"purePhoneNumber\":\"13800000000\",\"countryCode\":\"86\","
            + "\"watermark\":{\"appid\":\"" + appId + "\"}}}";
    }

    @Test
    void realTransportSendsFixedLengthJsonForBothWechatRequests() throws Exception {
        cache(null);
        HttpServer upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        List<Boolean> fixedLengthRequests = new ArrayList<>();
        upstream.createContext("/", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            boolean fixedLength = String.valueOf(body.length).equals(exchange.getRequestHeaders().getFirst("Content-Length"))
                && exchange.getRequestHeaders().getFirst("Transfer-Encoding") == null;
            fixedLengthRequests.add(fixedLength);
            String response = exchange.getRequestURI().getPath().equals("/cgi-bin/stable_token")
                ? "{\"access_token\":\"new-token\",\"expires_in\":7200}" : phone("wx-test");
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(fixedLength ? 200 : 411, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
            exchange.close();
        });
        upstream.start();
        try {
            var configured = new MallWechatService(redis, "wx-test", "test-secret");
            // 沿用正式构造器的传输配置，仅将固定官方地址替换为本地验收网关。
            var client = ((RestClient) ReflectionTestUtils.getField(configured, "wechatClient")).mutate()
                .baseUrl("http://127.0.0.1:" + upstream.getAddress().getPort()).build();
            var realTransport = new MallWechatService(redis, "wx-test", "test-secret", client);
            assertThat(realTransport.phone("one-time-code")).isEqualTo("13800000000");
            assertThat(fixedLengthRequests).containsExactly(true, true);
            verify(redis).set("test-token-key", "new-token", 7140L);
        } finally {
            upstream.stop(0);
        }
    }

    @Test
    void cachedTokenExchangesOnlyTheOfficialPhoneAuthorizationCode() {
        cache("cached-token");
        server.expect(requestTo("https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=cached-token"))
            .andExpect(method(HttpMethod.POST)).andExpect(content().json("{\"code\":\"one-time-code\"}"))
            .andRespond(withSuccess(phone("wx-test"), MediaType.APPLICATION_JSON));
        assertThat(service.phone("one-time-code")).isEqualTo("13800000000");
        server.verify();
    }

    @Test
    void stableAccessTokenIsCachedWithExpiryMargin() {
        cache(null);
        server.expect(requestTo("https://api.weixin.qq.com/cgi-bin/stable_token"))
            .andExpect(content().json("{\"grant_type\":\"client_credential\",\"appid\":\"wx-test\","
                + "\"secret\":\"test-secret\",\"force_refresh\":false}"))
            .andRespond(withSuccess("{\"access_token\":\"new-token\",\"expires_in\":7200}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=new-token"))
            .andRespond(withSuccess(phone("wx-test"), MediaType.APPLICATION_JSON));
        assertThat(service.phone("code")).isEqualTo("13800000000");
        verify(redis).set("test-token-key", "new-token", 7140L);
        server.verify();
    }

    @Test
    void explicitExpiredAccessTokenRefreshesOnce() {
        cache("expired-token");
        server.expect(requestTo("https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=expired-token"))
            .andRespond(withSuccess("{\"errcode\":42001}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.weixin.qq.com/cgi-bin/stable_token"))
            .andExpect(content().json("{\"force_refresh\":true}", false))
            .andRespond(withSuccess("{\"access_token\":\"fresh-token\",\"expires_in\":7200}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=fresh-token"))
            .andRespond(withSuccess(phone("wx-test"), MediaType.APPLICATION_JSON));
        assertThat(service.phone("code")).isEqualTo("13800000000");
        server.verify();
    }

    @Test
    void consumedAuthorizationIsNotRetried() {
        cache("cached-token");
        server.expect(requestTo("https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=cached-token"))
            .andRespond(withSuccess("{\"errcode\":40163}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> service.phone("used-code"))
            .isInstanceOfSatisfying(ScmBusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(MallErrorCode.WECHAT_CODE_INVALID));
        server.verify();
    }

    @Test
    void anotherAppPhoneCannotAuthenticateHere() {
        cache("cached-token");
        server.expect(requestTo("https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=cached-token"))
            .andRespond(withSuccess(phone("another-app"), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> service.phone("code"))
            .isInstanceOfSatisfying(ScmBusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(MallErrorCode.WECHAT_CODE_INVALID));
        server.verify();
    }

    @Test
    void providerTimeoutDoesNotReplayCodeOrExposeAccessToken() {
        cache("private-token");
        server.expect(requestTo("https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=private-token"))
            .andRespond(request -> { throw new SocketTimeoutException("upstream timeout"); });
        assertThatThrownBy(() -> service.phone("code"))
            .isInstanceOfSatisfying(ScmBusinessException.class, error -> {
                assertThat(error.getErrorCode()).isEqualTo(MallErrorCode.WECHAT_UNAVAILABLE);
                assertThat(error.getMessage()).doesNotContain("private-token");
                assertThat(error.getCause()).isNull();
            });
        server.verify();
    }

    @Test
    void upstreamHttpFailureHasReadableBusinessError() {
        cache("cached-token");
        server.expect(requestTo("https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=cached-token"))
            .andRespond(withServerError());
        assertThatThrownBy(() -> service.phone("code"))
            .isInstanceOfSatisfying(ScmBusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(MallErrorCode.WECHAT_UNAVAILABLE));
        server.verify();
    }

    @Test
    void missingCredentialsAndEmptyCodesNeverCallWechat() {
        var unconfigured = new MallWechatService(redis, "", "", builder.build());
        assertThatThrownBy(() -> unconfigured.phone("code"))
            .isInstanceOfSatisfying(ScmBusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(MallErrorCode.WECHAT_UNAVAILABLE));
        assertThatThrownBy(() -> service.phone(" "))
            .isInstanceOfSatisfying(ScmBusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(MallErrorCode.WECHAT_CODE_INVALID));
        server.verify();
    }
}
