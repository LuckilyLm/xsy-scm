package com.xsy.scm.pricing.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.pricing.domain.entity.TimePriceEntity;
import com.xsy.scm.pricing.domain.form.TimePriceQueryForm;
import com.xsy.scm.pricing.domain.vo.TimePriceVO;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TimePriceDao extends BaseMapper<TimePriceEntity> {

    List<TimePriceVO> queryPage(Page<?> page, @Param("query") TimePriceQueryForm query);

    TimePriceVO detail(@Param("id") Long id);

    /**
     * 该规格在给定时刻生效的时价。
     *
     * <p>
     * 区间是半开：[start_at, end_at)，同一时刻最多命中一条（重叠在写入时已被拒绝）。
     */
    List<TimePriceEntity> selectEffective(@Param("skuIds") List<Long> skuIds, @Param("at") OffsetDateTime priceAt);

    /** 同一规格上与给定区间重叠的条数，排除自身。 */
    long countOverlapping(@Param("skuId") Long skuId, @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt, @Param("excludeId") Long excludeId);

    int softDelete(@Param("id") Long id, @Param("version") Integer version, @Param("operator") String operator);

    /** 变更审计：只追加，记录改动前后快照。 */
    void log(@Param("id") Long id, @Param("operation") String operationType, @Param("operator") String operator,
            @Param("before") String beforeSnapshot, @Param("after") String afterSnapshot);
}
