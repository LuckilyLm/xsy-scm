package com.xsy.scm.admin.module.business.purchase.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.admin.module.business.purchase.domain.entity.InquiryItemEntity;
import com.xsy.scm.admin.module.business.purchase.domain.vo.InquiryItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 询价明细 Dao
 *
 * @author xsy-scm
 */
@Mapper
public interface InquiryItemDao extends BaseMapper<InquiryItemEntity> {

    /**
     * 查询询价单下的明细
     */
    List<InquiryItemVO> listByInquiryId(@Param("inquiryId") Long inquiryId);

    /**
     * 逻辑删除询价单下的全部明细
     */
    void batchUpdateDeletedByInquiryId(@Param("inquiryId") Long inquiryId);
}
