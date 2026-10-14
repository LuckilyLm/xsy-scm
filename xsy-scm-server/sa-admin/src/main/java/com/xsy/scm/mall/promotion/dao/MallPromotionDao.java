package com.xsy.scm.mall.promotion.dao;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.mall.promotion.domain.vo.MallClaimableCouponVO;
import com.xsy.scm.mall.promotion.domain.vo.MallCouponInstanceVO;
import com.xsy.scm.mall.promotion.domain.vo.MallPromotionActivityVO;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 商城营销只读投影：活动、可领券模板与当前客户的券。
 *
 * <p>
 * 商城自有的只读 DAO（与 {@code MallFundsSourceDao} 同一模式）：客户券一律按登录客户 id 收窄； 写操作（领券、占用、核销）不经过这里，全部走营销域正式服务。
 */
@Mapper
public interface MallPromotionDao {

    List<MallPromotionActivityVO> listActiveActivities(@Param("at") OffsetDateTime at);

    List<MallClaimableCouponVO> listClaimableCoupons(Page<?> page, @Param("at") OffsetDateTime at,
            @Param("customerId") Long customerId, @Param("claimSource") String claimSource);

    List<MallCouponInstanceVO> listMyInstances(Page<?> page, @Param("customerId") Long customerId,
            @Param("at") OffsetDateTime at, @Param("tab") String tab);
}
