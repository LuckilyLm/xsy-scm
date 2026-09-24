package net.lab1024.sa.base.module.support.file.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import net.lab1024.sa.base.module.support.file.domain.entity.FileRelationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * 文件授权关系读写。自定义 SQL 全部放 {@code mapper/support/FileRelationMapper.xml}。
 */
@Mapper
public interface FileRelationDao extends BaseMapper<FileRelationEntity> {

    /**
     * 按 key 取全部活动关系行；守卫按「任一业务对象可读即放行」判定。
     */
    List<FileRelationEntity> listActiveByFileKeys(@Param("fileKeys") Collection<String> fileKeys);

    int insertIgnoreBatch(@Param("list") List<FileRelationEntity> list);

    /**
     * 解绑：把该对象当前不再引用的 key 的关系行软删除。物理文件不删。
     */
    int softDeleteExcluding(@Param("bizType") String bizType,
                            @Param("bizId") Long bizId,
                            @Param("keepFileKeys") Collection<String> keepFileKeys);
}
