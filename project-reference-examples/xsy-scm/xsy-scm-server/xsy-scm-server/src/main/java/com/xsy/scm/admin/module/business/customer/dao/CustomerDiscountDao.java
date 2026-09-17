package com.xsy.scm.admin.module.business.customer.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.customer.domain.entity.CustomerDiscountEntity;
import com.xsy.scm.admin.module.business.customer.domain.form.CustomerDiscountQueryForm;
import com.xsy.scm.admin.module.business.customer.domain.vo.CustomerDiscountVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 客户折扣率 Dao
 *
 * @author xsy-scm
 */
@Mapper
public interface CustomerDiscountDao extends BaseMapper<CustomerDiscountEntity> {

    /**
     * 分页查询客户折扣率
     */
    List<CustomerDiscountVO> queryPage(Page page, @Param("queryForm") CustomerDiscountQueryForm queryForm);

    /**
     * 批量更新删除状态
     */
    void batchUpdateDeleted(@Param("idList") List<Long> idList,
                            @Param("deletedFlag") Boolean deletedFlag);
}
