package net.lab1024.sa.admin.module.scm.inventory.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import net.lab1024.sa.admin.module.scm.common.scope.ScmValueScope;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryWarningThresholdEntity;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryWarningQueryForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryWarningThresholdQueryForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryWarningThresholdVO;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryWarningVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 库存预警阈值配置读写 + 预警列表查询。
 *
 * <p><b>本 DAO 不碰 {@code inventory_balance}</b>（除预警列表的只读 LEFT JOIN）：
 * 阈值是配置，余额是派生状态，两者的写路径必须完全分开 —— 否则配置路径就会成为
 * 「创建没有流水支撑的余额行」的入口。
 *
 * <p>配置的增删用 MyBatis-Plus 的 {@code insert / deleteById}；**编辑必须走手写 SQL**
 * （{@link #updateThreshold}）—— 见该方法的注释：用 {@code updateById} 会把
 * 实体上标了 {@code updateStrategy = ALWAYS} 的字段（含 {@code created_at}）一并写进
 * SET 子句，而新建的更新实体里 {@code created_at} 是 {@code null}，
 * 直接违反 NOT NULL。手写 SQL 只更新真正该变的列，与全仓其它模块一致。
 */
@Mapper
public interface InventoryWarningThresholdDao extends BaseMapper<InventoryWarningThresholdEntity> {

    /**
     * 该 (仓库, SKU) 是否已有有效配置（新增时的防重锚点，与部分唯一索引同义）。
     */
    int countByWarehouseAndSku(@Param("warehouseId") Long warehouseId,
                               @Param("skuId") Long skuId);

    /**
     * 编辑配置（手写 SQL，带 {@code version} 乐观锁 + {@code deleted} 守卫）。
     *
     * <p>{@code warn_min} / {@code warn_max} 允许传 {@code null}（= 清空该边界），
     * 所以不能依赖 MyBatis 的默认非空策略 —— 手写 SQL 天然支持这一点。
     *
     * @return 影响行数，必须为 1；为 0 说明行不存在或版本不符
     */
    int updateThreshold(@Param("id") Long id,
                        @Param("warehouseId") Long warehouseId,
                        @Param("skuId") Long skuId,
                        @Param("warnMin") BigDecimal warnMin,
                        @Param("warnMax") BigDecimal warnMax,
                        @Param("remark") String remark,
                        @Param("version") Integer version,
                        @Param("operator") String operator);

    /**
     * 配置列表（联仓库 / SKU / 商品取展示字段）。
     */
    List<InventoryWarningThresholdVO> queryPage(Page<?> page,
                                                @Param("query") InventoryWarningThresholdQueryForm query,
                                                @Param("scope") ScmValueScope scope);

    /**
     * 配置详情（按 id）。
     */
    InventoryWarningThresholdVO detail(@Param("id") Long id);

    /**
     * 预警列表：阈值配置 LEFT JOIN 余额。
     *
     * <p>用 LEFT JOIN 而不是 JOIN：配置了阈值但**没有余额行**的 (仓库, SKU) 必须出现
     * （那正是「设了下限却一件没有」，应当预警），数量用 {@code COALESCE} 按 0 计。
     *
     * <p>{@code status} 为空时只返回异常项（LOW / HIGH）—— 预警列表的默认语义。
     * 状态表达式与过滤谓词共用 XML 里的同一个 {@code <sql>} 片段，
     * 避免「判定规则写两遍」这种必然漂移的写法。
     */
    List<InventoryWarningVO> queryWarningPage(Page<?> page, @Param("query") InventoryWarningQueryForm query,
                                              @Param("scope") ScmValueScope scope);
}
