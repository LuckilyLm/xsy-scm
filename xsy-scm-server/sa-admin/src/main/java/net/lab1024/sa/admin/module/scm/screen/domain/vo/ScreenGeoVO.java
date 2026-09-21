package net.lab1024.sa.admin.module.scm.screen.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 数据大屏-地理分布快照（只读聚合，地图 M1）。
 *
 * <p>坐标一律来自 {@code scm_region} 的**区划质心**（GCJ-02），不是任何单位的实际地址坐标；
 * 质心只用于把「某个市有 N 个客户」画在地图的正确位置上，不代表该市的范围或点位。
 *
 * <p>省级与市级两层都由市级聚合行**在 Java 侧上卷**得到，因此省界数值与气泡之和恒等，
 * 不会出现「省级图例和市级气泡对不上」这种两份 SQL 各自演算的漂移。
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
     * 市级节点。
     *
     * <p>{@code centerLng} / {@code centerLat} 来自区划字典，字典里查不到的编码不会成为节点
     * （没有坐标的气泡画不出来），这类记录只会体现在 {@code coverage} 的「已归属」与气泡之和的差额里。
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
     * 省级分布。与底图要素对齐的键是 {@code provinceCode}（= GeoJSON 的 {@code adcode}，
     * 同为 GB/T 2260 六位码），<b>不是</b>名称：字典里港澳用简称（香港 / 澳门），
     * 官方边界数据用全称（香港特别行政区 / 澳门特别行政区），按名称匹配会静默丢省。
     * {@code provinceName} 只是字典侧的展示名。
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
     * <p>两者之差就是「地图上找不到位置」的量，大屏必须把它显性化，
     * 而不是让用户以为看到的分布等于全部业务量。仓库口径与地图节点一致：只统计启用仓。
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
