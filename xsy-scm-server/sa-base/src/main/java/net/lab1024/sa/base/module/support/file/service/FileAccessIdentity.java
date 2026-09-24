package net.lab1024.sa.base.module.support.file.service;

import net.lab1024.sa.base.common.domain.RequestUser;
import net.lab1024.sa.base.module.support.file.constant.FileRelationBizTypeEnum;

/**
 * Application identity bridge; the base module does not depend on admin employee classes.
 */
public interface FileAccessIdentity {
    boolean canReadAllFiles(RequestUser user);

    /**
     * 调用者是否有权读这个业务对象 —— 决定他能否读该对象绑定的私有附件。
     *
     * <p>规则由各业务域自己回答（sa-admin 侧实现），底座不猜：
     * 返回 {@code false} 只是「不通过关系放行」，上传者本人和管理员仍可读（守卫另有判定）。
     */
    default boolean canReadBizObject(FileRelationBizTypeEnum bizType, Long bizId, RequestUser user) {
        return false;
    }
}
