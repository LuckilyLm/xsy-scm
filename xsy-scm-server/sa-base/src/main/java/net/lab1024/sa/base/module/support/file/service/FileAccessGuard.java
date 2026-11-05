package net.lab1024.sa.base.module.support.file.service;

import net.lab1024.sa.base.common.domain.RequestUser;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.module.support.file.constant.FileFolderTypeEnum;
import net.lab1024.sa.base.module.support.file.constant.FileRelationBizTypeEnum;
import net.lab1024.sa.base.module.support.file.dao.FileDao;
import net.lab1024.sa.base.module.support.file.dao.FileRelationDao;
import net.lab1024.sa.base.module.support.file.domain.entity.FileRelationEntity;
import net.lab1024.sa.base.module.support.file.domain.vo.FileVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 文件读取判定：{@code public/} 前缀对所有调用者开放（包括未登录请求）；
 * 存量 {@code private/} 文件仍只允许管理员、拥有业务对象查看权限的人或上传者本人读取。
 *
 * <p>2026-11-02 起新上传全部落在 {@code public/}，私有分支只服务改造前的存量文件与后续敏感资料。
 */
@Component
public class FileAccessGuard {

    private final FileDao fileDao;

    private final FileAccessIdentity identity;

    private final FileRelationDao relationDao;

    public FileAccessGuard(FileDao fileDao, FileAccessIdentity identity, FileRelationDao relationDao) {
        this.fileDao = fileDao;
        this.identity = identity;
        this.relationDao = relationDao;
    }

    public void checkRead(String keys, RequestUser user) {
        if (keys == null) {
            throw new AccessDenied();
        }
        List<String> keyList = List.of(keys.split(",", -1));
        Map<String, List<FileRelationEntity>> relations = loadRelations(keyList);
        // getFileUrl supports comma-separated keys. Never authorize only the first one.
        for (String key : keyList) {
            if (!canRead(key, user, relations)) {
                throw new AccessDenied();
            }
        }
    }

    /**
     * Non-throwing counterpart to {@link #checkRead}: for contexts that must not fail an entire
     * response over one unreadable key (e.g. VO fields that mix a caller's own files with
     * others'), silently drop keys the caller may not read instead of denying the whole request.
     * Applies the identical per-key policy as {@link #checkRead}, so a key that is readable
     * through one path is readable through the other and vice versa.
     */
    public List<String> filterReadable(List<String> keys, RequestUser user) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        Map<String, List<FileRelationEntity>> relations = loadRelations(keys);
        List<String> readable = new ArrayList<>(keys.size());
        for (String key : keys) {
            if (canRead(key, user, relations)) {
                readable.add(key);
            }
        }
        return readable;
    }

    /**
     * 一次批量取关系行：逐 key 查会让「20 张图的商品详情」变成 20 次查询。
     */
    private Map<String, List<FileRelationEntity>> loadRelations(List<String> keys) {
        List<String> privateKeys = keys.stream().filter(this::isManagedPrivate).toList();
        if (privateKeys.isEmpty()) {
            return Map.of();
        }
        Map<String, List<FileRelationEntity>> byKey = new HashMap<>();
        for (FileRelationEntity relation : relationDao.listActiveByFileKeys(privateKeys)) {
            byKey.computeIfAbsent(relation.getFileKey(), k -> new ArrayList<>()).add(relation);
        }
        return byKey;
    }

    /** key 是否落在存量私有目录白名单内；未知的 private/ 子目录仍然校验失败时拒绝操作。 */
    private boolean isManagedPrivate(String key) {
        return FileKeyPolicy.isValid(key) && !key.endsWith("/")
                && FileFolderTypeEnum.LEGACY_PRIVATE_FOLDERS.stream().anyMatch(key::startsWith);
    }

    private boolean canRead(String key, RequestUser user, Map<String, List<FileRelationEntity>> relations) {
        if (!FileKeyPolicy.isValid(key) || key.endsWith("/")) {
            return false;
        }
        if (key.startsWith(FileFolderTypeEnum.FOLDER_PUBLIC + "/")) {
            return true;
        }
        if (!isManagedPrivate(key) || user == null || user.getUserId() == null
                || user.getUserType() != UserTypeEnum.ADMIN_EMPLOYEE) {
            return false;
        }
        if (identity.canReadAllFiles(user)) {
            return true;
        }
        if (anyRelationReadable(relations.get(key), user)) {
            return true;
        }
        // 上传者本人可读自己上传的私有附件：关系表回答「业务对象授权」，不覆盖「我传给我的东西」。
        FileVO file = fileDao.getByFileKey(key);
        return file != null && Objects.equals(file.getCreatorId(), user.getUserId())
                && Objects.equals(file.getCreatorUserType(), user.getUserType().getValue());
    }

    /**
     * 「任一」而非「全部」：一份被多个业务对象共享的合同，只要用户对其中一个有权，
     * 就应当能读到它；要求全部有权会让共享方各自看不见。
     */
    private boolean anyRelationReadable(List<FileRelationEntity> relations, RequestUser user) {
        if (relations == null || relations.isEmpty()) {
            return false;
        }
        for (FileRelationEntity relation : relations) {
            FileRelationBizTypeEnum bizType = parseBizType(relation.getBizType());
            if (bizType != null && identity.canReadBizObject(bizType, relation.getBizId(), user)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 库里存的未知类型按「不通过关系放行」处理：写错值的行不该变成放行理由，
     * 也不该让整条响应抛错（它可能只是历史遗留）。
     */
    private FileRelationBizTypeEnum parseBizType(String value) {
        try {
            return value == null ? null : FileRelationBizTypeEnum.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static final class AccessDenied extends RuntimeException {
    }
}
