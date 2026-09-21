package net.lab1024.sa.admin.module.scm.order.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.pricing.service.PriceResolver;
import net.lab1024.sa.admin.module.scm.pricing.constant.ScmPriceStatusEnum;
import net.lab1024.sa.admin.module.scm.customer.dao.CustomerDao;
import net.lab1024.sa.admin.module.scm.customer.domain.entity.CustomerEntity;
import net.lab1024.sa.admin.module.scm.order.domain.dto.SalesOrderImportRow;
import net.lab1024.sa.admin.module.scm.order.domain.form.OrderAddressForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.SalesOrderAddForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.SalesOrderItemForm;
import net.lab1024.sa.admin.module.scm.order.domain.vo.SalesOrderImportErrorVO;
import net.lab1024.sa.admin.module.scm.order.domain.vo.SalesOrderImportResultVO;
import net.lab1024.sa.admin.module.scm.product.dao.ProductSkuOptionDao;
import net.lab1024.sa.admin.module.scm.product.domain.vo.ProductSkuOptionVO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellReference;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SalesOrderImportService {
    public static final String TEMPLATE_VERSION = "1.0";
    private static final int MAX_ROWS = 2000;
    private static final int MAX_ORDERS = 200;
    private static final int MAX_ITEMS_PER_ORDER = 100;
    private static final int MAX_ERRORS = 1000;
    private static final List<String> HEADERS = List.of("模板版本", "导入订单标识", "客户编码", "收货人", "联系电话", "收货地址",
            "期望配送时间", "SKU编码", "下单数量", "人工单价", "改价原因", "订单备注");
    private static final List<BiConsumer<SalesOrderImportRow, String>> SETTERS = List.of(
            SalesOrderImportRow::setTemplateVersion, SalesOrderImportRow::setOrderKey, SalesOrderImportRow::setCustomerCode,
            SalesOrderImportRow::setReceiverName, SalesOrderImportRow::setReceiverPhone, SalesOrderImportRow::setAddress,
            SalesOrderImportRow::setExpectDeliveryTime, SalesOrderImportRow::setSkuCode, SalesOrderImportRow::setOrderedQuantity,
            SalesOrderImportRow::setUnitPrice, SalesOrderImportRow::setOverrideReason, SalesOrderImportRow::setRemark);

    private final CustomerDao customers;
    private final ProductSkuOptionDao skus;
    private final SalesOrderService orders;
    private final PriceResolver prices;

    public SalesOrderImportResultVO importFile(MultipartFile file, String key, boolean priceOverrideAllowed) throws Exception {
        var result = new SalesOrderImportResultVO();
        if (file.getSize() > 5L * 1024 * 1024) {
            addError(result, 0, null, "文件", "FILE_SIZE", "导入文件不能超过 5 MiB");
            return result;
        }
        var bytes = file.getBytes();
        var fileHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        var rows = readRows(bytes, result);
        if (result.getTotalErrors() > 0) return result;
        var assembled = assemble(rows, priceOverrideAllowed);
        if (assembled.result().getTotalErrors() > 0) return assembled.result();
        try {
            return orders.importOrders(assembled.forms(), fileHash, key, rows.size());
        } catch (SalesOrderService.ImportOrderException exception) {
            // importOrders is a separate proxied bean: its entire transaction has rolled back here.
            var group = assembled.groups().get(exception.getOrderIndex());
            var cause = exception.getCause();
            var code = cause instanceof ScmBusinessException business ? String.valueOf(business.getErrorCode().getCode()) : "WRITE_CONFLICT";
            var message = cause instanceof ScmBusinessException business ? business.getErrorCode().getMsg() : "订单数据冲突或金额超出范围，请检查后重试";
            for (var indexed : group)
                addError(assembled.result(), indexed.rowNumber(), indexed.row().getOrderKey(), "订单", code, message + "；整批已回滚");
            return assembled.result();
        }
    }

    private List<SalesOrderImportRow> readRows(byte[] bytes, SalesOrderImportResultVO result) {
        var rows = new ArrayList<SalesOrderImportRow>();
        var formatter = new DataFormatter(java.util.Locale.ROOT);
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            if (workbook.getNumberOfSheets() != 1) {
                addError(result, 0, null, "文件", "SHEET_COUNT", "请保留模板中的一个工作表，避免遗漏订单");
                return rows;
            }
            var sheet = workbook.getSheetAt(0);
            var header = sheet.getRow(0);
            for (int column = 0; column < HEADERS.size(); column++) {
                if (header == null || !HEADERS.get(column).equals(trim(formatter.formatCellValue(header.getCell(column))))) {
                    addError(result, 1, null, CellReference.convertNumToColString(column), "HEADER_INVALID", "表头应为“" + HEADERS.get(column) + "”，请使用最新模板");
                }
            }
            if (result.getTotalErrors() > 0) return rows;
            for (var excelRow : sheet) {
                if (excelRow.getRowNum() == 0) continue;
                var row = new SalesOrderImportRow();
                row.setRowNumber(excelRow.getRowNum() + 1);
                boolean hasData = false;
                for (var cell : excelRow) {
                    var value = trim(formatter.formatCellValue(cell));
                    if (value == null) continue;
                    hasData = true;
                    var column = cell.getColumnIndex();
                    var name = column < HEADERS.size() ? HEADERS.get(column) : CellReference.convertNumToColString(column);
                    if (cell.getCellType() == CellType.FORMULA || cell.getCellType() == CellType.ERROR) {
                        addError(result, row.getRowNumber(), null, name, "CELL_INVALID", "不能使用公式或错误单元格，请填写实际值");
                    } else if (column >= HEADERS.size()) {
                        addError(result, row.getRowNumber(), null, name, "COLUMN_UNEXPECTED", "模板之外的列不能填写订单数据");
                    } else {
                        // Read the stored number, not its rounded display format.
                        if (cell.getCellType() == CellType.NUMERIC)
                            value = org.apache.poi.ss.util.NumberToTextConverter.toText(cell.getNumericCellValue());
                        SETTERS.get(column).accept(row, value);
                    }
                }
                if (hasData) rows.add(row);
                if (rows.size() > MAX_ROWS) {
                    addError(result, row.getRowNumber(), row.getOrderKey(), "文件", "ROW_LIMIT", "数据行不能超过 " + MAX_ROWS + " 行");
                    break;
                }
            }
        } catch (Exception exception) {
            addError(result, 0, null, "文件", "FILE_INVALID", "Excel 文件无法读取，请使用最新模板");
        }
        result.setTotalRows(rows.size());
        return rows;
    }

    private Assembly assemble(List<SalesOrderImportRow> rows, boolean priceOverrideAllowed) {
        var result = new SalesOrderImportResultVO();
        result.setTotalRows(rows.size());
        if (rows.isEmpty()) addError(result, 0, null, "文件", "FILE_EMPTY", "导入文件没有数据行");
        if (rows.size() > MAX_ROWS)
            addError(result, 0, null, "文件", "ROW_LIMIT", "数据行不能超过 " + MAX_ROWS + " 行");

        var customerCodes = rows.stream().map(SalesOrderImportRow::getCustomerCode).map(this::trim).filter(Objects::nonNull).distinct().toList();
        var skuCodes = rows.stream().map(SalesOrderImportRow::getSkuCode).map(this::trim).filter(Objects::nonNull).distinct().toList();
        var customerMap = customerCodes.isEmpty() ? Map.<String, CustomerEntity>of() : customers.selectActiveByCodes(customerCodes).stream().collect(Collectors.toMap(CustomerEntity::getCustomerCode, Function.identity()));
        var skuMap = skuCodes.isEmpty() ? Map.<String, ProductSkuOptionVO>of() : skus.selectByCodes(skuCodes).stream().collect(Collectors.toMap(ProductSkuOptionVO::getSkuCode, Function.identity()));

        var groups = new LinkedHashMap<String, List<IndexedRow>>();
        for (int index = 0; index < rows.size(); index++) {
            var row = rows.get(index);
            var rowNumber = row.getRowNumber();
            var orderKey = trim(row.getOrderKey());
            validateRow(row, rowNumber, orderKey, customerMap, skuMap, priceOverrideAllowed, result);
            if (orderKey != null)
                groups.computeIfAbsent(orderKey, ignored -> new ArrayList<>()).add(new IndexedRow(rowNumber, row));
        }
        result.setTotalOrders(groups.size());
        if (groups.size() > MAX_ORDERS)
            addError(result, 0, null, "导入订单标识", "ORDER_LIMIT", "订单数不能超过 " + MAX_ORDERS + " 张");

        var forms = new ArrayList<SalesOrderAddForm>();
        for (var entry : groups.entrySet()) {
            var group = entry.getValue();
            var first = group.getFirst();
            if (group.size() > MAX_ITEMS_PER_ORDER)
                addError(result, first.rowNumber(), entry.getKey(), "导入订单标识", "ITEM_LIMIT", "单张订单明细不能超过 " + MAX_ITEMS_PER_ORDER + " 行");
            validateGroup(entry.getKey(), group, result);
            validatePrices(entry.getKey(), group, customerMap, skuMap, result);
            if (result.getTotalErrors() == 0) forms.add(toForm(group, customerMap, skuMap));
        }
        return new Assembly(result, forms, new ArrayList<>(groups.values()));
    }

    private void validatePrices(String orderKey, List<IndexedRow> rows, Map<String, CustomerEntity> customerMap,
                                Map<String, ProductSkuOptionVO> skuMap, SalesOrderImportResultVO result) {
        var customer = customerMap.get(trim(rows.getFirst().row().getCustomerCode()));
        if (customer == null || !net.lab1024.sa.admin.module.scm.common.constant.ScmCustomerStatusEnum.valueOf(customer.getStatus()).tradable())
            return;
        var ids = rows.stream().map(x -> skuMap.get(trim(x.row().getSkuCode()))).filter(Objects::nonNull).map(ProductSkuOptionVO::getSkuId).distinct().toList();
        try {
            var resolved = prices.resolve(customer.getId(), ids, OffsetDateTime.now()).stream().collect(Collectors.toMap(x -> x.getSkuId(), Function.identity()));
            var total = BigDecimal.ZERO;
            for (var indexed : rows) {
                var row = indexed.row();
                var sku = skuMap.get(trim(row.getSkuCode()));
                if (sku == null) continue;
                var price = resolved.get(sku.getSkuId());
                if (price == null || !price.isSellable()) {
                    addError(result, indexed.rowNumber(), orderKey, "SKU编码", "SKU_NOT_SELLABLE", "商品已下架、分类停用或对该客户不可见");
                    continue;
                }
                if (trim(row.getUnitPrice()) == null && price.getPriceStatus() == ScmPriceStatusEnum.UNPRICED) {
                    addError(result, indexed.rowNumber(), orderKey, "人工单价", "UNPRICED", "商品没有可用价格，请维护价格或由有改价权限的人员填写人工单价及原因");
                    continue;
                }
                var quantity = trim(row.getOrderedQuantity());
                var manualPrice = trim(row.getUnitPrice());
                if (quantity == null || !quantity.matches("[0-9]{1,14}(\\.[0-9]{1,4})?")) continue;
                if (manualPrice != null && !manualPrice.matches("[0-9]{1,14}(\\.[0-9]{1,4})?")) continue;
                var unitPrice = manualPrice == null ? price.getUnitPrice() : new BigDecimal(manualPrice);
                var amount = unitPrice.multiply(new BigDecimal(quantity)).setScale(4, RoundingMode.HALF_UP);
                total = total.add(amount);
                if (amount.precision() > 18 || total.precision() > 18)
                    addError(result, indexed.rowNumber(), orderKey, "下单数量", "AMOUNT_OVERFLOW", "数量与单价计算的明细金额或订单总额超出范围");
            }
        } catch (ScmBusinessException exception) {
            for (var row : rows)
                addError(result, row.rowNumber(), orderKey, "客户编码", String.valueOf(exception.getErrorCode().getCode()), exception.getErrorCode().getMsg());
        }
    }

    private void validateRow(SalesOrderImportRow row, int rowNumber, String orderKey, Map<String, CustomerEntity> customerMap,
                             Map<String, ProductSkuOptionVO> skuMap, boolean priceOverrideAllowed, SalesOrderImportResultVO result) {
        required(result, rowNumber, orderKey, "模板版本", row.getTemplateVersion());
        if (trim(row.getTemplateVersion()) != null && !TEMPLATE_VERSION.equals(trim(row.getTemplateVersion())))
            addError(result, rowNumber, orderKey, "模板版本", "TEMPLATE_VERSION", "模板版本不受支持，请重新下载模板");
        required(result, rowNumber, orderKey, "导入订单标识", row.getOrderKey());
        required(result, rowNumber, orderKey, "客户编码", row.getCustomerCode());
        required(result, rowNumber, orderKey, "收货人", row.getReceiverName());
        required(result, rowNumber, orderKey, "联系电话", row.getReceiverPhone());
        required(result, rowNumber, orderKey, "收货地址", row.getAddress());
        required(result, rowNumber, orderKey, "SKU编码", row.getSkuCode());
        required(result, rowNumber, orderKey, "下单数量", row.getOrderedQuantity());
        length(result, rowNumber, orderKey, "收货人", row.getReceiverName(), 100);
        length(result, rowNumber, orderKey, "联系电话", row.getReceiverPhone(), 32);
        length(result, rowNumber, orderKey, "收货地址", row.getAddress(), 500);
        length(result, rowNumber, orderKey, "订单备注", row.getRemark(), 500);
        length(result, rowNumber, orderKey, "改价原因", row.getOverrideReason(), 500);

        var customerCode = trim(row.getCustomerCode());
        if (customerCode != null && !customerMap.containsKey(customerCode))
            addError(result, rowNumber, orderKey, "客户编码", "CUSTOMER_NOT_FOUND", "客户编码不存在");
        else if (customerCode != null && !net.lab1024.sa.admin.module.scm.common.constant.ScmCustomerStatusEnum.valueOf(customerMap.get(customerCode).getStatus()).tradable())
            addError(result, rowNumber, orderKey, "客户编码", "CUSTOMER_NOT_TRADABLE", "客户状态不可交易");
        var skuCode = trim(row.getSkuCode());
        if (skuCode != null && !skuMap.containsKey(skuCode))
            addError(result, rowNumber, orderKey, "SKU编码", "SKU_NOT_FOUND", "SKU 编码不存在");
        decimal(result, rowNumber, orderKey, "下单数量", row.getOrderedQuantity(), true);
        if (trim(row.getUnitPrice()) != null) {
            decimal(result, rowNumber, orderKey, "人工单价", row.getUnitPrice(), false);
            if (trim(row.getOverrideReason()) == null)
                addError(result, rowNumber, orderKey, "改价原因", "OVERRIDE_REASON_REQUIRED", "填写人工单价时必须填写改价原因");
            if (!priceOverrideAllowed)
                addError(result, rowNumber, orderKey, "人工单价", "PRICE_OVERRIDE_FORBIDDEN", "当前账号没有订单改价权限");
        } else if (trim(row.getOverrideReason()) != null) {
            addError(result, rowNumber, orderKey, "人工单价", "OVERRIDE_PRICE_REQUIRED", "填写改价原因时必须填写人工单价");
        }
        if (trim(row.getExpectDeliveryTime()) != null) try {
            OffsetDateTime.parse(trim(row.getExpectDeliveryTime()));
        } catch (DateTimeParseException exception) {
            addError(result, rowNumber, orderKey, "期望配送时间", "DATE_INVALID", "期望配送时间必须是带时区的 ISO-8601 格式");
        }
    }

    private void validateGroup(String orderKey, List<IndexedRow> rows, SalesOrderImportResultVO result) {
        var first = rows.getFirst().row();
        var skuCodes = new java.util.HashSet<String>();
        for (var indexed : rows) {
            var row = indexed.row();
            same(result, indexed.rowNumber(), orderKey, "客户编码", first.getCustomerCode(), row.getCustomerCode());
            same(result, indexed.rowNumber(), orderKey, "收货人", first.getReceiverName(), row.getReceiverName());
            same(result, indexed.rowNumber(), orderKey, "联系电话", first.getReceiverPhone(), row.getReceiverPhone());
            same(result, indexed.rowNumber(), orderKey, "收货地址", first.getAddress(), row.getAddress());
            same(result, indexed.rowNumber(), orderKey, "期望配送时间", first.getExpectDeliveryTime(), row.getExpectDeliveryTime());
            same(result, indexed.rowNumber(), orderKey, "订单备注", first.getRemark(), row.getRemark());
            var skuCode = trim(row.getSkuCode());
            if (skuCode != null && !skuCodes.add(skuCode))
                addError(result, indexed.rowNumber(), orderKey, "SKU编码", "SKU_DUPLICATE", "同一订单内 SKU 不能重复");
        }
    }

    private SalesOrderAddForm toForm(List<IndexedRow> rows, Map<String, CustomerEntity> customerMap, Map<String, ProductSkuOptionVO> skuMap) {
        var first = rows.getFirst().row();
        var form = new SalesOrderAddForm();
        form.setCustomerId(customerMap.get(trim(first.getCustomerCode())).getId());
        form.setOrderSource("IMPORT");
        form.setRemark(trim(first.getRemark()));
        if (trim(first.getExpectDeliveryTime()) != null)
            form.setExpectDeliveryTime(OffsetDateTime.parse(trim(first.getExpectDeliveryTime())));
        var address = new OrderAddressForm();
        address.setReceiverName(trim(first.getReceiverName()));
        address.setReceiverPhone(trim(first.getReceiverPhone()));
        address.setAddress(trim(first.getAddress()));
        form.setAddress(address);
        var items = new ArrayList<SalesOrderItemForm>();
        for (int index = 0; index < rows.size(); index++) {
            var row = rows.get(index).row();
            var item = new SalesOrderItemForm();
            item.setSkuId(skuMap.get(trim(row.getSkuCode())).getSkuId());
            item.setOrderedQuantity(fixed(row.getOrderedQuantity()));
            item.setSortOrder(index);
            var manual = trim(row.getUnitPrice()) != null;
            item.setManualPriceOverride(manual);
            item.setUnitPrice(manual ? fixed(row.getUnitPrice()) : null);
            item.setOverrideReason(manual ? trim(row.getOverrideReason()) : null);
            items.add(item);
        }
        form.setItems(items);
        return form;
    }

    private void required(SalesOrderImportResultVO result, int row, String key, String column, String value) {
        if (trim(value) == null) addError(result, row, key, column, "REQUIRED", column + "不能为空");
    }

    private void length(SalesOrderImportResultVO result, int row, String key, String column, String value, int max) {
        if (trim(value) != null && trim(value).length() > max)
            addError(result, row, key, column, "TOO_LONG", column + "不能超过 " + max + " 个字符");
    }

    private void decimal(SalesOrderImportResultVO result, int row, String key, String column, String value, boolean positive) {
        if (trim(value) == null) return;
        try {
            if (!trim(value).matches("[0-9]{1,14}(\\.[0-9]{1,4})?")) throw new NumberFormatException();
            var number = new BigDecimal(trim(value));
            if (positive ? number.signum() <= 0 : number.signum() < 0) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            addError(result, row, key, column, "DECIMAL_INVALID", column + "必须是" + (positive ? "大于零的" : "非负") + "四位以内小数");
        }
    }

    private void same(SalesOrderImportResultVO result, int row, String key, String column, String expected, String actual) {
        if (!Objects.equals(trim(expected), trim(actual)))
            addError(result, row, key, column, "HEADER_CONFLICT", "同一导入订单的" + column + "必须一致");
    }

    private String fixed(String value) {
        return new BigDecimal(trim(value)).setScale(4, RoundingMode.UNNECESSARY).toPlainString();
    }

    private String trim(String value) {
        if (value == null) return null;
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void addError(SalesOrderImportResultVO result, int row, String key, String column, String code, String message) {
        result.setTotalErrors(result.getTotalErrors() + 1);
        if (result.getErrors().size() < MAX_ERRORS)
            result.getErrors().add(new SalesOrderImportErrorVO(row, key, column, code, message));
    }

    private record IndexedRow(int rowNumber, SalesOrderImportRow row) {
    }

    private record Assembly(SalesOrderImportResultVO result, List<SalesOrderAddForm> forms,
                            List<List<IndexedRow>> groups) {
    }
}
