package net.lab1024.sa.admin.module.scm.purchase.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import net.lab1024.sa.admin.module.scm.common.scope.ScmValueScope;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseReceiptEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptQueryForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 收货单（W5 Target Design §5.7 / §4.3，Q7：一个采购单 → 多张独立收货单）。
 *
 * <p>收货单**继承并快照**采购单的供应商 / 仓库（P5），因此这里没有任何
 * 「按入参改供应商 / 仓库」的写方法 —— 跨仓收货在结构上不可能发生。
 *
 * <p>{@link #nextReceiptNo()} 取全局序列（不按日 reset），由
 * {@code PurchaseNumberGenerator} 拼成 {@code PR + yyyyMMdd + 至少 6 位}。
 */
@Mapper
public interface PurchaseReceiptDao extends BaseMapper<PurchaseReceiptEntity> {

    /**
     * 分页查询。
     *
     * <p>收货单本身没有采购员列，{@code scope}（采购员维度）在 Mapper 里按
     * {@code EXISTS} 半连父采购单的 {@code purchaser_id} 判定；{@code null} 失败关闭返回 0 行。
     * 仓库维度的收窄属库存/仓库侧口径，不在本语句里重复实现。
     */
    List<PurchaseReceiptVO> query(Page<?> page, @Param("query") PurchaseReceiptQueryForm query,
                                  @Param("scope") ScmValueScope scope);

    /**
     * 详情（单头）。
     */
    PurchaseReceiptVO detail(@Param("id") Long id);

    /**
     * 单条 `FOR UPDATE`。
     */
    PurchaseReceiptEntity lock(@Param("id") Long id);

    /**
     * 全局单调递增的收货单号序列（不按日 reset）。
     */
    Long nextReceiptNo();

    /**
     * 本采购单的活动收货单（校验「是否已收过」用）。
     */
    List<PurchaseReceiptEntity> listActiveByOrderId(@Param("purchaseOrderId") Long purchaseOrderId);

    /**
     * 软删（仅 DRAFT，由 Service 断言）。
     */
    int softDelete(@Param("id") Long id,
                   @Param("version") Integer version,
                   @Param("operator") String operator);
}
