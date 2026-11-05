package com.xsy.scm.base.module.support.reload.core;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import com.xsy.scm.base.module.support.reload.core.annoation.XsyReload;
import com.xsy.scm.base.module.support.reload.core.domain.XsyReloadObject;
import com.xsy.scm.base.module.support.reload.core.thread.XsyReloadRunnable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * XsyReloadManager 管理器
 * <p>
 * 可以在此类中添加 检测任务 以及注册 处理程序
 *
 */
@Slf4j
@Service
public class XsyReloadManager implements BeanPostProcessor {

    private static final String THREAD_NAME_PREFIX = "xsy-reload";
    private static final int THREAD_COUNT = 1;

    @Value("${reload.interval-seconds}")
    private Integer intervalSeconds;

    @Resource
    private AbstractXsyReloadCommand reloadCommand;

    private final Map<String, XsyReloadObject> reloadObjectMap = new ConcurrentHashMap<>();

    private ScheduledThreadPoolExecutor threadPoolExecutor;

    @PostConstruct
    public void init() {
        if (threadPoolExecutor != null) {
            return;
        }

        this.threadPoolExecutor = new ScheduledThreadPoolExecutor(THREAD_COUNT, r -> {
            Thread t = new Thread(r, THREAD_NAME_PREFIX);
            if (!t.isDaemon()) {
                t.setDaemon(true);
            }
            return t;
        });
        this.threadPoolExecutor.scheduleWithFixedDelay(new XsyReloadRunnable(this.reloadCommand), 10,
            this.intervalSeconds, TimeUnit.SECONDS);
        this.reloadCommand.setReloadManager(this);
    }

    @PreDestroy
    public void shutdown() {
        if (this.threadPoolExecutor != null) {
            this.threadPoolExecutor.shutdownNow();
            this.threadPoolExecutor = null;
        }
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Method[] methods = ReflectionUtils.getAllDeclaredMethods(bean.getClass());
        for (Method method : methods) {
            XsyReload xsyReload = method.getAnnotation(XsyReload.class);
            if (xsyReload == null) {
                continue;
            }
            int paramCount = method.getParameterCount();
            if (paramCount > 1) {
                log.error("<<XsyReloadManager>> register tag reload : " + xsyReload.value()
                    + " , param count cannot greater than one !");
                continue;
            }
            String reloadTag = xsyReload.value();
            this.register(reloadTag, new XsyReloadObject(bean, method));
        }
        return bean;
    }

    /**
     * 注册reload
     *
     * @param tag
     * @param xsyReloadObject
     */
    private void register(String tag, XsyReloadObject xsyReloadObject) {
        if (reloadObjectMap.containsKey(tag)) {
            log.error("<<XsyReloadManager>> register duplicated tag reload : " + tag + " , and it will be cover!");
        }
        reloadObjectMap.put(tag, xsyReloadObject);
    }

    /**
     * 获取重载对象
     *
     * @return
     */
    public Map<String, XsyReloadObject> reloadObjectMap() {
        return this.reloadObjectMap;
    }

}
