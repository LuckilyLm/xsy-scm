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

    /** SmartAdmin 底座扫描根（{@code net.lab1024.sa}），迁包期间与 {@link #XSY_COMPONENT_SCAN} 并存。 */
    public static final String COMPONENT_SCAN = "net.lab1024.sa";

    /**
     * SCM 业务域新包扫描根。
     *
     * <p>Q1 只迁 SCM 业务域，底座不动，因此迁包期间必须双根 ——
     * 只改 {@code @ComponentScan} 会让迁过去的 Mapper 静默注不进，
     * 且失败形态是启动期「找不到 Bean」，与包名迁移无表面关联。
     *
     * <p>{@code @ComponentScan} / {@code @MapperScan} 的属性是 {@code String[]}，
     * 但注解里<b>不能直接引用数组常量</b>（单个元素位置要求 {@code String}，会编译失败），
     * 必须像上面那样写成两个独立常量。数据权限的 {@code Reflections} 扫描
     * （{@code DataScopeSqlConfigService}）也需要两者，用
     * {@link #COMPONENT_SCAN_PATHS} 取合并结果，避免这里再成为第二处硬编码。
     *
     * <p>旧 SCM 包归零后，把 {@link #COMPONENT_SCAN} 收缩为 {@code com.xsy}
     * 并删除本常量与 {@link #COMPONENT_SCAN_PATHS}。
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
