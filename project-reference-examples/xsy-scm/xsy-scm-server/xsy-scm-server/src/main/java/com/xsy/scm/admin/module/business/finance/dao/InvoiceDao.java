package com.xsy.scm.admin.module.business.finance.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.finance.domain.entity.InvoiceEntity;
import com.xsy.scm.admin.module.business.finance.domain.form.InvoiceQueryForm;
import com.xsy.scm.admin.module.business.finance.domain.vo.InvoiceVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 发票 Dao
 *
 * @author xsy-scm
 */
@Mapper
public interface InvoiceDao extends BaseMapper<InvoiceEntity> {

    /**
     * 分页查询发票
     */
    List<InvoiceVO> queryPage(Page page, @Param("queryForm") InvoiceQueryForm queryForm);

    /**
     * 按订单查询发票（整单红冲联动）
     */
    List<InvoiceEntity> listByOrderId(@Param("orderId") Long orderId);

    /**
     * 批量更新删除状态
     */
    void batchUpdateDeleted(@Param("idList") List<Long> idList,
                            @Param("deletedFlag") Boolean deletedFlag);
}
