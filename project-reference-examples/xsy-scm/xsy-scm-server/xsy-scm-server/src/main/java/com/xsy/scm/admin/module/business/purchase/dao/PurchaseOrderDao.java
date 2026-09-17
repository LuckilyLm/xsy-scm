package com.xsy.scm.admin.module.business.purchase.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.purchase.domain.entity.PurchaseOrderEntity;
import com.xsy.scm.admin.module.business.purchase.domain.form.PurchaseOrderQueryForm;
import com.xsy.scm.admin.module.business.purchase.domain.vo.PurchaseOrderVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 采购单 Dao
 *
 * @author xsy-scm
 */
@Mapper
public interface PurchaseOrderDao extends BaseMapper<PurchaseOrderEntity> {

    /**
     * 分页查询采购单
     */
    List<PurchaseOrderVO> queryPage(Page page, @Param("queryForm") PurchaseOrderQueryForm queryForm);

    /**
     * 批量更新删除状态
     *
     * <p>采购单业务上应走「取消」状态，本方法仅供异常数据清理使用。</p>
     */
    void batchUpdateDeleted(@Param("purchaseIdList") List<Long> purchaseIdList,
                            @Param("deletedFlag") Boolean deletedFlag);
}
