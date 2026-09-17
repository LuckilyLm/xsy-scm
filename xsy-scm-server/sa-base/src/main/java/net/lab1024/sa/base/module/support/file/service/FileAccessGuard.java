package net.lab1024.sa.base.module.support.file.service;

import net.lab1024.sa.base.common.domain.RequestUser;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.module.support.file.dao.FileDao;
import net.lab1024.sa.base.module.support.file.domain.vo.FileVO;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** HTTP file reads only; business services retain their own permission/relation boundary. */
@Component
public class FileAccessGuard {
    private final FileDao fileDao;
    private final FileAccessIdentity identity;

    public FileAccessGuard(FileDao fileDao, FileAccessIdentity identity) {
        this.fileDao = fileDao;
        this.identity = identity;
    }

    public void checkRead(String keys, RequestUser user) {
        if (keys == null || user == null || user.getUserId() == null
                || user.getUserType() != UserTypeEnum.ADMIN_EMPLOYEE) {
            throw new AccessDenied();
        }
        // getFileUrl supports comma-separated keys. Never authorize only the first one.
        for (String key : keys.split(",", -1)) {
            if (!canRead(key, user)) {
                throw new AccessDenied();
            }
        }
    }

    private boolean canRead(String key, RequestUser user) {
        if (!FileKeyPolicy.isValid(key) || key.endsWith("/")) {
            return false;
        }
        if (key.startsWith("public/") || key.startsWith("private/notice/")
                || key.startsWith("private/help-doc/")) {
            return true;
        }
        if (!(key.startsWith("private/common/") || key.startsWith("private/feedback/"))) {
            return false;
        }
        if (identity.canReadAllFiles(user)) {
            return true;
        }
        FileVO file = fileDao.getByFileKey(key);
        return file != null && Objects.equals(file.getCreatorId(), user.getUserId())
                && Objects.equals(file.getCreatorUserType(), user.getUserType().getValue());
    }

    public static final class AccessDenied extends RuntimeException { }
}
