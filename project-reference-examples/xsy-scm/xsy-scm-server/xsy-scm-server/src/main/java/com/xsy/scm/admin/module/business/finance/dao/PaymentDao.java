package com.xsy.scm.admin.module.business.finance.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.finance.domain.entity.PaymentEntity;
import com.xsy.scm.admin.module.business.finance.domain.form.PaymentQueryForm;
import com.xsy.scm.admin.module.business.finance.domain.vo.PaymentVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 收款单 Dao
 *
 * @author xsy-scm
 */
@Mapper
public interface PaymentDao extends BaseMapper<PaymentEntity> {

    /**
     * 分页查询收款单
     */
    List<PaymentVO> queryPage(Page page, @Param("queryForm") PaymentQueryForm queryForm);
}
