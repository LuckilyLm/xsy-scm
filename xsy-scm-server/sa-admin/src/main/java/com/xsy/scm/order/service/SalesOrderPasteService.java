package com.xsy.scm.order.service;

import com.xsy.scm.common.constant.ScmCustomerStatusEnum;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.customer.dao.CustomerDao;
import com.xsy.scm.order.domain.form.SalesOrderPasteForm;
import com.xsy.scm.order.domain.vo.SalesOrderImportErrorVO;
import com.xsy.scm.order.domain.vo.SalesOrderPastePreviewRowVO;
import com.xsy.scm.order.domain.vo.SalesOrderPastePreviewVO;
import com.xsy.scm.pricing.constant.ScmPriceStatusEnum;
import com.xsy.scm.pricing.constant.ScmUnavailableReasonEnum;
import com.xsy.scm.pricing.domain.vo.ResolvedPriceVO;
import com.xsy.scm.pricing.service.PriceResolver;
import com.xsy.scm.product.dao.ProductSkuOptionDao;
import com.xsy.scm.product.domain.vo.ProductSkuOptionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 订单抽屉里的整单粘贴预览。
 *
 * <p>
 * 只负责把粘贴行匹配到商品规格并解析价格，不创建订单；最终仍走 {@link SalesOrderService} 的建单/改单命令， 不绕过定价、改价原因、幂等和数据范围。匹配规则与送货单导入保持一致：商品名称 + 规格 + 单位，
 * 规格为空时要求该名称只对应一个规格。
 */
@Service
@RequiredArgsConstructor
public class SalesOrderPasteService {
    private static final String DECIMAL_PATTERN = "[0-9]{1,14}(\\.[0-9]{1,4})?";
    private static final int MAX_ROWS = 100;

    private final CustomerDao customerDao;
    private final ProductSkuOptionDao productSkuOptionDao;
    private final PriceResolver priceResolver;

    public SalesOrderPastePreviewVO preview(SalesOrderPasteForm form, boolean priceOverrideAllowed) {
        var result = new SalesOrderPastePreviewVO();
        var rows = form.getRows() == null ? List.<SalesOrderPasteForm.Row>of() : form.getRows();
        if (rows.size() > MAX_ROWS) {
            rows = rows.subList(0, MAX_ROWS);
        }
        result.setTotalRows(rows.size());

        var customer = form.getCustomerId() == null ? null : customerDao.selectById(form.getCustomerId());
        if (customer == null) {
            var row = new SalesOrderPastePreviewRowVO();
            row.getErrors().add(new SalesOrderImportErrorVO(0, null, "客户", "CUSTOMER_NOT_FOUND", "客户不存在，请重新选择"));
            result.getRows().add(row);
            result.setErrorRows(1);
            return result;
        }
        if (!ScmCustomerStatusEnum.valueOf(customer.getStatus()).tradable()) {
            var row = new SalesOrderPastePreviewRowVO();
            row.getErrors()
                .add(new SalesOrderImportErrorVO(0, customer.getName(), "客户", "CUSTOMER_NOT_TRADABLE", "客户状态不可交易"));
            result.getRows().add(row);
            result.setErrorRows(1);
            return result;
        }

        var skuByName = loadSkus(rows);
        var previewRows = new ArrayList<SalesOrderPastePreviewRowVO>();
        var matchedSkus = new ArrayList<ProductSkuOptionVO>();
        var seenSkuIds = new HashSet<Long>();
        for (int index = 0; index < rows.size(); index++) {
            var source = rows.get(index);
            var row = new SalesOrderPastePreviewRowVO();
            row.setRowNumber(index + 1);
            row.setProductName(trim(source.getProductName()));
            row.setSpecName(trim(source.getSpecName()));
            row.setUnit(trim(source.getUnit()));
            row.setQuantity(trim(source.getQuantity()));
            row.setRawUnitPrice(trim(source.getUnitPrice()));
            row.setRemark(trim(source.getRemark()));
            row.setManualPrice(row.getRawUnitPrice() != null);
            validateInput(row);
            matchSku(row, skuByName, seenSkuIds);
            if (row.getSkuId() != null) {
                var sku = findSku(skuByName, row.getSkuId());
                if (sku != null) {
                    matchedSkus.add(sku);
                }
            }
            previewRows.add(row);
        }

        Map<Long, ResolvedPriceVO> prices = Map.of();
        if (!matchedSkus.isEmpty()) {
            var ids = matchedSkus.stream().map(ProductSkuOptionVO::getSkuId).distinct().toList();
            try {
                prices = priceResolver.resolve(customer.getId(), ids, OffsetDateTime.now()).stream()
                    .collect(Collectors.toMap(ResolvedPriceVO::getSkuId, Function.identity()));
            } catch (ScmBusinessException exception) {
                for (var row : previewRows) {
                    if (row.getSkuId() != null) {
                        addError(row, "商品名称", String.valueOf(exception.getErrorCode().getCode()),
                            exception.getErrorCode().getMsg());
                    }
                }
            }
        }
        for (var row : previewRows) {
            if (row.getSkuId() != null) {
                applyPrice(row, prices.get(row.getSkuId()), priceOverrideAllowed);
            }
            row.setMatched(row.getErrors().isEmpty());
            if (!row.getErrors().isEmpty()) {
                result.setErrorRows(result.getErrorRows() + 1);
            }
            result.getRows().add(row);
        }
        result.setMatchedRows(result.getTotalRows() - result.getErrorRows());
        return result;
    }

    private Map<String, List<ProductSkuOptionVO>> loadSkus(List<SalesOrderPasteForm.Row> rows) {
        var names = rows.stream().map(SalesOrderPasteForm.Row::getProductName).map(this::trim).filter(Objects::nonNull)
            .distinct().toList();
        if (names.isEmpty()) {
            return Map.of();
        }
        return productSkuOptionDao.selectByProductNames(names).stream()
            .collect(Collectors.groupingBy(sku -> trim(sku.getProductName()), LinkedHashMap::new, Collectors.toList()));
    }

    private ProductSkuOptionVO findSku(Map<String, List<ProductSkuOptionVO>> skuByName, Long skuId) {
        return skuByName.values().stream().flatMap(List::stream).filter(sku -> skuId.equals(sku.getSkuId())).findFirst()
            .orElse(null);
    }

    private void validateInput(SalesOrderPastePreviewRowVO row) {
        if (row.getProductName() == null) {
            addError(row, "商品名称", "REQUIRED", "商品名称不能为空");
        }
        if (row.getQuantity() == null) {
            addError(row, "数量", "REQUIRED", "数量不能为空");
        } else if (!row.getQuantity().matches(DECIMAL_PATTERN) || new BigDecimal(row.getQuantity()).signum() <= 0) {
            addError(row, "数量", "DECIMAL_INVALID", "数量必须是大于零的四位以内小数");
        }
        if (row.getRawUnitPrice() != null && (!row.getRawUnitPrice().matches(DECIMAL_PATTERN)
            || new BigDecimal(row.getRawUnitPrice()).signum() < 0)) {
            addError(row, "销售单价", "DECIMAL_INVALID", "销售单价必须是非负的四位以内小数");
        }
        if (row.getRemark() != null && row.getRemark().length() > 500) {
            addError(row, "备注", "TOO_LONG", "备注不能超过500个字符");
        }
    }

    private void matchSku(SalesOrderPastePreviewRowVO row, Map<String, List<ProductSkuOptionVO>> skuByName,
        HashSet<Long> seenSkuIds) {
        if (row.getProductName() == null) {
            return;
        }
        var candidates = new ArrayList<>(skuByName.getOrDefault(row.getProductName(), List.of()));
        if (row.getSpecName() != null) {
            candidates.removeIf(sku -> !row.getSpecName().equals(trim(sku.getSpecName())));
        }
        if (candidates.isEmpty()) {
            addError(row, row.getSpecName() == null ? "商品名称" : "规格", ScmUnavailableReasonEnum.SKU_NOT_FOUND.name(),
                row.getSpecName() == null ? "商品名称不存在" : "商品名称与规格不匹配");
            return;
        }
        if (candidates.size() > 1) {
            addError(row, "规格", "SKU_AMBIGUOUS", "匹配到多个商品规格，请补充规格");
            return;
        }
        var sku = candidates.getFirst();
        if (row.getUnit() != null && !row.getUnit().equals(trim(sku.getSaleUnit()))) {
            addError(row, "单位", "UNIT_MISMATCH", "单位应为“" + sku.getSaleUnit() + "”");
            return;
        }
        if (!seenSkuIds.add(sku.getSkuId())) {
            addError(row, "商品名称", "SKU_DUPLICATE", "同一订单内商品规格不能重复");
            return;
        }
        row.setSkuId(sku.getSkuId());
        row.setSkuCode(sku.getSkuCode());
        row.setMatchedProductName(sku.getProductName());
        row.setMatchedSpecName(sku.getSpecName());
        row.setSaleUnit(sku.getSaleUnit());
        row.setProductType(sku.getProductType());
    }

    private void applyPrice(SalesOrderPastePreviewRowVO row, ResolvedPriceVO price, boolean priceOverrideAllowed) {
        if (price == null || !price.isSellable()) {
            addError(row, "商品名称", "SKU_NOT_SELLABLE", "商品已下架、分类停用或对该客户不可见");
            return;
        }
        row.setResolvedUnitPrice(price.getUnitPrice());
        row.setPriceSource(price.getPriceSource() == null ? null : price.getPriceSource().name());
        if (row.getRawUnitPrice() != null) {
            if (!priceOverrideAllowed) {
                addError(row, "销售单价", "PRICE_OVERRIDE_FORBIDDEN", "当前账号没有订单改价权限，不能按粘贴单价下单");
            }
        } else if (price.getPriceStatus() == ScmPriceStatusEnum.UNPRICED) {
            addError(row, "销售单价", ScmPriceStatusEnum.UNPRICED.name(), "商品没有可用价格，请填写销售单价或先维护价格");
        }
    }

    private void addError(SalesOrderPastePreviewRowVO row, String column, String code, String message) {
        row.getErrors()
            .add(new SalesOrderImportErrorVO(row.getRowNumber(), row.getMatchedProductName(), column, code, message));
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
