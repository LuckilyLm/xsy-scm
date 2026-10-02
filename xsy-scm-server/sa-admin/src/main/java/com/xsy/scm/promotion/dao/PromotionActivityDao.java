package com.xsy.scm.promotion.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.promotion.domain.entity.PromotionActivityEntity;
import com.xsy.scm.promotion.domain.form.PromotionActivityQueryForm;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PromotionActivityDao extends BaseMapper<PromotionActivityEntity> {

    List<PromotionActivityEntity> queryPage(Page<?> page, @Param("query") PromotionActivityQueryForm query);

    /**
     * 取当前生效中的活动（状态 ACTIVE 且落在有效窗口内）。
     *
     * <p>
     * 只按时间窗口与状态取，**不在这里判互斥组**：互斥是「同一单里选哪几条」的决策，
     * 属于计算逻辑，塞进 SQL 会让同一条规则有两个实现。
     */
    List<PromotionActivityEntity> listActive(@Param("at") OffsetDateTime at);

    PromotionActivityEntity lockById(@Param("id") Long id);

    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("version") Integer version,
            @Param("operator") String operator);
}
