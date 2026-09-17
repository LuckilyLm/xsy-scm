package com.xsy.scm.admin.module.business.screen.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.screen.domain.entity.ScreenConfigEntity;
import com.xsy.scm.admin.module.business.screen.domain.form.ScreenConfigQueryForm;
import com.xsy.scm.admin.module.business.screen.domain.vo.ScreenConfigVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 数据大屏配置 Dao
 *
 * @author xsy-scm
 */
@Mapper
public interface ScreenConfigDao extends BaseMapper<ScreenConfigEntity> {

    /**
     * 分页查询大屏配置
     */
    List<ScreenConfigVO> queryPage(Page page, @Param("queryForm") ScreenConfigQueryForm queryForm);

    /**
     * 批量更新删除状态
     */
    void batchUpdateDeleted(@Param("idList") List<Long> idList,
                            @Param("deletedFlag") Boolean deletedFlag);
}
