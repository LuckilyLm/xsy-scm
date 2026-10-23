package com.xsy.scm.inventory.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.inventory.domain.entity.InventoryTransferEntity;
import com.xsy.scm.inventory.domain.form.InventoryTransferQueryForm;
import com.xsy.scm.inventory.domain.vo.InventoryInTransitVO;
import com.xsy.scm.inventory.domain.vo.InventoryTransferVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 调拨单在 SQL 中按状态限制写入：草稿才能修改、删除和发出，发出后才能收货。发出和收货使用单据行锁防止并发重复处理。
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
     * <p>
     * 锁序纪律：<b>单据锁先于余额锁</b>。发出时先锁本行再按 {@code (warehouse_id, sku_id)} 升序锁源仓余额行；收货时同理锁目标仓余额行。与收货 / 出库 / 盘点 / 报损报溢同一顺序。
     */
    InventoryTransferEntity lockById(@Param("id") Long id);

    /**
     * 置为在途（带状态条件，防并发重复发出）。
     */
    int markShipped(@Param("id") Long id, @Param("shippedAt") OffsetDateTime shippedAt,
        @Param("shippedBy") String shippedBy);

    /**
     * 置为已收货（带状态条件，只有在途可收货）。
     */
    int markReceived(@Param("id") Long id, @Param("receivedAt") OffsetDateTime receivedAt,
        @Param("receivedBy") String receivedBy);

    /**
     * 置为已取消（带状态条件，只有草稿可取消）。
     */
    int markCancelled(@Param("id") Long id, @Param("operator") String operator);

    /**
     * 回写草稿头（源仓 / 目标仓 / 备注）。
     */
    int updateDraft(@Param("id") Long id, @Param("fromWarehouseId") Long fromWarehouseId,
        @Param("toWarehouseId") Long toWarehouseId, @Param("remark") String remark, @Param("operator") String operator);

    /**
     * 分页查询（联两次 warehouse 取源仓 / 目标仓展示字段）。
     *
     * <p>
     * 调拨的可见口径是「源仓或目标仓任一被授权」，只授权一端的人看不见在途单就没法对账。
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
     * <p>
     * 只读聚合，不修改任何业务表；结果按 (transfer_no, sku_id) 展开。
     */
    List<InventoryInTransitVO> queryInTransit(@Param("scope") ScmValueScope scope);
}
