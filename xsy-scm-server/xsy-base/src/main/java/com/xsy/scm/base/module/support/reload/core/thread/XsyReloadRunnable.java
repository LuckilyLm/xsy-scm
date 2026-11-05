package com.xsy.scm.base.module.support.reload.core.thread;

import lombok.extern.slf4j.Slf4j;
import com.xsy.scm.base.module.support.reload.core.AbstractXsyReloadCommand;
import com.xsy.scm.base.module.support.reload.core.domain.XsyReloadItem;
import com.xsy.scm.base.module.support.reload.core.domain.XsyReloadObject;
import com.xsy.scm.base.module.support.reload.core.domain.XsyReloadResult;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * reload 线程
 *
 */
@Slf4j
public class XsyReloadRunnable implements Runnable {

    private AbstractXsyReloadCommand abstractXsyReloadCommand;

    private boolean isInit = false;

    public XsyReloadRunnable(AbstractXsyReloadCommand abstractXsyReloadCommand) {
        this.abstractXsyReloadCommand = abstractXsyReloadCommand;
    }

    @Override
    public void run() {
        try {
            this.doTask();
        } catch (Throwable e) {
            log.error("", e);
        }
    }

    /**
     * 检测Identifier变化，执行reload
     */
    private void doTask() {
        if (!isInit) {
            this.abstractXsyReloadCommand.init();
            isInit = true;
            return;
        }

        List<XsyReloadItem> xsyReloadItemList = this.abstractXsyReloadCommand.readReloadItem();
        ConcurrentHashMap<String, String> tagIdentifierMap = this.abstractXsyReloadCommand.getTagIdentifierMap();
        for (XsyReloadItem xsyReloadItem : xsyReloadItemList) {
            String tag = xsyReloadItem.getTag();
            String tagIdentifier = xsyReloadItem.getIdentification();
            String preTagChangeIdentifier = tagIdentifierMap.get(tag);
            // 数据不一致
            if (preTagChangeIdentifier == null || !preTagChangeIdentifier.equals(tagIdentifier)) {
                this.abstractXsyReloadCommand.putIdentifierMap(tag, tagIdentifier);
                // 执行重新加载此项的动作
                XsyReloadResult xsyReloadResult = this.doReload(xsyReloadItem);
                this.abstractXsyReloadCommand.handleReloadResult(xsyReloadResult);
            }
        }
    }

    /**
     * 方法调用
     *
     * @param xsyReloadItem
     * @return
     */
    private XsyReloadResult doReload(XsyReloadItem xsyReloadItem) {
        XsyReloadResult result = new XsyReloadResult();
        XsyReloadObject xsyReloadObject = this.abstractXsyReloadCommand.reloadObject(xsyReloadItem.getTag());
        try {
            if (xsyReloadObject == null) {
                result.setResult(false);
                result.setException("不能从系统中找到对应的tag：" + xsyReloadItem.getTag());
                return result;
            }

            Method method = xsyReloadObject.getMethod();
            if (method == null) {
                result.setResult(false);
                result.setException("reload方法不存在");
                return result;
            }

            result.setTag(xsyReloadItem.getTag());
            result.setArgs(xsyReloadItem.getArgs());
            result.setIdentification(xsyReloadItem.getIdentification());
            result.setResult(true);
            int paramCount = method.getParameterCount();
            if (paramCount > 1) {
                result.setResult(false);
                result.setException("reload方法" + method.getName() + "参数太多");
                return result;
            }

            if (paramCount == 0) {
                method.invoke(xsyReloadObject.getReloadObject());
            } else {
                method.invoke(xsyReloadObject.getReloadObject(), xsyReloadItem.getArgs());
            }
        } catch (Throwable throwable) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            throwable.printStackTrace(pw);

            result.setResult(false);
            result.setException(throwable.toString());
        }
        return result;
    }

}
