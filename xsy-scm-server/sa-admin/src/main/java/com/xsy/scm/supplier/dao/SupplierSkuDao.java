package com.xsy.scm.supplier.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.supplier.domain.entity.SupplierSkuEntity;
import com.xsy.scm.supplier.domain.form.SupplierSkuQueryForm;
import com.xsy.scm.supplier.domain.vo.OrderableSkuVO;
import com.xsy.scm.supplier.domain.vo.SupplierSkuCountVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface SupplierSkuDao extends BaseMapper<SupplierSkuEntity> {

    /**
     * 活动关联行，按 id 升序（替换编辑页回填用）。
     */
    List<SupplierSkuEntity> selectActiveBySupplierId(@Param("supplierId") Long supplierId);

    /**
     * 加行锁读取某供应商的全部活动关联行（写入路径的<b>第二把锁</b>）。
     *
     * <p>{@code ORDER BY id ... FOR UPDATE} 保证多行加锁顺序确定，配合 {@code supplier} 行锁形成
     * 固定锁序。
     */
    List<SupplierSkuEntity> selectActiveBySupplierIdForUpdate(@Param("supplierId") Long supplierId);

    /**
     * 检查某个 SKU 是否有可采购的供应商来源。
     *
     * <p>该查询统一检查供应商、供应商 SKU、商品 SPU 与 SKU 的启用状态，供
     * {@code SupplierSkuService.requireEnabledForPurchasing} 复用。
     */
    List<SupplierSkuEntity> selectEnabledBySkuId(@Param("skuId") Long skuId);

    /**
     * 只读反查分页。
     */
    List<SupplierSkuEntity> queryPage(Page<?> page, @Param("query") SupplierSkuQueryForm query);

    /**
     * 原子软删：{@code supplier_id + id + version} 三谓词。
     *
     * <p>{@code supplier_id} 谓词保证不会误删其他供应商的行——即使调用方传错了 id。
     */
    int softDeleteOwnedWithVersion(@Param("supplierId") Long supplierId,
                                   @Param("id") Long id,
                                   @Param("version") Integer version,
                                   @Param("operator") String operator);

    /**
     * 删除供应商前的活动关联检查。
     */
    long countActiveBySupplierId(@Param("supplierId") Long supplierId);

    /**
     * 列表批量补全 {@code skuCount}，一次查询完成，不 N+1。
     */
    List<SupplierSkuCountVO> countActiveBySupplierIds(@Param("supplierIds") Collection<Long> supplierIds);

    /**
     * 读取可下单 SKU（SPU 与 SKU 同时 {@code ON_SHELF}）用于构造快照。
     *
     * <p>空结果表示 SKU 不存在、已软删、或 SPU/SKU 任一未上架 → 调用方报 40942。
     */
    List<OrderableSkuVO> selectOrderableSkuByIds(@Param("skuIds") Collection<Long> skuIds);
}
