package net.lab1024.sa.admin.module.scm.inventory.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import net.lab1024.sa.admin.module.scm.common.scope.ScmValueScope;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryConversionEntity;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryConversionQueryForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryConversionVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 规格转换单头读写。
 *
 * <p><b>每个写方法都带 {@code status = 'PENDING'} 守卫</b>：只有待审核的单据可改 / 可审。
 * 守卫写在 SQL 的 {@code WHERE} 里而不只是服务层 —— 服务层的状态判断是「给人看的错误码」，
 * SQL 的守卫才是并发下真正生效的那一道。
 *
 * <p><b>为什么不需要乐观锁在 update 上</b>：改待审核与审批都先
 * {@link #lockById}（{@code FOR UPDATE}）持有单据行锁，并发的改草稿要拿同一把锁 →
 * 只能等前者提交后才执行，而那时状态已不是 PENDING，会被守卫拒绝。
 * 行锁把「读明细 → 写流水」这段序列化了。审批仍**额外**带 {@code version}：
 * 它的「读」发生在弹窗打开那一刻，不在锁的保护范围内。
 */
@Mapper
public interface InventoryConversionDao extends BaseMapper<InventoryConversionEntity> {

    /**
     * 单号是否存在（软删范围内）。
     */
    int countByConversionNo(@Param("conversionNo") String conversionNo);

    /**
     * 取下一个单号序列值（PG sequence，全局单调递增、不按日 reset）。
     */
    long nextConversionNo();

    /**
     * 无锁读。
     */
    InventoryConversionEntity selectById(@Param("id") Long id);

    /**
     * 锁定单据行（{@code SELECT ... FOR UPDATE}）。
     *
     * <p>锁序纪律：**单据锁先于余额锁**。审批时先锁本行，
     * 再按 {@code (warehouse_id, sku_id)} 升序锁本次涉及的所有余额行
     * （源 SKU 与目标 SKU 都要锁，见 {@code InventoryConversionService}）。
     */
    InventoryConversionEntity lockById(@Param("id") Long id);

    /**
     * 置为已完成（审批通过），带状态 + 版本双重守卫。
     */
    int markCompleted(@Param("id") Long id,
                      @Param("auditedAt") OffsetDateTime auditedAt,
                      @Param("auditor") String auditor,
                      @Param("auditOpinion") String auditOpinion,
                      @Param("version") Integer version);

    /**
     * 置为已驳回，带状态 + 版本双重守卫。
     */
    int markRejected(@Param("id") Long id,
                     @Param("auditedAt") OffsetDateTime auditedAt,
                     @Param("auditor") String auditor,
                     @Param("auditOpinion") String auditOpinion,
                     @Param("version") Integer version);

    /**
     * 回写待审核单据的头（仅 PENDING）。
     */
    int updatePending(@Param("id") Long id,
                      @Param("warehouseId") Long warehouseId,
                      @Param("convertType") String convertType,
                      @Param("reason") String reason,
                      @Param("remark") String remark,
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
