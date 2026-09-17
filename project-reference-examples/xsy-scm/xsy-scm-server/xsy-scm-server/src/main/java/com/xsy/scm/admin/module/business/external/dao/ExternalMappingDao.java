package com.xsy.scm.admin.module.business.external.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.external.domain.entity.ExternalMappingEntity;
import com.xsy.scm.admin.module.business.external.domain.form.ExternalMappingQueryForm;
import com.xsy.scm.admin.module.business.external.domain.vo.ExternalMappingVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 外部平台映射 Dao
 *
 * @author xsy-scm
 */
@Mapper
public interface ExternalMappingDao extends BaseMapper<ExternalMappingEntity> {

    /**
     * 分页查询映射
     */
    List<ExternalMappingVO> queryPage(Page page, @Param("queryForm") ExternalMappingQueryForm queryForm);

    /**
     * 按条件查询全部映射（导出用，不分页）
     */
    List<ExternalMappingVO> listByCondition(@Param("queryForm") ExternalMappingQueryForm queryForm);

    /**
     * 统计同平台 + 同对象 + 同系统ID + 同外部ID 的映射数（导入去重）
     */
    Long countByMapping(@Param("systemType") Integer systemType,
                        @Param("bizType") Integer bizType,
                        @Param("localId") Long localId,
                        @Param("externalId") String externalId);

    /**
     * 批量更新删除状态
     */
    void batchUpdateDeleted(@Param("idList") List<Long> idList,
                            @Param("deletedFlag") Boolean deletedFlag);
}
