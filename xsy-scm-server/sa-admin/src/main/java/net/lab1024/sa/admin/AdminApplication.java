package net.lab1024.sa.admin;

import net.lab1024.sa.base.listener.Ip2RegionListener;
import net.lab1024.sa.base.listener.LogVariableListener;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SmartAdmin 项目启动类
 *
 */
@EnableCaching
@EnableScheduling
@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)
@ComponentScan({AdminApplication.COMPONENT_SCAN, AdminApplication.XSY_COMPONENT_SCAN})
@MapperScan(value = {AdminApplication.COMPONENT_SCAN, AdminApplication.XSY_COMPONENT_SCAN}, annotationClass = Mapper.class)
@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class})
public class AdminApplication {

    /** Spring Boot 同时扫描 SmartAdmin 与 SCM 的组件，保持两套包路径都能完成依赖注入。 */
    public static final String COMPONENT_SCAN = "net.lab1024.sa";

    /**
     * XSY 业务使用 com.xsy，SmartAdmin 底座使用 net.lab1024.sa。
     * 组件、Mapper 和数据权限扫描都必须覆盖这两个包路径。
     */
    public static final String XSY_COMPONENT_SCAN = "com.xsy";

    /** 双根合并视图，供无法使用注解数组的调用方（{@code Reflections}）使用。 */
    public static final String[] COMPONENT_SCAN_PATHS = {COMPONENT_SCAN, XSY_COMPONENT_SCAN};

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(AdminApplication.class);
        // 添加 日志监听器，使 log4j2-spring.xml 可以间接读取到配置文件的属性
        application.addListeners(new LogVariableListener(), new Ip2RegionListener());
        application.run(args);
    }
}
