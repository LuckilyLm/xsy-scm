package com.xsy.scm.screen.dao;

import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.screen.domain.vo.ScreenGeoVO;
import com.xsy.scm.screen.domain.vo.ScreenInventoryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 数据大屏专属的只读聚合 DAO：只放<b>呈现形态本身属于大屏</b>的查询（仓库分布条、供应链网络节点、地理分布）。
 *
 * <p>
 * 经营类指标（销售额 / 订单 / 采购 / 库存健康度 / 趋势 / 排行）不在这里 —— 它们是首页、大屏、报表共用的口径， 统一由 {@code ScmBusinessMetricsDao} 定义。同一件事在三处各写一份
 * SQL，漂移后不会报错，只会给出三个不同的数。
 *
 * <p>
 * <b>{@code scope} 是必填参数</b>：大屏是业务列表的聚合视图，不能成为绕过数据范围的旁门 —— 一次页面加载要发多个 Dao 调用， 因此上下文由 {@code ScreenDataService}
 * 每次请求解析一次再逐个下传。
 */
@Mapper
public interface ScreenDataDao {

    List<ScreenInventoryVO.WarehouseDistribution> inventoryDistributionByWarehouse(
            @Param("scope") ScmDataScopeContext scope);

    /**
     * 供应链网络节点：仅启用仓库，带库存量与今日出库量（出库方向的全部流水类型）。
     */
    List<ScreenInventoryVO.WarehouseNode> warehouseNetworkNodes(@Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime, @Param("outboundTypes") List<String> outboundTypes,
            @Param("scope") ScmDataScopeContext scope);

    /**
     * 按市聚合的主档归属行，坐标取 {@code scm_region} 的区划质心。
     *
     * <p>
     * 只返回<b>已解析出市级归属、且编码能在区划字典里查到</b>的行；省级分布由调用方在这里的结果上向上卷一层， 避免两份 SQL 各自演算导致省界与气泡对不上。
     */
    List<ScreenGeoVO.CityNode> geoCityRows(@Param("scope") ScmDataScopeContext scope);

    /**
     * 三张主档各自的「总数 / 已归属数」，与 {@link #geoCityRows(ScmDataScopeContext)} 共用同一份口径片段。
     */
    ScreenGeoVO.Coverage geoCoverage(@Param("scope") ScmDataScopeContext scope);
}
