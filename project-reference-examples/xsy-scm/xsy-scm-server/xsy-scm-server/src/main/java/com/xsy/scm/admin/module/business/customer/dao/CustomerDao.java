package com.xsy.scm.admin.module.business.customer.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.customer.domain.entity.CustomerEntity;
import com.xsy.scm.admin.module.business.customer.domain.form.CustomerQueryForm;
import com.xsy.scm.admin.module.business.customer.domain.vo.CustomerVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 客户档案 Dao
 *
 * @author xsy-scm
 */
@Mapper
public interface CustomerDao extends BaseMapper<CustomerEntity> {

    /**
     * 分页查询客户
     */
    List<CustomerVO> queryPage(Page page, @Param("queryForm") CustomerQueryForm queryForm);

    /**
     * 批量更新删除状态
     */
    void batchUpdateDeleted(@Param("customerIdList") List<Long> customerIdList,
                            @Param("deletedFlag") Boolean deletedFlag);
}
