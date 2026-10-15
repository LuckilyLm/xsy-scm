package net.lab1024.sa.base.module.support.file.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.module.support.file.constant.FileRelationBizTypeEnum;
import net.lab1024.sa.base.module.support.file.dao.FileRelationDao;
import net.lab1024.sa.base.module.support.file.domain.entity.FileRelationEntity;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/** 私有附件读取权限由对象关系决定。绑定与业务保存处于同一事务；解绑仅撤销关系，不立即删除物理文件。 */
@Service
@RequiredArgsConstructor
public class FileRelationService {

    private final FileRelationDao fileRelationDao;

    /**
     * 幂等绑定：已存在的 (key, 类型, 对象) 不报错也不产生第二行。
     */
    @Transactional(rollbackFor = Exception.class)
    public void bind(FileRelationBizTypeEnum bizType, Long bizId, Collection<String> fileKeys) {
        if (bizId == null || CollectionUtils.isEmpty(fileKeys)) {
            return;
        }
        List<FileRelationEntity> rows = rows(bizType, bizId, fileKeys);
        if (!rows.isEmpty()) {
            fileRelationDao.insertIgnoreBatch(rows);
        }
    }

    /**
     * 同步对象当前引用的附件：新增文件建立授权，移除文件撤销授权。
     * 不删除物理文件；绑定变更与业务对象保存处于同一事务。
     *
     * @param keepFileKeys 仍被引用的文件键；为空表示全部解绑
     */
    @Transactional(rollbackFor = Exception.class)
    public void rebind(FileRelationBizTypeEnum bizType, Long bizId, Collection<String> keepFileKeys) {
        if (bizId == null) {
            return;
        }
        bind(bizType, bizId, keepFileKeys);
        fileRelationDao.softDeleteExcluding(bizType.name(), bizId,
                keepFileKeys == null ? List.of() : keepFileKeys.stream().filter(k -> k != null && !k.isBlank()).toList());
    }

    private static List<FileRelationEntity> rows(FileRelationBizTypeEnum bizType, Long bizId,
                                                 Collection<String> fileKeys) {
        return fileKeys.stream()
                .filter(key -> key != null && !key.isBlank())
                .distinct()
                .map(key -> {
                    FileRelationEntity row = new FileRelationEntity();
                    row.setFileKey(key);
                    row.setBizType(bizType.name());
                    row.setBizId(bizId);
                    return row;
                })
                .toList();
    }

    /**
     * 拆分业务附件列的存储形态（逗号分隔的多 fileKey，与 {@code FileKeyVoSerializer} 读写一致）。
     *
     * <p>口径只在这里定义一次：各写路径自行 split 会 trim 出不同的 key，
     * 换绑时会把本应保留的关系行判成「不再引用」而静默收回读取权。
     *
     * @return 去空白、去重后的 key 列表；入参为 null 或空白时返回空列表
     */
    public static List<String> splitKeys(String commaSeparated) {
        if (commaSeparated == null || commaSeparated.isBlank()) {
            return List.of();
        }
        return Arrays.stream(commaSeparated.split(",")).map(String::trim).filter(key -> !key.isEmpty()).distinct().toList();
    }
}
