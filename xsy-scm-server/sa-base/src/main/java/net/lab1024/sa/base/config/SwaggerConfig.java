package net.lab1024.sa.base.config;

import com.google.common.collect.Lists;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.base.common.constant.RequestHeaderConst;
import net.lab1024.sa.base.common.swagger.SmartOperationCustomizer;
import net.lab1024.sa.base.constant.SwaggerTagConst;
import org.apache.commons.lang3.StringUtils;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.customizers.OpenApiBuilderCustomizer;
import org.springdoc.core.customizers.ServerBaseUrlCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.providers.JavadocProvider;
import org.springdoc.core.service.OpenAPIService;
import org.springdoc.core.service.SecurityService;
import org.springdoc.core.utils.PropertyResolverUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;

import java.util.List;
import java.util.Optional;

/**
 * springdoc-openapi 配置
 * nginx配置前缀时如果需要访问【/swagger-ui/index.html】需添加额外nginx配置
 *  location /v3/api-docs/ {
 *          proxy_pass  http://127.0.0.1:1024/v3/api-docs/;
 * }
 */
@Slf4j
@Configuration
@Conditional(SystemEnvironmentConfig.class)
public class SwaggerConfig {
    /**
     * 用于解决/swagger-ui/index.html页面ServersUrl 测试环境部署错误问题
     */
    @Value("${springdoc.swagger-ui.server-base-url}")
    private String serverBaseUrl;

    public static final String[] SWAGGER_WHITELIST = {
            "/swagger-ui/**",
            "/swagger-ui/index.html",
            "/swagger-ui.html",
            "/swagger-ui.html/**",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/doc.html",
    };

    @Bean
    public OpenAPI api() {
        return new OpenAPI()
                .components(components())
                .info(new Info()
                        .title("鲜蔬源智链 接口文档")
                        .contact(new Contact().name("鲜蔬源智慧供应链管理平台"))
                        .version("v2.0")
                        .description("鲜蔬源智慧供应链管理平台后端接口文档，覆盖商品、客户、供应商、价格、销售订单、采购、收货、仓储等全链路业务。" +
                                "<br/>支持客户专属定价、实际重量收货、订单全流程追溯，并提供登录限制、接口加解密等安全能力。")
                )
                .addSecurityItem(new SecurityRequirement().addList(RequestHeaderConst.TOKEN));
    }

    private Components components() {
        return new Components()
                .addSecuritySchemes(RequestHeaderConst.TOKEN, new SecurityScheme().scheme("Bearer").description("请输入token,格式为[Bearer xxxxxxxx]").type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name(RequestHeaderConst.TOKEN));
    }

    @Bean
    public GlobalOpenApiCustomizer orderGlobalOpenApiCustomizer() {
        return openApi -> {
            // 全局添加鉴权参数
            if(openApi.getPaths()!=null){
                openApi.getPaths().forEach((s, pathItem) -> {
                    // 为所有接口添加鉴权
                    pathItem.readOperations().forEach(operation -> {
                        operation.addSecurityItem(new SecurityRequirement().addList(HttpHeaders.AUTHORIZATION));
                    });
                });
            }
        };
    }

    @Bean
    public GroupedOpenApi businessApi() {
        return GroupedOpenApi.builder()
                .group("业务接口")
                .pathsToMatch("/**")
                .pathsToExclude(SwaggerTagConst.Support.URL_PREFIX + "/**")
                .addOperationCustomizer(new SmartOperationCustomizer())
                .build();

    }

    @Bean
    public GroupedOpenApi supportApi() {
        return GroupedOpenApi.builder()
                .group("支撑接口(Support)")
                .pathsToMatch(SwaggerTagConst.Support.URL_PREFIX + "/**")
                .addOperationCustomizer(new SmartOperationCustomizer())
                .build();
    }

    /**
     * 以下代码可以用于设置 /swagger-ui/index.html 的serverBaseUrl
     * 如果使用knife4j则不需要
     * @param openAPI
     * @param securityParser
     * @param springDocConfigProperties
     * @param propertyResolverUtils
     * @param openApiBuilderCustomizers
     * @param serverBaseUrlCustomizers
     * @param javadocProvider
     * @return
     */
    @Bean
    public OpenAPIService openApiBuilder(Optional<OpenAPI> openAPI,
                                         SecurityService securityParser,
                                         SpringDocConfigProperties springDocConfigProperties,
                                         PropertyResolverUtils propertyResolverUtils,
                                         Optional<List<OpenApiBuilderCustomizer>> openApiBuilderCustomizers,
                                         Optional<List<ServerBaseUrlCustomizer>> serverBaseUrlCustomizers,
                                         Optional<JavadocProvider> javadocProvider) {
        List<ServerBaseUrlCustomizer> list = Lists.newArrayList(new ServerBaseUrlCustomizer() {
            @Override
            public String customize(String serverBaseUrl, HttpRequest request) {
                if (StringUtils.isNotBlank(serverBaseUrl)) {
                    return serverBaseUrl;
                }
                return serverBaseUrl;
            }
        });
        return new OpenAPIService(openAPI, securityParser, springDocConfigProperties,
                propertyResolverUtils, openApiBuilderCustomizers, Optional.of(list), javadocProvider);
    }
}
