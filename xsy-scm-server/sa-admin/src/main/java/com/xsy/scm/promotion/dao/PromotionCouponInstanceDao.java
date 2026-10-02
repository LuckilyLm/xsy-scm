package com.xsy.scm.promotion.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.promotion.domain.entity.PromotionCouponInstanceEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 客户券实例读写。
 *
 * <p>
 * 状态流转全部走手写 SQL 且带 {@code status} 前置条件：并发下同一张券只能被占用一次，
 * 第二次影响 0 行，调用方据此回答「券已被占用」而不是覆盖别人的占用。
 */
@Mapper
public interface PromotionCouponInstanceDao extends BaseMapper<PromotionCouponInstanceEntity> {

    /**
     * 取某张客户券并加锁（占用 / 核销 / 释放用）。
     *
     * <p>
     * 刻意<b>没有</b>「自动挑一张可用券」的方法：用哪张券是客户的权益，
     * 由调用方显式给出券实例 id，服务端只负责校验它可用且属于该客户。
     */
    PromotionCouponInstanceEntity lockById(@Param("id") Long id);

    List<PromotionCouponInstanceEntity> listByCustomer(@Param("customerId") Long customerId,
            @Param("status") String status, @Param("limit") int limit);

    int markReserved(@Param("id") Long id, @Param("version") Integer version, @Param("orderId") Long orderId,
            @Param("operator") String operator);

    int markUsed(@Param("id") Long id, @Param("version") Integer version, @Param("orderId") Long orderId,
            @Param("operator") String operator);

    int markReleased(@Param("id") Long id, @Param("version") Integer version, @Param("reason") String reason,
            @Param("operator") String operator);

    int insertBatch(@Param("rows") List<PromotionCouponInstanceEntity> rows);
}
