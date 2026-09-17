package com.xsy.scm.admin.module.business.customer.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.customer.domain.entity.CustomerGoodsVisibleEntity;
import com.xsy.scm.admin.module.business.customer.domain.form.CustomerGoodsVisibleQueryForm;
import com.xsy.scm.admin.module.business.customer.domain.vo.CustomerGoodsVisibleVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 客户商品可见性 Dao
 *
 * @author xsy-scm
 */
@Mapper
public interface CustomerGoodsVisibleDao extends BaseMapper<CustomerGoodsVisibleEntity> {

    /**
     * 分页查询客户商品可见性
     */
    List<CustomerGoodsVisibleVO> queryPage(Page page, @Param("queryForm") CustomerGoodsVisibleQueryForm queryForm);

    /**
     * 批量更新删除状态
     */
    void batchUpdateDeleted(@Param("idList") List<Long> idList,
                            @Param("deletedFlag") Boolean deletedFlag);
}
