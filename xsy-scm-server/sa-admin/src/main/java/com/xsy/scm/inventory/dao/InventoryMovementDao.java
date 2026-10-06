package com.xsy.scm.inventory.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.inventory.domain.entity.InventoryMovementEntity;
import com.xsy.scm.inventory.domain.form.InventoryMovementQueryForm;
import com.xsy.scm.inventory.domain.vo.InventoryMovementVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 库存流水读写。
 *
 * <p>
 * <b>append-only 的接口形态</b>：本接口只有 <b>insert + select</b>，没有任何 update / delete 方法 —— 这不是「忘了写」，而是硬化后的契约。表上还有
 * {@code ck_inventory_movement_append_only CHECK (deleted = FALSE)} 在数据库层兜底，因此即使有人绕过 DAO 写 SQL 去改历史流水，也会被 PG 拒绝。
 *
 * <p>
 * 未来冲销（采购退货等）必须<b>新增反向 movement</b>，而不是修改/删除历史行。
 */
@Mapper
public interface InventoryMovementDao extends BaseMapper<InventoryMovementEntity> {

    /**
     * 追加一条流水，源身份冲突时<b>不做任何事</b>（防重）。
     *
     * <p>
     * 冲突目标与部分唯一索引 {@code uk_inventory_movement_source_active} 完全匹配：
     * {@code ON CONFLICT (source_document_type, source_document_item_id)
     * WHERE deleted = FALSE AND source_document_item_id IS NOT NULL DO NOTHING}。
     *
     * <p>
     * <b>返回值的语义由调用方区分</b>：
     * <ul>
     * <li>实时 confirm 路径：返回 0 = 该源事实已入库，属于<b>不可能发生的数据异常</b> → 抛 {@code INVENTORY_DUPLICATE_INBOUND(41002)}
     * 让事务整体回滚（fail-fast，绝不静默吞掉）；</li>
     * <li>backfill 路径：返回 0 是<b>预期值</b>（幂等跳过），不抛错。</li>
     * </ul>
     *
     * @return 1 = 已追加；0 = 源身份已存在，本次未写入
     */
    int insertOnConflictDoNothing(InventoryMovementEntity entity);

    /**
     * 流水分页（联仓库 / SKU / 商品取展示字段，并取收货单号供跳转）。
     */
    List<InventoryMovementVO> queryPage(Page<?> page, @Param("query") InventoryMovementQueryForm query,
            @Param("scope") ScmValueScope scope);

    /**
     * 按来源行读回那一条活动流水。
     *
     * <p>
     * 流水是 append-only，跨事务的两步动作（调拨「发出 → 收货」）拿不到上一步的内存值，只能回读<b>已冻结的事实</b>。列对
     * {@code (source_document_type, source_document_item_id)} 上的部分唯一索引 {@code uk_inventory_movement_source_active}
     * 逐字匹配，因此至多一行。
     */
    InventoryMovementEntity selectBySourceItem(@Param("sourceDocumentType") String sourceDocumentType,
            @Param("sourceDocumentItemId") Long sourceDocumentItemId);

    /**
     * 某个收货行是否已有活动流水（IT / 对账用，只读）。
     */
    int countActiveBySourceItem(@Param("sourceDocumentType") String sourceDocumentType,
            @Param("sourceDocumentItemId") Long sourceDocumentItemId);

    List<InventoryMovementEntity> listSalesOutAllocations(@Param("orderItemId") Long orderItemId);
}
