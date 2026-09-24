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
 * 私有附件的读取判定。放行只有三条路：管理员、业务对象授权（关系表）、上传者本人。
 *
 * <p>目录前缀只用来判「公开」和「这个 key 属于哪个受管目录」，不再单独构成放行理由：
 * {@code private/notice/}、{@code private/help-doc/} 以前的前缀级放行等于
 * 「同前缀下所有人的附件互看」，而前缀表达不了「哪一张单据」，所以 FA-2 起
 * 必须由 {@code t_file_relation} 回答（见 docs/decisions.md「P0 基线收口裁决」第 12–13 条）。
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
        if (keys == null || user == null || user.getUserId() == null
                || user.getUserType() != UserTypeEnum.ADMIN_EMPLOYEE) {
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
        if (keys == null || keys.isEmpty() || user == null || user.getUserId() == null
                || user.getUserType() != UserTypeEnum.ADMIN_EMPLOYEE) {
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

    /** key 是否落在上传白名单声明的私有目录下；不在白名单里的前缀一律不放行。 */
    private boolean isManagedPrivate(String key) {
        return FileKeyPolicy.isValid(key) && !key.endsWith("/")
                && folderOf(key) != null && folderOf(key).startsWith(FileFolderTypeEnum.FOLDER_PRIVATE);
    }

    private String folderOf(String key) {
        for (FileFolderTypeEnum folder : FileFolderTypeEnum.values()) {
            if (key.startsWith(folder.getFolder())) {
                return folder.getFolder();
            }
        }
        return null;
    }

    private boolean canRead(String key, RequestUser user, Map<String, List<FileRelationEntity>> relations) {
        if (!FileKeyPolicy.isValid(key) || key.endsWith("/")) {
            return false;
        }
        if (key.startsWith(FileFolderTypeEnum.FOLDER_PUBLIC + "/")) {
            return true;
        }
        if (!isManagedPrivate(key)) {
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
