package com.xsy.scm.screen.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 大屏地图使用行政区划质心定位汇总气泡，不代表客户真实地址。省级数据由市级汇总上卷，确保数值一致。
 */
@Data
@Schema(description = "数据大屏-地理分布")
public class ScreenGeoVO {

    @Schema(description = "按市聚合的节点（ECharts 气泡层），按客户数倒序")
    private List<CityNode> cities;

    @Schema(description = "按省上卷的分布（ECharts 着色层），按客户数倒序")
    private List<ProvinceNode> provinces;

    @Schema(description = "地理归属覆盖度")
    private Coverage coverage;

    /**
     * 地图坐标来自行政区划字典，缺少坐标的记录不画气泡，但仍计入覆盖率的未定位差额。
     */
    @Data
    @Schema(description = "市级地理节点")
    public static class CityNode {

        @Schema(description = "市级行政区划码")
        private Integer cityCode;

        @Schema(description = "市名称")
        private String cityName;

        @Schema(description = "所属省级行政区划码")
        private Integer provinceCode;

        @Schema(description = "所属省名称（字典侧展示名，与底图要素名不必相同）")
        private String provinceName;

        @Schema(description = "区划质心经度，GCJ-02")
        private BigDecimal centerLng;

        @Schema(description = "区划质心纬度，GCJ-02")
        private BigDecimal centerLat;

        @Schema(description = "该市活动客户数")
        private Long customerCount;

        @Schema(description = "该市活动供应商数")
        private Long supplierCount;

        @Schema(description = "该市启用仓库数")
        private Long warehouseCount;
    }

    /**
     * 省级分布。与底图对齐用 {@code provinceCode}（= GeoJSON 的 {@code adcode}，同为 GB/T 2260 六位码），而不是名称：
     * 字典里港澳用简称，官方边界数据用全称，按名称匹配会静默丢省。{@code provinceName} 只是字典侧的展示名。
     */
    @Data
    @Schema(description = "省级地理节点")
    public static class ProvinceNode {

        @Schema(description = "省级行政区划码")
        private Integer provinceCode;

        @Schema(description = "省名称")
        private String provinceName;

        @Schema(description = "已出现（至少有一个归属主档）的地级市数")
        private Integer cityCount;

        @Schema(description = "该省客户数")
        private Long customerCount;

        @Schema(description = "该省供应商数")
        private Long supplierCount;

        @Schema(description = "该省启用仓库数")
        private Long warehouseCount;
    }

    /**
     * 覆盖度：{@code *Total} 是参与统计的全部主档，{@code *Located} 是其中已解析出市级归属的。
     *
     * <p>
     * 两者之差就是「地图上找不到位置」的量，大屏必须显示出来，避免用户以为看到的分布就是全部业务量。仓库与地图节点一致：只统计启用仓。
     */
    @Data
    @Schema(description = "地理归属覆盖度")
    public static class Coverage {

        @Schema(description = "活动客户总数")
        private Long customerTotal;

        @Schema(description = "已归属到市的客户数")
        private Long customerLocated;

        @Schema(description = "活动供应商总数")
        private Long supplierTotal;

        @Schema(description = "已归属到市的供应商数")
        private Long supplierLocated;

        @Schema(description = "启用仓库总数")
        private Long warehouseTotal;

        @Schema(description = "已归属到市的启用仓库数")
        private Long warehouseLocated;
    }
}
