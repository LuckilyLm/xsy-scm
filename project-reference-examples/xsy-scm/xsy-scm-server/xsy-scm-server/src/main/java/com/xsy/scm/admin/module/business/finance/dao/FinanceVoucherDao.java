package com.xsy.scm.admin.module.business.finance.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.finance.domain.entity.FinanceVoucherEntity;
import com.xsy.scm.admin.module.business.finance.domain.form.VoucherQueryForm;
import com.xsy.scm.admin.module.business.finance.domain.vo.FinanceVoucherVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 会计凭证 Dao
 *
 * @author xsy-scm
 */
@Mapper
public interface FinanceVoucherDao extends BaseMapper<FinanceVoucherEntity> {

    /**
     * 分页查询会计凭证
     */
    List<FinanceVoucherVO> queryPage(Page page, @Param("queryForm") VoucherQueryForm queryForm);

    /**
     * 统计同一业务单已生成的凭证数（幂等校验）
     */
    Long countByBiz(@Param("bizType") Integer bizType, @Param("bizId") Long bizId);

    /**
     * 批量更新删除状态
     */
    void batchUpdateDeleted(@Param("idList") List<Long> idList,
                            @Param("deletedFlag") Boolean deletedFlag);
}
