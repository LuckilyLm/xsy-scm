package net.lab1024.sa.admin.module.scm.pricing.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.time.OffsetDateTime;

import net.lab1024.sa.admin.module.scm.pricing.domain.entity.AgreementPriceEntity;
import net.lab1024.sa.admin.module.scm.pricing.domain.form.AgreementPriceQueryForm;
import net.lab1024.sa.admin.module.scm.pricing.domain.vo.AgreementPriceVO;

@Mapper
public interface AgreementPriceDao extends BaseMapper<AgreementPriceEntity> {
    List<AgreementPriceVO> queryPage(Page<?> page, @Param("query") AgreementPriceQueryForm query);

    AgreementPriceVO detail(@Param("id") Long id);

    Long lockParent(@Param("id") Long id);

    long countOverlapping(@Param("dimension") Long dimension, @Param("skuId") Long skuId, @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to, @Param("excludeId") Long excludeId);

    List<AgreementPriceEntity> selectEffective(@Param("dimension") Long dimension, @Param("skuIds") List<Long> skuIds, @Param("at") OffsetDateTime at);

    int softDelete(@Param("id") Long id, @Param("version") Integer version, @Param("operator") String operator);

    void log(@Param("id") Long id, @Param("operation") String operation, @Param("operator") String operator, @Param("before") String before, @Param("after") String after);
}
