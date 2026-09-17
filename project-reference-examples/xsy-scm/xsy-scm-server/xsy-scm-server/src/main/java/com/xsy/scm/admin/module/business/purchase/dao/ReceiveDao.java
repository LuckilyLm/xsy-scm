package com.xsy.scm.admin.module.business.purchase.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.purchase.domain.entity.ReceiveEntity;
import com.xsy.scm.admin.module.business.purchase.domain.form.ReceiveQueryForm;
import com.xsy.scm.admin.module.business.purchase.domain.vo.ReceiveVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 采购收货单 Dao
 *
 * @author xsy-scm
 */
@Mapper
public interface ReceiveDao extends BaseMapper<ReceiveEntity> {

    /**
     * 根据采购明细查询收货记录（支持同一明细多次收货）
     */
    List<ReceiveEntity> queryByItemId(@Param("itemId") Long itemId);

    /**
     * 分页查询收货单
     */
    List<ReceiveVO> queryPage(Page page, @Param("queryForm") ReceiveQueryForm queryForm);

    /**
     * 批量更新删除状态
     */
    void batchUpdateDeleted(@Param("receiveIdList") List<Long> receiveIdList,
                            @Param("deletedFlag") Boolean deletedFlag);
}
