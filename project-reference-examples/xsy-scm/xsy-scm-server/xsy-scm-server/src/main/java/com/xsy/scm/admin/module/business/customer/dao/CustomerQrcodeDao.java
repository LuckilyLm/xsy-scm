package com.xsy.scm.admin.module.business.customer.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.customer.domain.entity.CustomerQrcodeEntity;
import com.xsy.scm.admin.module.business.customer.domain.form.CustomerQrcodeQueryForm;
import com.xsy.scm.admin.module.business.customer.domain.vo.CustomerQrcodeVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 业务员推广二维码 Dao
 *
 * @author xsy-scm
 */
@Mapper
public interface CustomerQrcodeDao extends BaseMapper<CustomerQrcodeEntity> {

    /**
     * 分页查询推广二维码
     */
    List<CustomerQrcodeVO> queryPage(Page page, @Param("queryForm") CustomerQrcodeQueryForm queryForm);

    /**
     * 批量更新删除状态
     */
    void batchUpdateDeleted(@Param("qrcodeIdList") List<Long> qrcodeIdList,
                            @Param("deletedFlag") Boolean deletedFlag);
}
