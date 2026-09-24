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

/**
 * 附件与业务对象的绑定入口。
 *
 * <p>「绑定」是授权动作而不是元数据动作：私有附件的读取权由这里的行决定，
 * 因此每条写附件列的业务链路都必须在**同一事务内**调用它，
 * 否则表单保存成功而附件永远不可读。
 *
 * <p>解绑只软删除关系行，**不删物理文件** —— 与 SCM 既有「不静默删数据」一致。
 */
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
     * 把关系行同步成对象**当前**引用的这一组 key：缺的补上、多的收回。
     *
     * <p>这里必须同时做两件事，只做清理会让「新建带附件的对象」永远没有授权行 ——
     * 私有附件因此只有上传者和管理员能读，其他有权查看该对象的员工一律 30005，
     * 而 FA-2 的口径恰恰是「能看对象即可看其附件」。各写链路都用本方法而不是 {@link #bind}，
     * 是因为更新可以删掉附件，只增不减会让已删附件长期可读。
     *
     * @param keepFileKeys 对象现在引用的 key；为空表示该对象已不含附件
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
