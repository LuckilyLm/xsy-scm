package com.xsy.scm.admin.module.business.purchase.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.purchase.domain.entity.PurchaseItemEntity;
import com.xsy.scm.admin.module.business.purchase.domain.form.PurchaseItemQueryForm;
import com.xsy.scm.admin.module.business.purchase.domain.vo.PurchaseItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 采购明细 Dao
 *
 * @author xsy-scm
 */
@Mapper
public interface PurchaseItemDao extends BaseMapper<PurchaseItemEntity> {

    /**
     * 根据采购单查询明细
     */
    List<PurchaseItemEntity> queryByPurchaseId(@Param("purchaseId") Long purchaseId);

    /**
     * 分页查询采购明细
     */
    List<PurchaseItemVO> queryPage(Page page, @Param("queryForm") PurchaseItemQueryForm queryForm);

    /**
     * 批量更新删除状态
     */
    void batchUpdateDeleted(@Param("itemIdList") List<Long> itemIdList,
                            @Param("deletedFlag") Boolean deletedFlag);
}
