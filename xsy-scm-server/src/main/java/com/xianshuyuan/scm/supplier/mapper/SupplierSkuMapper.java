package com.xianshuyuan.scm.supplier.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xianshuyuan.scm.supplier.entity.SupplierSkuEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SupplierSkuMapper extends BaseMapper<SupplierSkuEntity> {
    List<SupplierSkuEntity> selectActiveBySupplierId(@Param("supplierId") long supplierId);

    List<SupplierSkuEntity> selectActiveBySupplierIdForUpdate(@Param("supplierId") long supplierId);

    List<SupplierSkuEntity> selectEnabledBySkuId(@Param("skuId") long skuId);

    int softDeleteOwnedWithVersion(@Param("supplierId") long supplierId,
                                   @Param("id") long id,
                                   @Param("version") int version);
}
