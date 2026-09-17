package com.xsy.scm.admin.module.business.finance.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.finance.domain.entity.ReceivableEntity;
import com.xsy.scm.admin.module.business.finance.domain.form.ReceivableQueryForm;
import com.xsy.scm.admin.module.business.finance.domain.vo.ReceivableVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 应收单 Dao
 *
 * @author xsy-scm
 */
@Mapper
public interface ReceivableDao extends BaseMapper<ReceivableEntity> {

    /**
     * 分页查询应收单
     */
    List<ReceivableVO> queryPage(Page page, @Param("queryForm") ReceivableQueryForm queryForm);
}
