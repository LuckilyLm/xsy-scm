package net.lab1024.sa.admin.config;

import jakarta.annotation.Resource;
import net.lab1024.sa.admin.interceptor.AdminInterceptor;
import net.lab1024.sa.admin.interceptor.MallCustomerInterceptor;
import com.xsy.scm.device.interceptor.ScaleDeviceInterceptor;
import net.lab1024.sa.base.config.SwaggerConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * web相关配置
 *
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private AdminInterceptor adminInterceptor;

    @Resource
    private MallCustomerInterceptor mallCustomerInterceptor;

    @Resource
    private ScaleDeviceInterceptor scaleDeviceInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(mallCustomerInterceptor)
                .addPathPatterns("/scm/mall/**")
                // 推广码落地页免登录：扫码的人可能还没有商城账号
                .excludePathPatterns("/scm/mall/promo/**");
        // 设备接入用自己的双重身份：设备凭证 + 员工授权会话，不走员工登录态。
        registry.addInterceptor(scaleDeviceInterceptor)
                .addPathPatterns("/scm/device/scale/v1/**");
        registry.addInterceptor(adminInterceptor)
                .excludePathPatterns(SwaggerConfig.SWAGGER_WHITELIST)
                .excludePathPatterns("/scm/mall/**")
                // 设备接口由 ScaleDeviceInterceptor 校验，员工登录态在这里没有意义
                .excludePathPatterns("/scm/device/scale/v1/**")
                // 采购单分享页由令牌授权：手机端不登录，令牌的签发与撤销都在后台完成
                .excludePathPatterns("/scm/purchase/share/public/**")
                .addPathPatterns("/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("doc.html").addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/");
    }

}
