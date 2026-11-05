package com.xsy.scm.admin;

import com.xsy.scm.base.listener.Ip2RegionListener;
import com.xsy.scm.base.listener.LogVariableListener;
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
 * XSY 项目启动类
 *
 */
@EnableCaching
@EnableScheduling
@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)
@ComponentScan(XsyApplication.COMPONENT_SCAN)
@MapperScan(value = XsyApplication.COMPONENT_SCAN, annotationClass = Mapper.class)
@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class})
public class XsyApplication {

    /** 系统管理、公共能力与供应链业务使用同一个扫描根。 */
    public static final String COMPONENT_SCAN = "com.xsy.scm";

    /** 供按数组接收扫描路径的工具复用，避免另设扫描范围。 */
    public static final String[] COMPONENT_SCAN_PATHS = {COMPONENT_SCAN};

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(XsyApplication.class);
        // 添加 日志监听器，使 log4j2-spring.xml 可以间接读取到配置文件的属性
        application.addListeners(new LogVariableListener(), new Ip2RegionListener());
        application.run(args);
    }
}
