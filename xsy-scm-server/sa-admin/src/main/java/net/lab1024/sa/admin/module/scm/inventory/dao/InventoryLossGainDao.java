package net.lab1024.sa.admin.module.scm.inventory.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryLossGainEntity;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryLossGainQueryForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryLossGainVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 报损报溢单头读写。
 *
 * <p>本表是**有状态单据**，因此有 update 方法。真正的不可变纪律在
 * {@code inventory_movement} 上，不在这里。
 *
 * <p><b>每个写方法都带 {@code status = 'PENDING'} 守卫</b>：只有待审核的单据可改 / 可审。
 * 参考项目对 update / delete 没有状态守卫，那会让「已完成（已写流水）」的单据被改内容或被删掉，
 * 账与单从此对不上。本波次刻意补上，并且把守卫写进 SQL 的 {@code WHERE} 而不是只写在服务层 ——
 * 服务层的状态判断是「给人看的错误码」，SQL 的守卫才是并发下真正生效的那一道。
 *
 * <p><b>为什么只有审批带 {@code version}，update 不带</b>：审批是一道**控制**，
 * 审批人必须批准自己读到的内容；若允许在「打开 → 审批」之间被静默改掉数量，
 * 这道控制就形同虚设，因此审批用乐观锁把这种情形变成 40921。
 * 而两个人同时改同一张草稿属于录单碰撞，与全仓其它单据（出库单 / 盘点单）处理方式一致
 * —— 本项目在那一类场景上不引入乐观锁，保持一致比局部更严更重要。
 */
@Mapper
public interface InventoryLossGainDao extends BaseMapper<InventoryLossGainEntity> {

    /** 单号是否存在（软删范围内）。生成单号时用于冲突重试。 */
    int countByLossGainNo(@Param("lossGainNo") String lossGainNo);

    /** 取下一个单号序列值（PG sequence，全局单调递增、不按日 reset，跳号可接受）。 */
    long nextLossGainNo();

    /** 无锁读（详情 / 状态校验前置）。 */
    InventoryLossGainEntity selectById(@Param("id") Long id);

    /**
     * 锁定单据行（{@code SELECT ... FOR UPDATE}）。
     *
     * <p>锁序纪律：**单据锁先于余额锁**。审批通过时先锁本行，再按
     * {@code (warehouse_id, sku_id)} 升序锁余额行，与收货 / 出库 / 盘点确认同一顺序，
     * 避免四条链路以相反顺序拿锁而死锁。
     */
    InventoryLossGainEntity lockById(@Param("id") Long id);

    /**
     * 置为已完成（审批通过），带状态 + 版本双重守卫。
     *
     * @return 影响行数，必须为 1；为 0 说明状态已变或版本不符 → 调用方区分后抛 41029 / 40921
     */
    int markCompleted(@Param("id") Long id,
                      @Param("auditedAt") OffsetDateTime auditedAt,
                      @Param("auditor") String auditor,
                      @Param("auditOpinion") String auditOpinion,
                      @Param("version") Integer version);

    /** 置为已驳回，带状态 + 版本双重守卫。 */
    int markRejected(@Param("id") Long id,
                     @Param("auditedAt") OffsetDateTime auditedAt,
                     @Param("auditor") String auditor,
                     @Param("auditOpinion") String auditOpinion,
                     @Param("version") Integer version);

    /** 回写待审核单据的头（仅 PENDING）。 */
    int updatePending(@Param("id") Long id,
                      @Param("adjustType") String adjustType,
                      @Param("warehouseId") Long warehouseId,
                      @Param("reason") String reason,
                      @Param("remark") String remark,
                      @Param("operator") String operator);

    /** 分页查询（联仓库取展示字段）。 */
    List<InventoryLossGainVO> queryPage(Page<?> page, @Param("query") InventoryLossGainQueryForm query);

    /** 详情。 */
    InventoryLossGainVO detail(@Param("id") Long id);
}
