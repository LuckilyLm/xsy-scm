package com.xsy.scm.screen.dao;

import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.screen.domain.vo.ScreenGeoVO;
import com.xsy.scm.screen.domain.vo.ScreenInventoryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

/** 大屏 DAO 只查仓库分布和地理网络等展示数据；销售、采购等经营指标沿用统一统计服务。 */
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
     * 按市聚合的主档归属行，坐标取 {@code xsy_region} 的区划质心。
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
