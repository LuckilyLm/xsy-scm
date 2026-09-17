package com.xsy.scm.admin.module.business.finance.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.finance.domain.entity.ExternalConfigEntity;
import com.xsy.scm.admin.module.business.finance.domain.form.ExternalConfigQueryForm;
import com.xsy.scm.admin.module.business.finance.domain.vo.ExternalConfigVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 外部系统配置 Dao
 *
 * @author xsy-scm
 */
@Mapper
public interface ExternalConfigDao extends BaseMapper<ExternalConfigEntity> {

    /**
     * 分页查询外部系统配置
     */
    List<ExternalConfigVO> queryPage(Page page, @Param("queryForm") ExternalConfigQueryForm queryForm);

    /**
     * 按系统类型查询启用中的配置
     */
    ExternalConfigEntity getBySystemType(@Param("systemType") Integer systemType);

    /**
     * 批量更新删除状态
     */
    void batchUpdateDeleted(@Param("idList") List<Long> idList,
                            @Param("deletedFlag") Boolean deletedFlag);
}
