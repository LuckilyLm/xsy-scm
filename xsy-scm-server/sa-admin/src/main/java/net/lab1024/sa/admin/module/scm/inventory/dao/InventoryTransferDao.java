package net.lab1024.sa.admin.module.scm.inventory.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import net.lab1024.sa.admin.module.scm.common.scope.ScmValueScope;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryTransferEntity;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryTransferQueryForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryInTransitVO;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryTransferVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 调拨单头读写。
 *
 * <p><b>每个写方法都带状态守卫</b>（{@code WHERE status = ...}）：
 * 发出仅 {@code DRAFT}、收货仅 {@code SHIPPED}、改 / 删仅 {@code DRAFT}。
 * 守卫写在 SQL 里而不只是服务层 —— 服务层的状态判断是「给人看的错误码」，
 * SQL 的守卫才是并发下真正生效的那一道。
 *
 * <p><b>为什么不需要乐观锁</b>：发出与收货都先 {@link #lockById}（{@code FOR UPDATE}）
 * 持有单据行锁，再读明细。并发的「改草稿」也要拿同一把行锁 → 它只能等发出/收货提交后才执行，
 * 而那时状态已不是 DRAFT，改草稿会被守卫拒绝。行锁天然把「读明细 → 写流水」这段序列化了，
 * 所以不需要额外的版本号（报损报溢的审批需要，是因为它的「读」发生在弹窗打开那一刻，
 * 不在锁的保护范围内）。
 */
@Mapper
public interface InventoryTransferDao extends BaseMapper<InventoryTransferEntity> {

    /**
     * 单号是否存在（软删范围内）。生成单号时用于冲突重试。
     */
    int countByTransferNo(@Param("transferNo") String transferNo);

    /**
     * 取下一个单号序列值（PG sequence，全局单调递增、不按日 reset，跳号可接受）。
     */
    long nextTransferNo();

    /**
     * 无锁读（详情 / 状态校验前置）。
     */
    InventoryTransferEntity selectById(@Param("id") Long id);

    /**
     * 锁定单据行（{@code SELECT ... FOR UPDATE}）。
     *
     * <p>锁序纪律：**单据锁先于余额锁**。发出时先锁本行再按 {@code (warehouse_id, sku_id)}
     * 升序锁源仓余额行；收货时同理锁目标仓余额行。与收货 / 出库 / 盘点 / 报损报溢同一顺序。
     */
    InventoryTransferEntity lockById(@Param("id") Long id);

    /**
     * 置为在途（带状态条件，防并发重复发出）。
     */
    int markShipped(@Param("id") Long id,
                    @Param("shippedAt") OffsetDateTime shippedAt,
                    @Param("shippedBy") String shippedBy);

    /**
     * 置为已收货（带状态条件，只有在途可收货）。
     */
    int markReceived(@Param("id") Long id,
                     @Param("receivedAt") OffsetDateTime receivedAt,
                     @Param("receivedBy") String receivedBy);

    /**
     * 置为已取消（带状态条件，只有草稿可取消）。
     */
    int markCancelled(@Param("id") Long id,
                      @Param("operator") String operator);

    /**
     * 回写草稿头（源仓 / 目标仓 / 备注）。
     */
    int updateDraft(@Param("id") Long id,
                    @Param("fromWarehouseId") Long fromWarehouseId,
                    @Param("toWarehouseId") Long toWarehouseId,
                    @Param("remark") String remark,
                    @Param("operator") String operator);

    /**
     * 分页查询（联两次 warehouse 取源仓 / 目标仓展示字段）。
     *
     * <p>调拨的可见口径是「源仓或目标仓任一被授权」，只授权一端的人看不见在途单就没法对账。
     */
    List<InventoryTransferVO> queryPage(Page<?> page, @Param("query") InventoryTransferQueryForm query,
                                        @Param("scope") ScmValueScope scope);

    /**
     * 详情。
     */
    InventoryTransferVO detail(@Param("id") Long id);

    /**
     * 在途库存报表：按调拨单明细聚合 SHIPPED 状态的调拨量。
     *
     * <p>只读聚合，不修改任何业务表；结果按 (transfer_no, sku_id) 展开。
     */
    List<InventoryInTransitVO> queryInTransit(@Param("scope") ScmValueScope scope);
}
