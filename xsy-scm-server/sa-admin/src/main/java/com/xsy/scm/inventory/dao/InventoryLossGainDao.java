package com.xsy.scm.inventory.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.inventory.domain.entity.InventoryLossGainEntity;
import com.xsy.scm.inventory.domain.form.InventoryLossGainQueryForm;
import com.xsy.scm.inventory.domain.vo.InventoryLossGainVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

/** 报损报溢单可以更新，但所有写入都必须在 SQL 中限制为待审核状态。审批额外校验版本号，防止批准前单据内容被修改。 */
@Mapper
public interface InventoryLossGainDao extends BaseMapper<InventoryLossGainEntity> {

    /**
     * 单号是否存在（软删范围内）。生成单号时用于冲突重试。
     */
    int countByLossGainNo(@Param("lossGainNo") String lossGainNo);

    /**
     * 取下一个单号序列值（PG sequence，全局单调递增、不按日 reset，跳号可接受）。
     */
    long nextLossGainNo();

    /**
     * 无锁读（详情 / 状态校验前置）。
     */
    InventoryLossGainEntity selectById(@Param("id") Long id);

    /**
     * 锁定单据行（{@code SELECT ... FOR UPDATE}）。
     *
     * <p>
     * 锁序纪律：<b>单据锁先于余额锁</b>。审批通过时先锁本行，再按 {@code (warehouse_id, sku_id)} 升序锁余额行，与收货 / 出库 / 盘点确认同一顺序，避免四条链路以相反顺序拿锁而死锁。
     */
    InventoryLossGainEntity lockById(@Param("id") Long id);

    /**
     * 置为已完成（审批通过），带状态 + 版本双重守卫。
     *
     * @return 影响行数，必须为 1；为 0 说明状态已变或版本不符 → 调用方区分后抛 41029 / 40921
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
    int updatePending(@Param("id") Long id, @Param("adjustType") String adjustType,
            @Param("warehouseId") Long warehouseId, @Param("reason") String reason, @Param("remark") String remark,
            @Param("operator") String operator);

    /**
     * 分页查询（联仓库取展示字段）。
     */
    List<InventoryLossGainVO> queryPage(Page<?> page, @Param("query") InventoryLossGainQueryForm query,
            @Param("scope") ScmValueScope scope);

    /**
     * 详情。
     */
    InventoryLossGainVO detail(@Param("id") Long id);
}
