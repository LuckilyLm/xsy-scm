package net.lab1024.sa.admin.module.scm.inventory.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryReservationEntity;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryReservationQueryForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryReservationVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 库存预留读写。
 *
 * <p>防重靠部分唯一索引 {@code uk_inventory_reservation_source_active}
 * （{@code source_document_type + source_document_item_id}），插入用
 * {@code ON CONFLICT ... DO NOTHING} 且冲突目标与索引逐字匹配（Q11）。
 */
@Mapper
public interface InventoryReservationDao extends BaseMapper<InventoryReservationEntity> {

    /**
     * 并发安全的「首建预留」：同一来源行只会成功一次。
     *
     * @return 1 = 本次插入成功；0 = 该来源行已有有效预留（调用方按业务决定是幂等还是报错）
     */
    int insertOnConflictDoNothing(InventoryReservationEntity entity);

    /**
     * 无锁读。
     */
    InventoryReservationEntity selectById(@Param("id") Long id);

    /**
     * 按来源行查有效预留（释放时用；不锁）。
     */
    InventoryReservationEntity selectActiveBySource(@Param("sourceDocumentType") String sourceDocumentType,
                                                    @Param("sourceDocumentItemId") Long sourceDocumentItemId);

    /**
     * 按**来源单据头**查全部有效预留（订单取消时批量释放用）。
     *
     * <p>与 {@link #selectActiveBySource} 的区别在粒度：订单确认是逐行预留，
     * 订单取消却是整单释放，因此需要一个头级入口，避免调用方自己拿明细再循环。
     */
    List<InventoryReservationEntity> listActiveBySourceDocument(
            @Param("sourceDocumentType") String sourceDocumentType,
            @Param("sourceDocumentId") Long sourceDocumentId);

    /**
     * 锁定预留行（{@code SELECT ... FOR UPDATE}）。
     *
     * <p>锁序：预留行锁在**余额锁之前**获取，与出库单头锁同一层级。
     */
    InventoryReservationEntity lockById(@Param("id") Long id);

    /**
     * 置为已释放（带状态条件，防重复释放）。
     */
    int markReleased(@Param("id") Long id, @Param("operator") String operator);

    /**
     * 置为已消耗（带状态条件）。
     */
    int markConsumed(@Param("id") Long id, @Param("operator") String operator);

    /**
     * 分页查询（联仓库 / SKU / 商品取展示字段）。
     */
    List<InventoryReservationVO> queryPage(Page<?> page, @Param("query") InventoryReservationQueryForm query);
}
