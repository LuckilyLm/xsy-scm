package net.lab1024.sa.base.module.support.file.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import net.lab1024.sa.base.module.support.file.domain.vo.FileVO;
import net.lab1024.sa.base.module.support.file.domain.entity.FileEntity;
import net.lab1024.sa.base.module.support.file.domain.form.FileQueryForm;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * 文件服务
 *
 */
@Mapper
public interface FileDao extends BaseMapper<FileEntity> {

    /**
     * 文件key单个查询
     *
     * @param fileKey
     * @return
     */
    FileVO getByFileKey(@Param("fileKey") String fileKey);


    /**
     * 批量获取
     */
    List<FileVO> selectByFileKeyList(@Param("fileKeyList") Collection<String> fileKeyList);

    /**
     * 分页 查询
     *
     * @param page
     * @param queryForm
     * @return
     */
    List<FileVO> queryPage(Page page, @Param("queryForm") FileQueryForm queryForm);

    /**
     * 某用户上传后仍未绑定任何业务对象的暂存件数量（用于「每用户最多 100 个」的上限判断）。
     */
    int countUnboundScratchByCreator(@Param("creatorId") Long creatorId,
                                     @Param("creatorUserType") Integer creatorUserType);

    /**
     * 可回收暂存件候选：超过保留窗口且**没有任何活动关系行**。
     *
     * <p>这里只判关系行；「是否仍被业务表直接引用」由回收任务再确认一次 ——
     * 两条件都在，才允许物理删除。
     */
    List<FileVO> listScratchCandidates(@Param("retentionDays") int retentionDays,
                                       @Param("limit") int limit);

}
