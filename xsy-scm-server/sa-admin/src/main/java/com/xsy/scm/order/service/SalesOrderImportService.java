package com.xsy.scm.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.xsy.scm.common.constant.ScmCustomerStatusEnum;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.customer.dao.CustomerDao;
import com.xsy.scm.customer.domain.entity.CustomerEntity;
import com.xsy.scm.order.constant.ScmOrderSourceEnum;
import com.xsy.scm.order.domain.form.OrderAddressForm;
import com.xsy.scm.order.domain.form.SalesOrderAddForm;
import com.xsy.scm.order.domain.form.SalesOrderItemForm;
import com.xsy.scm.order.domain.vo.SalesOrderImportErrorVO;
import com.xsy.scm.order.domain.vo.SalesOrderImportResultVO;
import com.xsy.scm.pricing.constant.ScmPriceStatusEnum;
import com.xsy.scm.pricing.constant.ScmUnavailableReasonEnum;
import com.xsy.scm.pricing.domain.vo.ResolvedPriceVO;
import com.xsy.scm.pricing.service.PriceResolver;
import com.xsy.scm.product.dao.ProductSkuOptionDao;
import com.xsy.scm.product.domain.vo.ProductSkuOptionVO;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.ss.util.NumberToTextConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 送货单版式的销售订单导入：一张送货单生成一张订单。
 *
 * <p>
 * 表头位于「收货单位 / 日期」下方的第一行，列固定为 序号、商品名称、单位、规格、实数量、销售单价、销售金额、备注； 合计行与签字栏不参与导入。客户按送货单上的收货单位名称匹配，商品按商品名称 + 规格 + 单位匹配，
 * 实数量作为下单数量。送货单单价属于人工定价：导入账号必须有订单改价权限，服务端统一记录改价原因“送货单导入”。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesOrderImportService {
    private static final int MAX_ROWS = 2000;
    private static final int MAX_ITEMS_PER_ORDER = 100;
    private static final int MAX_ERRORS = 1000;
    private static final String IMPORT_PRICE_REASON = "送货单导入";
    private static final String DEFAULT_ORDER_KEY = "送货单";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final String DECIMAL_PATTERN = "[0-9]{1,14}(\\.[0-9]{1,4})?";
    private static final List<String> HEADERS = List.of("序号", "商品名称", "单位", "规格", "实数量", "销售单价", "销售金额", "备注");
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("yyyy/M/d"), DateTimeFormatter.ofPattern("yyyy.M.d"),
        DateTimeFormatter.ofPattern("M/d/yyyy"), DateTimeFormatter.ofPattern("MM-dd-yy"),
        DateTimeFormatter.ofPattern("yyyy年M月d日"));

    private final CustomerDao customerDao;
    private final ProductSkuOptionDao productSkuOptionDao;
    private final SalesOrderService salesOrderService;
    private final PriceResolver priceResolver;

    public SalesOrderImportResultVO importFile(MultipartFile file, String key, boolean priceOverrideAllowed)
        throws Exception {
        var result = new SalesOrderImportResultVO();
        if (file.getSize() > 5L * 1024 * 1024) {
            addError(result, 0, null, "文件", "FILE_SIZE", "导入文件不能超过 5 MiB");
            return result;
        }
        var bytes = file.getBytes();
        var fileHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        var note = readNote(bytes, result);
        if (result.getTotalErrors() > 0)
            return result;
        var assembled = assemble(note, priceOverrideAllowed);
        if (assembled.result().getTotalErrors() > 0)
            return assembled.result();
        try {
            return salesOrderService.importOrders(assembled.forms(), fileHash, key, note.lines().size());
        } catch (SalesOrderService.ImportOrderException exception) {
            // importOrders 是独立的代理 Bean，到这里它的整个事务已回滚。
            var group = assembled.groups().get(exception.getOrderIndex());
            var cause = exception.getCause();
            var code = cause instanceof ScmBusinessException business
                ? String.valueOf(business.getErrorCode().getCode())
                : "WRITE_CONFLICT";
            var message = cause instanceof ScmBusinessException business
                ? business.getErrorCode().getMsg()
                : "订单数据冲突或金额超出范围，请检查后重试";
            for (var indexed : group)
                addError(assembled.result(), indexed.rowNumber(), orderKey(note), "订单", code, message + "；整批已回滚");
            return assembled.result();
        }
    }

    private DeliveryNote readNote(byte[] bytes, SalesOrderImportResultVO result) {
        var formatter = new DataFormatter(java.util.Locale.ROOT);
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            if (workbook.getNumberOfSheets() != 1) {
                addError(result, 0, null, "文件", "SHEET_COUNT", "请保留模板中的一个工作表，避免遗漏订单");
                return new DeliveryNote(null, 0, null, 0, List.of());
            }
            var sheet = workbook.getSheetAt(0);
            var headerRowIndex = findHeaderRow(sheet, formatter);
            if (headerRowIndex < 0) {
                addError(result, 1, null, "表头", "HEADER_INVALID",
                    "未找到送货单表头，应包含：" + String.join("、", HEADERS) + "；请使用最新模板");
                return new DeliveryNote(null, 0, null, 0, List.of());
            }
            validateHeader(sheet.getRow(headerRowIndex), formatter, headerRowIndex, result);
            if (result.getTotalErrors() > 0)
                return new DeliveryNote(null, 0, null, 0, List.of());

            var meta = readMeta(sheet, headerRowIndex, formatter, result);
            var lines = readLines(sheet, headerRowIndex, formatter, result);
            result.setTotalRows(lines.size());
            if (lines.isEmpty())
                addError(result, headerRowIndex + 2, null, "商品名称", "FILE_EMPTY", "送货单没有商品明细");
            if (lines.size() > MAX_ROWS)
                addError(result, lines.get(MAX_ROWS).rowNumber(), null, "商品名称", "ROW_LIMIT",
                    "数据行不能超过 " + MAX_ROWS + " 行");
            // 备注属于商品行（切段、去皮、分袋），逐行保留；不汇总成整单备注，否则打印时分不清是哪件商品的要求
            return new DeliveryNote(meta.customerName(), meta.customerRow(), meta.deliveryDate(), meta.dateRow(),
                lines);
        } catch (Exception exception) {
            // 对调用方仍返回 FILE_INVALID，但服务端要留下原因：解析异常若只改写成「请使用最新模板」，
            // 用户会反复重导模板，运维也拿不到线索。
            log.error("订单导入文件解析失败，整批按 FILE_INVALID 拒绝", exception);
            addError(result, 0, null, "文件", "FILE_INVALID", "Excel 文件无法读取，请使用最新送货单模板");
            return new DeliveryNote(null, 0, null, 0, List.of());
        }
    }

    private int findHeaderRow(Sheet sheet, DataFormatter formatter) {
        int last = Math.min(sheet.getLastRowNum(), 20);
        for (int index = 0; index <= last; index++) {
            var row = sheet.getRow(index);
            if (row == null)
                continue;
            if (!"商品名称".equals(cellText(row.getCell(1), formatter)) || !"单位".equals(cellText(row.getCell(2), formatter))
                || !"规格".equals(cellText(row.getCell(3), formatter))
                || !"实数量".equals(cellText(row.getCell(4), formatter))
                || !"销售单价".equals(cellText(row.getCell(5), formatter))
                || !"销售金额".equals(cellText(row.getCell(6), formatter)))
                continue;
            return index;
        }
        return -1;
    }

    private void validateHeader(Row header, DataFormatter formatter, int headerRowIndex,
        SalesOrderImportResultVO result) {
        for (int column = 0; column < HEADERS.size(); column++) {
            var actual = header == null ? null : cellText(header.getCell(column), formatter);
            if (!HEADERS.get(column).equals(actual))
                addError(result, headerRowIndex + 1, null, CellReference.convertNumToColString(column),
                    "HEADER_INVALID", "表头应为“" + HEADERS.get(column) + "”，请使用最新送货单模板");
        }
    }

    private Meta readMeta(Sheet sheet, int headerRowIndex, DataFormatter formatter, SalesOrderImportResultVO result) {
        String customerName = null;
        int customerRow = 0;
        OffsetDateTime deliveryDate = null;
        int dateRow = 0;
        for (int rowIndex = 0; rowIndex < headerRowIndex; rowIndex++) {
            var row = sheet.getRow(rowIndex);
            if (row == null)
                continue;
            for (int column = 0; column < Math.max(row.getLastCellNum(), HEADERS.size()); column++) {
                var cell = row.getCell(column);
                if (cell == null)
                    continue;
                var text = cellText(cell, formatter);
                if (text == null)
                    continue;
                if (text.startsWith("收货单位")) {
                    customerRow = rowIndex + 1;
                    customerName = trim(afterLabel(text, "收货单位"));
                    if (customerName == null)
                        customerName = cellText(row.getCell(column + 1), formatter);
                } else if (text.startsWith("日期")) {
                    dateRow = rowIndex + 1;
                    var inline = trim(afterLabel(text, "日期"));
                    if (inline != null) {
                        deliveryDate = parseDateText(inline, dateRow, result);
                    } else {
                        deliveryDate = readDate(row.getCell(column + 1), formatter, dateRow, result);
                    }
                }
            }
        }
        return new Meta(trim(customerName), customerRow, deliveryDate, dateRow);
    }

    private String afterLabel(String text, String label) {
        var rest = text.substring(label.length()).trim();
        if (rest.startsWith("：") || rest.startsWith(":"))
            rest = rest.substring(1).trim();
        return rest;
    }

    private OffsetDateTime readDate(Cell cell, DataFormatter formatter, int rowNumber,
        SalesOrderImportResultVO result) {
        if (cell == null)
            return null;
        if (cell.getCellType() == CellType.ERROR) {
            addError(result, rowNumber, null, "日期", "CELL_INVALID", "日期不能是错误单元格，请填写实际日期");
            return null;
        }
        if (cell.getCellType() == CellType.FORMULA) {
            addError(result, rowNumber, null, "日期", "CELL_INVALID", "日期不能使用公式，请填写实际日期");
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate().atStartOfDay(ZONE).toOffsetDateTime();
        }
        // 常规格式的数字不能被当成 Excel 序列号：直接按文本解析，避免把 20261027 读成遥远日期。
        return parseDateText(cellText(cell, formatter), rowNumber, result);
    }

    private OffsetDateTime parseDateText(String text, int rowNumber, SalesOrderImportResultVO result) {
        if (text == null)
            return null;
        for (var format : DATE_FORMATS) {
            try {
                return LocalDate.parse(text, format).atStartOfDay(ZONE).toOffsetDateTime();
            } catch (DateTimeParseException ignored) {
                // 继续尝试下一种常见格式
            }
        }
        addError(result, rowNumber, null, "日期", "DATE_INVALID", "日期格式应为 yyyy-MM-dd");
        return null;
    }

    private List<NoteLine> readLines(Sheet sheet, int headerRowIndex, DataFormatter formatter,
        SalesOrderImportResultVO result) {
        var lines = new ArrayList<NoteLine>();
        for (int rowIndex = headerRowIndex + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            var row = sheet.getRow(rowIndex);
            if (row == null)
                continue;
            var first = cellText(row.getCell(0), formatter);
            if (first != null && first.startsWith("合计"))
                break;
            var productName = cellText(row.getCell(1), formatter);
            if (productName != null && productName.contains("销售方"))
                break;
            if (isBlankLine(row, formatter))
                continue;
            var rowNumber = rowIndex + 1;
            validateCellTypes(row, rowNumber, formatter, result);
            lines.add(new NoteLine(rowNumber, productName, cellText(row.getCell(2), formatter),
                cellText(row.getCell(3), formatter), cellText(row.getCell(4), formatter),
                cellText(row.getCell(5), formatter), amountText(row, formatter), cellText(row.getCell(7), formatter)));
            if (lines.size() > MAX_ROWS)
                break;
        }
        return lines;
    }

    private void validateCellTypes(Row row, int rowNumber, DataFormatter formatter, SalesOrderImportResultVO result) {
        for (int column = 0; column < HEADERS.size(); column++) {
            var cell = row.getCell(column);
            if (cell == null)
                continue;
            var name = HEADERS.get(column);
            if (cell.getCellType() == CellType.ERROR) {
                addError(result, rowNumber, null, name, "CELL_INVALID", "不能使用错误单元格，请填写实际值");
            } else if (cell.getCellType() == CellType.FORMULA) {
                // 销售金额常写成「实数量×销售单价」，允许公式但必须已有可用的缓存数值。
                if (column != 6 || cell.getCachedFormulaResultType() != CellType.NUMERIC)
                    addError(result, rowNumber, null, name, "CELL_INVALID", "该列不能使用公式，请填写实际值");
            }
        }
        for (int column = HEADERS.size(); column < Math.max(row.getLastCellNum(), HEADERS.size()); column++) {
            if (cellText(row.getCell(column), formatter) != null)
                addError(result, rowNumber, null, CellReference.convertNumToColString(column), "COLUMN_UNEXPECTED",
                    "模板之外的列不能填写订单数据");
        }
    }

    private boolean isBlankLine(Row row, DataFormatter formatter) {
        for (int column = 1; column < HEADERS.size(); column++)
            if (cellText(row.getCell(column), formatter) != null)
                return false;
        return true;
    }

    private String amountText(Row row, DataFormatter formatter) {
        var cell = row.getCell(6);
        if (cell == null)
            return null;
        if (cell.getCellType() == CellType.FORMULA)
            return cell.getCachedFormulaResultType() == CellType.NUMERIC
                ? NumberToTextConverter.toText(cell.getNumericCellValue())
                : null;
        return cellText(cell, formatter);
    }

    private Assembly assemble(DeliveryNote note, boolean priceOverrideAllowed) {
        var result = new SalesOrderImportResultVO();
        result.setTotalRows(note.lines().size());
        if (note.lines().isEmpty())
            return new Assembly(result, List.of(), List.of());
        result.setTotalOrders(1);
        var key = orderKey(note);
        if (note.customerName() == null)
            addError(result, Math.max(note.customerRow(), 3), key, "收货单位", "REQUIRED", "收货单位不能为空");
        var customer = findCustomer(note, result);
        var indexedLines = matchSkus(note, result);
        validateLines(note, indexedLines, result);
        if (customer != null) {
            validateAddress(customer, note, result);
            validatePrices(customer, indexedLines, priceOverrideAllowed, note, result);
        }
        if (result.getTotalErrors() > 0)
            return new Assembly(result, List.of(), List.of(groupOf(indexedLines)));
        return new Assembly(result, List.of(toForm(customer, note, indexedLines)), List.of(groupOf(indexedLines)));
    }

    private CustomerEntity findCustomer(DeliveryNote note, SalesOrderImportResultVO result) {
        if (note.customerName() == null)
            return null;
        var key = orderKey(note);
        var matches = customerDao.selectActiveByNames(List.of(note.customerName())).stream()
            .filter(customer -> note.customerName().equals(trim(customer.getName()))).toList();
        if (matches.isEmpty()) {
            addError(result, Math.max(note.customerRow(), 3), key, "收货单位", "CUSTOMER_NOT_FOUND",
                "收货单位对应的客户不存在，请填写客户档案中的客户名称");
            return null;
        }
        if (matches.size() > 1) {
            addError(result, Math.max(note.customerRow(), 3), key, "收货单位", "CUSTOMER_AMBIGUOUS",
                "客户名称存在多条有效档案，请先处理重名后再导入");
            return null;
        }
        var customer = matches.getFirst();
        if (!ScmCustomerStatusEnum.valueOf(customer.getStatus()).tradable()) {
            addError(result, Math.max(note.customerRow(), 3), key, "收货单位", "CUSTOMER_NOT_TRADABLE", "客户状态不可交易");
            return null;
        }
        return customer;
    }

    private List<IndexedLine> matchSkus(DeliveryNote note, SalesOrderImportResultVO result) {
        var key = orderKey(note);
        var names = note.lines().stream().map(NoteLine::productName).filter(Objects::nonNull).distinct().toList();
        var byName = names.isEmpty()
            ? Map.<String, List<ProductSkuOptionVO>>of()
            : productSkuOptionDao.selectByProductNames(names).stream().collect(
                Collectors.groupingBy(sku -> trim(sku.getProductName()), LinkedHashMap::new, Collectors.toList()));
        var seenSkuIds = new HashSet<Long>();
        var indexedLines = new ArrayList<IndexedLine>();
        for (var line : note.lines()) {
            var rowNumber = line.rowNumber();
            if (line.productName() == null) {
                addError(result, rowNumber, key, "商品名称", "REQUIRED", "商品名称不能为空");
                indexedLines.add(new IndexedLine(rowNumber, line, null));
                continue;
            }
            var candidates = byName.getOrDefault(line.productName(), List.of());
            if (line.specName() != null)
                candidates = candidates.stream().filter(sku -> line.specName().equals(trim(sku.getSpecName())))
                    .toList();
            if (candidates.isEmpty()) {
                addError(result, rowNumber, key, line.specName() == null ? "商品名称" : "规格",
                    ScmUnavailableReasonEnum.SKU_NOT_FOUND.name(), line.specName() == null ? "商品名称不存在" : "商品名称与规格不匹配");
                indexedLines.add(new IndexedLine(rowNumber, line, null));
                continue;
            }
            if (candidates.size() > 1) {
                addError(result, rowNumber, key, "规格", "SKU_AMBIGUOUS", "匹配到多个商品规格，请补充规格");
                indexedLines.add(new IndexedLine(rowNumber, line, null));
                continue;
            }
            var sku = candidates.getFirst();
            if (line.unit() != null && !line.unit().equals(sku.getSaleUnit())) {
                addError(result, rowNumber, key, "单位", "UNIT_MISMATCH", "单位应为“" + sku.getSaleUnit() + "”");
                indexedLines.add(new IndexedLine(rowNumber, line, null));
                continue;
            }
            if (!seenSkuIds.add(sku.getSkuId())) {
                addError(result, rowNumber, key, "商品名称", "SKU_DUPLICATE", "同一订单内商品规格不能重复");
                indexedLines.add(new IndexedLine(rowNumber, line, null));
                continue;
            }
            indexedLines.add(new IndexedLine(rowNumber, line, sku));
        }
        return indexedLines;
    }

    private void validateLines(DeliveryNote note, List<IndexedLine> lines, SalesOrderImportResultVO result) {
        var key = orderKey(note);
        if (lines.size() > MAX_ITEMS_PER_ORDER)
            addError(result, lines.get(MAX_ITEMS_PER_ORDER).rowNumber(), key, "商品名称", "ITEM_LIMIT",
                "单张订单明细不能超过 " + MAX_ITEMS_PER_ORDER + " 行");
        for (var indexed : lines) {
            var line = indexed.line();
            var rowNumber = indexed.rowNumber();
            required(result, rowNumber, key, "商品名称", line.productName());
            required(result, rowNumber, key, "单位", line.unit());
            required(result, rowNumber, key, "实数量", line.quantity());
            length(result, rowNumber, key, "备注", line.remark(), 200);
            decimal(result, rowNumber, key, "实数量", line.quantity(), true);
            if (line.unitPrice() != null)
                decimal(result, rowNumber, key, "销售单价", line.unitPrice(), false);
            if (line.amount() != null)
                decimal(result, rowNumber, key, "销售金额", line.amount(), false);
            if (line.unitPrice() == null && line.amount() != null) {
                addError(result, rowNumber, key, "销售金额", "AMOUNT_WITHOUT_PRICE", "填写销售金额时必须填写销售单价");
                continue;
            }
            if (line.quantity() != null && line.quantity().matches(DECIMAL_PATTERN) && line.unitPrice() != null
                && line.unitPrice().matches(DECIMAL_PATTERN) && line.amount() != null
                && line.amount().matches(DECIMAL_PATTERN)) {
                var computed = new BigDecimal(line.quantity()).multiply(new BigDecimal(line.unitPrice())).setScale(2,
                    RoundingMode.HALF_UP);
                var given = new BigDecimal(line.amount()).setScale(2, RoundingMode.HALF_UP);
                if (computed.compareTo(given) != 0)
                    addError(result, rowNumber, key, "销售金额", "AMOUNT_NOT_MATCH", "销售金额应等于实数量×销售单价");
            }
        }
    }

    private void validateAddress(CustomerEntity customer, DeliveryNote note, SalesOrderImportResultVO result) {
        if (trim(customer.getContactName()) != null && trim(customer.getContactPhone()) != null
            && trim(customer.getAddress()) != null)
            return;
        addError(result, Math.max(note.customerRow(), 3), orderKey(note), "收货单位", "CUSTOMER_PROFILE_INCOMPLETE",
            "客户档案缺少收货人、联系电话或收货地址，请先在客户档案补全后再导入");
    }

    private void validatePrices(CustomerEntity customer, List<IndexedLine> lines, boolean priceOverrideAllowed,
        DeliveryNote note, SalesOrderImportResultVO result) {
        var key = orderKey(note);
        var priced = lines.stream().filter(line -> line.sku() != null).toList();
        if (priced.isEmpty())
            return;
        var ids = priced.stream().map(line -> line.sku().getSkuId()).distinct().toList();
        try {
            var resolved = priceResolver.resolve(customer.getId(), ids, OffsetDateTime.now()).stream()
                .collect(Collectors.toMap(ResolvedPriceVO::getSkuId, Function.identity()));
            for (var indexed : priced) {
                var line = indexed.line();
                var price = resolved.get(indexed.sku().getSkuId());
                if (price == null || !price.isSellable()) {
                    addError(result, indexed.rowNumber(), key, "商品名称", "SKU_NOT_SELLABLE", "商品已下架、分类停用或对该客户不可见");
                    continue;
                }
                if (line.unitPrice() != null && !priceOverrideAllowed) {
                    addError(result, indexed.rowNumber(), key, "销售单价", "PRICE_OVERRIDE_FORBIDDEN",
                        "当前账号没有订单改价权限，无法按送货单单价导入");
                    continue;
                }
                if (line.unitPrice() == null && price.getPriceStatus() == ScmPriceStatusEnum.UNPRICED) {
                    addError(result, indexed.rowNumber(), key, "销售单价", ScmPriceStatusEnum.UNPRICED.name(),
                        "商品没有可用价格，请填写销售单价或先维护价格");
                    continue;
                }
                if (line.quantity() == null || !line.quantity().matches(DECIMAL_PATTERN))
                    continue;
                var unitPrice = line.unitPrice() != null && line.unitPrice().matches(DECIMAL_PATTERN)
                    ? new BigDecimal(line.unitPrice())
                    : price.getUnitPrice();
                if (unitPrice == null)
                    continue;
                var amount = unitPrice.multiply(new BigDecimal(line.quantity())).setScale(4, RoundingMode.HALF_UP);
                if (amount.precision() > 18)
                    addError(result, indexed.rowNumber(), key, "实数量", "AMOUNT_OVERFLOW", "数量与单价计算的明细金额超出范围");
            }
        } catch (ScmBusinessException exception) {
            for (var indexed : priced)
                addError(result, indexed.rowNumber(), key, "收货单位", String.valueOf(exception.getErrorCode().getCode()),
                    exception.getErrorCode().getMsg());
        }
    }

    private SalesOrderAddForm toForm(CustomerEntity customer, DeliveryNote note, List<IndexedLine> lines) {
        var form = new SalesOrderAddForm();
        form.setCustomerId(customer.getId());
        form.setOrderSource(ScmOrderSourceEnum.IMPORT.name());
        form.setExpectDeliveryTime(note.deliveryDate());
        var address = new OrderAddressForm();
        address.setReceiverName(trim(customer.getContactName()));
        address.setReceiverPhone(trim(customer.getContactPhone()));
        address.setAddress(trim(customer.getAddress()));
        form.setAddress(address);
        var items = new ArrayList<SalesOrderItemForm>();
        for (int index = 0; index < lines.size(); index++) {
            var line = lines.get(index).line();
            var item = new SalesOrderItemForm();
            item.setSkuId(lines.get(index).sku().getSkuId());
            item.setOrderedQuantity(fixed(line.quantity()));
            // 送货单的备注是行备注：随商品打印到分拣单/送货单，不再汇总到整单备注
            item.setRemark(trim(line.remark()));
            item.setSortOrder(index);
            var manual = line.unitPrice() != null;
            item.setManualPriceOverride(manual);
            item.setUnitPrice(manual ? fixed(line.unitPrice()) : null);
            item.setOverrideReason(manual ? IMPORT_PRICE_REASON : null);
            items.add(item);
        }
        form.setItems(items);
        return form;
    }

    private List<IndexedRow> groupOf(List<IndexedLine> lines) {
        return lines.stream().map(line -> new IndexedRow(line.rowNumber(), line.line())).toList();
    }

    private String orderKey(DeliveryNote note) {
        return note.customerName() == null ? DEFAULT_ORDER_KEY : note.customerName();
    }

    private void required(SalesOrderImportResultVO result, int row, String key, String column, String cellValue) {
        if (trim(cellValue) == null)
            addError(result, row, key, column, "REQUIRED", column + "不能为空");
    }

    private void length(SalesOrderImportResultVO result, int row, String key, String column, String cellValue,
        int max) {
        if (trim(cellValue) != null && trim(cellValue).length() > max)
            addError(result, row, key, column, "TOO_LONG", column + "不能超过 " + max + " 个字符");
    }

    private void decimal(SalesOrderImportResultVO result, int row, String key, String column, String decimalText,
        boolean positive) {
        if (trim(decimalText) == null)
            return;
        try {
            if (!trim(decimalText).matches(DECIMAL_PATTERN))
                throw new NumberFormatException();
            var number = new BigDecimal(trim(decimalText));
            if (positive ? number.signum() <= 0 : number.signum() < 0)
                throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            addError(result, row, key, column, "DECIMAL_INVALID",
                column + "必须是" + (positive ? "大于零的" : "非负") + "四位以内小数");
        }
    }

    private String fixed(String decimalText) {
        return new BigDecimal(trim(decimalText)).setScale(4, RoundingMode.UNNECESSARY).toPlainString();
    }

    private String cellText(Cell cell, DataFormatter formatter) {
        if (cell == null)
            return null;
        if (cell.getCellType() == CellType.NUMERIC)
            return trim(NumberToTextConverter.toText(cell.getNumericCellValue()));
        return trim(formatter.formatCellValue(cell));
    }

    private String trim(String inputText) {
        if (inputText == null)
            return null;
        var trimmed = inputText.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void addError(SalesOrderImportResultVO result, int row, String key, String column, String code,
        String message) {
        result.setTotalErrors(result.getTotalErrors() + 1);
        if (result.getErrors().size() < MAX_ERRORS)
            result.getErrors().add(new SalesOrderImportErrorVO(row, key, column, code, message));
    }

    private record Meta(String customerName, int customerRow, OffsetDateTime deliveryDate, int dateRow) {
    }

    private record NoteLine(int rowNumber, String productName, String unit, String specName, String quantity,
        String unitPrice, String amount, String remark) {
    }

    private record DeliveryNote(String customerName, int customerRow, OffsetDateTime deliveryDate, int dateRow,
        List<NoteLine> lines) {
    }

    private record IndexedLine(int rowNumber, NoteLine line, ProductSkuOptionVO sku) {
    }

    private record IndexedRow(int rowNumber, NoteLine line) {
    }

    private record Assembly(SalesOrderImportResultVO result, List<SalesOrderAddForm> forms,
        List<List<IndexedRow>> groups) {
    }
}
