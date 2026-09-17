package com.xsy.scm.admin.module.business.supplier.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.supplier.domain.entity.SupplierAccountEntity;
import com.xsy.scm.admin.module.business.supplier.domain.form.SupplierAccountQueryForm;
import com.xsy.scm.admin.module.business.supplier.domain.vo.SupplierAccountVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 供应商账号 Dao
 *
 * @author xsy-scm
 */
@Mapper
public interface SupplierAccountDao extends BaseMapper<SupplierAccountEntity> {

    /**
     * 分页查询供应商账号
     */
    List<SupplierAccountVO> queryPage(Page page, @Param("queryForm") SupplierAccountQueryForm queryForm);

    /**
     * 按登录账号查询（用于唯一性校验）
     */
    SupplierAccountEntity getByAccount(@Param("account") String account);

    /**
     * 批量更新删除状态
     */
    void batchUpdateDeleted(@Param("idList") List<Long> idList,
                            @Param("deletedFlag") Boolean deletedFlag);
}
