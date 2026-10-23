package com.xsy.scm.device.service;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.device.domain.form.ScaleMeasurementForm;
import com.xsy.scm.device.domain.vo.ScaleMeasurementVO;
import com.xsy.scm.device.support.ScaleDeviceContext;
import com.xsy.scm.device.support.ScaleDevicePrincipal;
import com.xsy.scm.sorting.constant.SortingErrorCode;
import com.xsy.scm.sorting.domain.form.SortingScaleBasketForm;
import com.xsy.scm.sorting.domain.vo.SortingScaleBasketVO;
import com.xsy.scm.sorting.service.SortingScaleEventService;

import java.math.BigDecimal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 秤端读数上报的协议适配层。
 *
 * <p>
 * 只做三件事：把设备协议字段映射成分拣域的筐上报，补上服务端已校验的设备与员工身份，再转成设备协议要求的响应。 业务校验（单位、净重、幂等、任务归属）全部留在分拣域，避免同一规则在两个域各写一遍。
 *
 * <p>
 * 设备编码与员工身份一律取自已校验的上下文，请求体里即便带了这两个字段也不采用，否则一台设备可以冒充另一台。
 */
@Service
@RequiredArgsConstructor
public class ScaleMeasurementService {

    private final SortingScaleEventService sortingScaleEventService;

    @Transactional(rollbackFor = Exception.class)
    public ScaleMeasurementVO upload(ScaleMeasurementForm form) {
        ScaleDevicePrincipal principal = ScaleDeviceContext.require();
        BigDecimal rawReading = parseQuantity(form.getRawValue());
        BigDecimal netWeight = parseQuantity(form.getNetWeight());
        if (rawReading.signum() < 0 || netWeight.signum() < 0) {
            throw new ScmBusinessException(SortingErrorCode.SCALE_MEASUREMENT_NEGATIVE);
        }
        SortingScaleBasketForm basket = new SortingScaleBasketForm();
        basket.setEventKey(form.getEventKey());
        basket.setOperationId(form.getOperationId());
        basket.setTaskId(form.getTaskId());
        basket.setTaskItemId(form.getTaskItemId());
        basket.setDeviceCode(principal.deviceCode());
        basket.setDeviceSessionId(principal.sessionId());
        basket.setBasketNo(form.getBasketNo());
        basket.setRawReading(rawReading);
        basket.setRawUnit(form.getRawUnit());
        basket.setNetWeight(netWeight);
        basket.setBusinessUnit(form.getBusinessUnit());
        basket.setWeightKind(form.getWeightKind() == null ? "NET" : form.getWeightKind().trim());
        basket.setConversionRule(form.getConversionRule());
        basket.setTareWeight(parseOptionalQuantity(form.getTareWeight()));
        basket.setStableFlag(form.getStableFlag());
        basket.setStabilitySource(form.getStabilitySource());
        basket.setJobVersion(form.getJobVersion());
        basket.setTaskItemVersion(toItemVersion(form.getTaskItemVersion()));
        basket.setCapturedAt(form.getCapturedAt());
        basket.setProtocolType(form.getProtocolType());
        basket.setAgentVersion(form.getAgentVersion());
        return toVO(sortingScaleEventService.reportBasket(basket));
    }

    private static ScaleMeasurementVO toVO(SortingScaleBasketVO source) {
        ScaleMeasurementVO vo = new ScaleMeasurementVO();
        vo.setMeasurementId(source.getMeasurementId());
        vo.setEventKey(source.getEventKey());
        vo.setBasketNo(source.getBasketNo());
        vo.setStatus(source.getStatus());
        vo.setDuplicated(source.getDuplicated());
        vo.setAccepted(source.getAccepted());
        vo.setReceivedAt(source.getReceivedAt());
        return vo;
    }

    /** 数量字符串转十进制。设备原值可能带尾随零，用字符串接收再转，避免经过浮点丢精度。 */
    private static BigDecimal parseQuantity(String value) {
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException exception) {
            throw new ScmBusinessException(SortingErrorCode.SCALE_EVENT_STATE_INVALID);
        }
    }

    private static BigDecimal parseOptionalQuantity(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseQuantity(value);
    }

    /** 设备协议的明细版本是无符号长整型，库里是整型。超出范围说明设备与平台的数据格式对不上，直接拒绝，不截断成另一个版本号。 */
    private static Integer toItemVersion(Long value) {
        if (value == null) {
            return null;
        }
        if (value < 0 || value > Integer.MAX_VALUE) {
            throw new ScmBusinessException(SortingErrorCode.SCALE_BASKET_CONTEXT_CHANGED);
        }
        return value.intValue();
    }
}
