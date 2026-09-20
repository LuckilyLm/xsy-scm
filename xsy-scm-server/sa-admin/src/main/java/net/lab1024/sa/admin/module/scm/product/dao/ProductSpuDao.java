package net.lab1024.sa.admin.module.scm.product.dao;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import net.lab1024.sa.admin.module.scm.product.domain.entity.ProductSpuEntity;
import net.lab1024.sa.admin.module.scm.product.domain.form.ProductSpuQueryForm;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
@Mapper
public interface ProductSpuDao extends BaseMapper<ProductSpuEntity> {
    List<ProductSpuEntity> queryPage(Page<?> page, @Param("query") ProductSpuQueryForm query, @Param("categoryIds") List<Long> categoryIds);

    /** 商品是否产生过订单、采购、库存或价格事实；为真时禁止删除档案。 */
    boolean hasBusinessReference(@Param("spuId") Long spuId);

    /** 批量命令的统一加锁入口：按 id 升序锁住活动行后复核乐观锁版本。 */
    List<ProductSpuEntity> lockByIds(@Param("spuIds") List<Long> spuIds);

    /**
     * 对已加锁、已复核版本的行做一条批量 UPDATE；调用方必须先在事务内持有这些行锁，
     * 因此这里不再带 version 条件，只刷新 version 与审计列。null 字段表示本次不改。
     */
    int batchApply(@Param("spuIds") List<Long> spuIds, @Param("status") String status,
                   @Param("masterStatus") String masterStatus, @Param("categoryId") Long categoryId,
                   @Param("operator") String operator);
}
