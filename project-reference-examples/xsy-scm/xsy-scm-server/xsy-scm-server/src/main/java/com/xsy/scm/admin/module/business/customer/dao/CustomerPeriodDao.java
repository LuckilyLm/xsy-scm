package com.xsy.scm.admin.module.business.customer.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.customer.domain.entity.CustomerPeriodEntity;
import com.xsy.scm.admin.module.business.customer.domain.form.CustomerPeriodQueryForm;
import com.xsy.scm.admin.module.business.customer.domain.vo.CustomerPeriodVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 客户账期 Dao
 *
 * @author xsy-scm
 */
@Mapper
public interface CustomerPeriodDao extends BaseMapper<CustomerPeriodEntity> {

    /**
     * 分页查询客户账期
     */
    List<CustomerPeriodVO> queryPage(Page page, @Param("queryForm") CustomerPeriodQueryForm queryForm);

    /**
     * 批量更新删除状态
     */
    void batchUpdateDeleted(@Param("periodIdList") List<Long> periodIdList,
                            @Param("deletedFlag") Boolean deletedFlag);

    /**
     * 查询客户生效的「时间型」账期（用于计算应收到期日）
     */
    CustomerPeriodEntity queryEffectiveTimePeriod(@Param("customerId") Long customerId);
}
