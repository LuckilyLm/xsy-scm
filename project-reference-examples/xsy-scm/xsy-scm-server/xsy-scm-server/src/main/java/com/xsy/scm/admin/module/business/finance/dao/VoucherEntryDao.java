package com.xsy.scm.admin.module.business.finance.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.admin.module.business.finance.domain.entity.VoucherEntryEntity;
import com.xsy.scm.admin.module.business.finance.domain.vo.VoucherEntryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 凭证分录 Dao
 *
 * @author xsy-scm
 */
@Mapper
public interface VoucherEntryDao extends BaseMapper<VoucherEntryEntity> {

    /**
     * 查询凭证下的分录
     */
    List<VoucherEntryVO> listByVoucherId(@Param("voucherId") Long voucherId);

    /**
     * 逻辑删除凭证下的全部分录
     */
    void batchUpdateDeletedByVoucherId(@Param("voucherId") Long voucherId);
}
