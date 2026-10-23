package com.xsy.scm.inventory.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.inventory.domain.entity.InventoryConversionEntity;
import com.xsy.scm.inventory.domain.form.InventoryConversionQueryForm;
import com.xsy.scm.inventory.domain.vo.InventoryConversionVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 规格转换单只有待审核时允许修改、删除或审批，状态限制必须写在 SQL 条件中。审批使用版本校验，防止确认过程中明细被改动。
 */
@Mapper
public interface InventoryConversionDao extends BaseMapper<InventoryConversionEntity> {

    /**
     * 单号是否存在（软删范围内）。
     */
    int countByConversionNo(@Param("conversionNo") String conversionNo);

    /**
     * 取下一个单号序列值（PG sequence，全局单调递增、不按日重置）。
     */
    long nextConversionNo();

    /**
     * 无锁读。
     */
    InventoryConversionEntity selectById(@Param("id") Long id);

    /**
     * 锁定单据行（{@code SELECT ... FOR UPDATE}）。
     *
     * <p>
     * 加锁顺序：先锁单据行，再锁余额行。审批时先锁本行，再按 {@code (warehouse_id, sku_id)} 升序锁本次涉及的所有余额行（源 SKU 与目标 SKU 都要锁，见
     * {@code InventoryConversionService}）。
     */
    InventoryConversionEntity lockById(@Param("id") Long id);

    /**
     * 置为已完成（审批通过），带状态 + 版本双重守卫。
     */
    int markCompleted(@Param("id") Long id, @Param("auditedAt") OffsetDateTime auditedAt,
        @Param("auditor") String auditor, @Param("auditOpinion") String auditOpinion,
        @Param("version") Integer version);

    /**
     * 置为已驳回，带状态 + 版本双重守卫。
     */
    int markRejected(@Param("id") Long id, @Param("auditedAt") OffsetDateTime auditedAt,
        @Param("auditor") String auditor, @Param("auditOpinion") String auditOpinion,
        @Param("version") Integer version);

    /**
     * 回写待审核单据的头（仅 PENDING）。
     */
    int updatePending(@Param("id") Long id, @Param("warehouseId") Long warehouseId,
        @Param("convertType") String convertType, @Param("reason") String reason, @Param("remark") String remark,
        @Param("operator") String operator);

    /**
     * 分页查询（联仓库取展示字段）。
     */
    List<InventoryConversionVO> queryPage(Page<?> page, @Param("query") InventoryConversionQueryForm query,
        @Param("scope") ScmValueScope scope);

    /**
     * 详情。
     */
    InventoryConversionVO detail(@Param("id") Long id);
}
