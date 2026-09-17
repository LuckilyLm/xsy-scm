package com.xsy.scm.admin.module.business.customer.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.customer.domain.entity.CustomerProductAliasEntity;
import com.xsy.scm.admin.module.business.customer.domain.form.CustomerProductAliasQueryForm;
import com.xsy.scm.admin.module.business.customer.domain.vo.CustomerProductAliasVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 客户商品别名 Dao
 *
 * @author xsy-scm
 */
@Mapper
public interface CustomerProductAliasDao extends BaseMapper<CustomerProductAliasEntity> {

    /**
     * 分页查询客户商品别名
     */
    List<CustomerProductAliasVO> queryPage(Page page, @Param("queryForm") CustomerProductAliasQueryForm queryForm);

    /**
     * 按客户 + 商品 + 规格查询（用于唯一性校验）
     */
    CustomerProductAliasEntity getByCustomerProductSku(@Param("customerId") Long customerId,
                                                       @Param("productId") Long productId,
                                                       @Param("skuId") Long skuId);

    /**
     * 批量更新删除状态
     */
    void batchUpdateDeleted(@Param("idList") List<Long> idList,
                            @Param("deletedFlag") Boolean deletedFlag);
}
