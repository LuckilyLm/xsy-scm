package com.xsy.scm.promotion.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.promotion.domain.entity.PromotionCouponEntity;
import com.xsy.scm.promotion.domain.form.PromotionCouponQueryForm;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PromotionCouponDao extends BaseMapper<PromotionCouponEntity> {

    List<PromotionCouponEntity> queryPage(Page<?> page, @Param("query") PromotionCouponQueryForm query);

    /**
     * 取当前生效中的券模板（状态 ACTIVE 且落在有效窗口内）。
     */
    List<PromotionCouponEntity> listActive(@Param("at") OffsetDateTime at);

    PromotionCouponEntity lockById(@Param("id") Long id);
}
