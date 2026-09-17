package com.xsy.scm.admin.module.business.purchase.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.purchase.domain.entity.InquiryEntity;
import com.xsy.scm.admin.module.business.purchase.domain.form.InquiryQueryForm;
import com.xsy.scm.admin.module.business.purchase.domain.vo.InquiryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 询价单 Dao
 *
 * @author xsy-scm
 */
@Mapper
public interface InquiryDao extends BaseMapper<InquiryEntity> {

    /**
     * 分页查询询价单
     */
    List<InquiryVO> queryPage(Page page, @Param("queryForm") InquiryQueryForm queryForm);

    /**
     * 批量更新删除状态
     */
    void batchUpdateDeleted(@Param("idList") List<Long> idList,
                            @Param("deletedFlag") Boolean deletedFlag);
}
