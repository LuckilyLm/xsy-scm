package com.xsy.scm.delivery.domain.form;

import com.xsy.scm.common.constant.ScmGeoCoordinateSystemEnum;
import com.xsy.scm.common.validation.ScmEnumValue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Data;

/**
 * GPS 轨迹上报。
 *
 * <p>
 * {@code eventKey} 是**必填**的稳定幂等键：设备在弱网下会重试，用「时间 + 坐标」当键会在
 * 静止时把多个真实点压成一个，用随机键则完全去不了重。键由上报端生成并保持不变。
 *
 * <p>
 * {@code capturedAt} 是设备采集时间，由服务端记录接收时间；两者不合并（见实体说明）。
 */
@Data
public class DeliveryGpsReportForm {

    @NotBlank(message = "事件键不能为空")
    @Size(max = 160, message = "事件键不能超过160个字符")
    private String eventKey;

    @NotNull(message = "线路不能为空")
    @Positive(message = "线路编号必须大于零")
    private Long routeId;

    @Size(max = 64, message = "设备编码不能超过64个字符")
    private String deviceCode;

    @NotNull(message = "采集时间不能为空")
    private OffsetDateTime capturedAt;

    @NotNull(message = "经度不能为空")
    @DecimalMin(value = "-180", message = "经度必须在-180至180之间")
    @DecimalMax(value = "180", message = "经度必须在-180至180之间")
    @Digits(integer = 3, fraction = 8, message = "经度最多3位整数和8位小数")
    private BigDecimal longitude;

    @NotNull(message = "纬度不能为空")
    @DecimalMin(value = "-90", message = "纬度必须在-90至90之间")
    @DecimalMax(value = "90", message = "纬度必须在-90至90之间")
    @Digits(integer = 2, fraction = 8, message = "纬度最多2位整数和8位小数")
    private BigDecimal latitude;

    @NotBlank(message = "坐标系不能为空")
    @ScmEnumValue(enumClass = ScmGeoCoordinateSystemEnum.class, message = "坐标系必须为 GCJ02 或 WGS84")
    private String geomCrs;

    @DecimalMin(value = "0", message = "定位精度不能为负")
    @Digits(integer = 8, fraction = 2, message = "定位精度最多8位整数和2位小数")
    private BigDecimal accuracyMeters;

    @DecimalMin(value = "0", message = "速度不能为负")
    @Digits(integer = 8, fraction = 2, message = "速度最多8位整数和2位小数")
    private BigDecimal speedKph;
}
