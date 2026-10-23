package com.xsy.scm.supplier.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.supplier.domain.entity.SupplierSkuEntity;
import com.xsy.scm.supplier.domain.form.SupplierSkuQueryForm;
import com.xsy.scm.supplier.domain.vo.OrderableSkuVO;
import com.xsy.scm.supplier.domain.vo.SupplierSkuCountVO;
import com.xsy.scm.supplier.domain.vo.SupplierSkuSupplierSummaryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface SupplierSkuDao extends BaseMapper<SupplierSkuEntity> {

    /** 活动关联行，按 id 升序，供替换编辑页回填。 */
    List<SupplierSkuEntity> selectActiveBySupplierId(@Param("supplierId") Long supplierId);

    /**
     * 加行锁读取某供应商的全部活动关联行，是写入路径的第二把锁。
     *
     * <p>
     * {@code ORDER BY id ... FOR UPDATE} 保证多行加锁顺序固定，配合 supplier 行锁形成稳定锁序。
     */
    List<SupplierSkuEntity> selectActiveBySupplierIdForUpdate(@Param("supplierId") Long supplierId);

    /**
     * 检查某个 SKU 是否有可采购的供应商来源，统一检查供应商、供应商 SKU、SPU 与 SKU 的启用状态。
     */
    List<SupplierSkuEntity> selectEnabledBySkuId(@Param("skuId") Long skuId);

    /** 只读反查分页。 */
    List<SupplierSkuEntity> queryPage(Page<?> page, @Param("query") SupplierSkuQueryForm query);

    /**
     * 按供应商汇总可供货情况。
     *
     * <p>
     * 商品数按 SPU 去重、规格数按关联行计数：一个商品常挂多个规格，两者不等是常态。
     *
     * @param status
     *            可选的关联状态过滤；为空表示全部
     */
    List<SupplierSkuSupplierSummaryVO> summarizeBySupplier(@Param("status") String status);

    /**
     * 原子软删：{@code supplier_id + id + version} 三个条件。 {@code supplier_id} 保证不会误删其他供应商的行。
     */
    int softDeleteOwnedWithVersion(@Param("supplierId") Long supplierId, @Param("id") Long id,
        @Param("version") Integer version, @Param("operator") String operator);

    /** 删除供应商前的活动关联检查。 */
    long countActiveBySupplierId(@Param("supplierId") Long supplierId);

    /** 列表批量补全 {@code skuCount}，一次查询完成，不走 N+1。 */
    List<SupplierSkuCountVO> countActiveBySupplierIds(@Param("supplierIds") Collection<Long> supplierIds);

    /**
     * 读取可下单 SKU（SPU 与 SKU 同时上架），用于构造订单行保存的商品信息。
     *
     * <p>
     * 空结果表示 SKU 不存在、已删除或未上架，调用方报 40942。
     */
    List<OrderableSkuVO> selectOrderableSkuByIds(@Param("skuIds") Collection<Long> skuIds);
}
