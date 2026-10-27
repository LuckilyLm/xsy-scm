package com.xsy.scm.purchase.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.purchase.domain.entity.PurchasePriceAdjustEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 采购价变更审计只追加：不提供 update / delete。
 */
@Mapper
public interface PurchasePriceAdjustDao extends BaseMapper<PurchasePriceAdjustEntity> {
}
