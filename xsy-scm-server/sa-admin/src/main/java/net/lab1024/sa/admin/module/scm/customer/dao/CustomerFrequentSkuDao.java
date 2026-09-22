package net.lab1024.sa.admin.module.scm.customer.dao;

import net.lab1024.sa.admin.module.scm.customer.domain.vo.CustomerFrequentSkuVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 客户「常购商品」只读聚合（Wave 7 客户 360°）。
 *
 * <p>这是一次<b>跨域只读</b>：聚合源是订单事实表 {@code sales_order / sales_order_item}，
 * 但读模型归客户视图所有，因此与 {@code CustomerQueryService} 补全供应商 / 业务员名同为跨域读，
 * 不新增客户表、也不把结果落成副本。纯 SELECT，不触碰订单写路径。
 */
@Mapper
public interface CustomerFrequentSkuDao {

    /**
     * 近 {@code since}（含）之后已确认订单，按 (SKU, 销售单位快照) 聚合出常购行。
     *
     * @param customerId 客户 ID，聚合的唯一上下文
     * @param since      窗口下界（Asia/Shanghai 日界算出的时刻），过滤 {@code confirmed_at}
     * @param limit      返回分组数上限（Service 已裁剪到安全区间）
     */
    List<CustomerFrequentSkuVO> frequentSkus(@Param("customerId") Long customerId,
                                             @Param("since") OffsetDateTime since,
                                             @Param("limit") int limit);
}
