package com.xsy.scm.admin.module.business.purchase.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.admin.module.business.purchase.domain.entity.InquiryQuoteEntity;
import com.xsy.scm.admin.module.business.purchase.domain.vo.InquiryQuoteVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 供应商报价 Dao
 *
 * @author xsy-scm
 */
@Mapper
public interface InquiryQuoteDao extends BaseMapper<InquiryQuoteEntity> {

    /**
     * 查询询价单下的全部报价
     */
    List<InquiryQuoteVO> listByInquiryId(@Param("inquiryId") Long inquiryId);

    /**
     * 按明细 + 供应商查询已有报价（用于幂等更新）
     */
    InquiryQuoteEntity getByItemAndSupplier(@Param("itemId") Long itemId,
                                            @Param("supplierId") Long supplierId);
}
