package com.xsy.scm.print.support;

import com.xsy.scm.print.constant.ScmPrintDocumentTypeEnum;
import com.xsy.scm.trace.domain.vo.TraceUnitVO;
import com.xsy.scm.trace.permission.TracePermission;
import com.xsy.scm.trace.service.TraceUnitService;
import com.xsy.scm.trace.support.TracePublicLinkBuilder;

import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 包装码标签的打印数据源。
 *
 * <p>
 * 标签贴在实物上，只放码、批次、商品、包装量，以及绑定后的客户与订单号；不含价格与成本，模板也不能放宽这些字段。 取数走追溯域的查询服务，与详情页同一套数据范围。
 */
@Component
@RequiredArgsConstructor
public class TraceUnitPrintSourceProvider implements ScmPrintSourceProvider {

    private final TraceUnitService traceUnitService;

    private final TracePublicLinkBuilder tracePublicLinkBuilder;

    @Override
    public ScmPrintDocumentTypeEnum documentType() {
        return ScmPrintDocumentTypeEnum.TRACE_UNIT;
    }

    @Override
    public String queryPermission() {
        return TracePermission.UNIT_QUERY;
    }

    @Override
    public void requireVisible(Long businessId) {
        traceUnitService.printSource(businessId);
    }

    @Override
    public ScmPrintSource load(Long businessId) {
        TraceUnitVO.Replay replay = traceUnitService.printSource(businessId);
        TraceUnitVO.Unit unit = replay.getUnit();
        TraceUnitVO.Lot lot = replay.getLot();
        TraceUnitVO.Binding binding = replay.getBinding();

        var header = ScmPrintSource.values();
        header.put("unitCode", ScmPrintText.text(unit.getUnitCode()));
        header.put("lotNo", ScmPrintText.text(unit.getLotNo()));
        header.put("sourceLotNo", ScmPrintText.text(unit.getSourceLotNo()));
        header.put("productName", ScmPrintText.text(lot == null ? null : lot.getSkuName()));
        header.put("skuName", ScmPrintText.text(lot == null ? null : lot.getSkuName()));
        header.put("packageType", packageTypeLabel(unit.getPackageType()));
        header.put("quantity", ScmPrintText.fixed(unit.getQuantity()));
        header.put("unit", ScmPrintText.text(unit.getUnit()));
        header.put("netWeight", ScmPrintText.fixed(unit.getNetWeight()));
        header.put("weightUnit", ScmPrintText.text(unit.getWeightUnit()));
        // 扫码链接，前端据此生成二维码；未配置公开地址时为空，标签只印码。
        header.put("traceUrl", ScmPrintText.text(tracePublicLinkBuilder.build(unit.getPublicToken())));
        // 未绑定时留空：标签可以先贴，客户是分拣现场才确定的
        header.put("customerName", ScmPrintText.text(binding == null ? null : binding.getCustomerName()));
        header.put("orderNo", ScmPrintText.text(binding == null ? null : binding.getOrderNo()));
        return new ScmPrintSource(unit.getUnitCode(), header, new java.util.ArrayList<Map<String, String>>(), Map.of());
    }

    private String packageTypeLabel(String packageType) {
        if (packageType == null) {
            return "";
        }
        return switch (packageType) {
            case "BAG" -> "袋";
            case "BOX" -> "箱";
            case "CRATE" -> "筐";
            default -> packageType;
        };
    }
}
