package net.lab1024.sa.admin.module.scm.product;

import cn.dev33.satoken.annotation.SaCheckPermission;
import net.lab1024.sa.admin.AdminApplication;
import net.lab1024.sa.admin.module.scm.common.error.ScmErrorCode;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.product.controller.*;
import net.lab1024.sa.admin.module.scm.product.dao.ProductSkuOptionDao;
import net.lab1024.sa.admin.module.scm.product.domain.form.*;
import net.lab1024.sa.admin.module.scm.product.domain.vo.*;
import net.lab1024.sa.admin.module.scm.product.service.*;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.admin.test.PgITPaths;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.junit.jupiter.api.*;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.OffsetDateTime;
import java.util.*;

import static net.lab1024.sa.admin.module.scm.product.constant.ProductErrorCode.*;
import static org.assertj.core.api.Assertions.*;

/**
 * PCO-1 商品主档增强：单位与标签字典、master_status 作用域、删除保护、批量维护与高级筛选。
 * 用例只覆盖主数据侧的新增口径，订单与采购写入路径的既有行为由 {@link ProductPgIT} 守住了事实验证。
 */
@SpringBootTest(classes = AdminApplication.class, properties = {
        "project.log-directory=" + PgITPaths.DEFAULT_LOG_DIR,
        "file.storage.local.upload-path=" + PgITPaths.DEFAULT_UPLOAD_PATH,
        "file.storage.local.url-prefix=http://127.0.0.1:18082",
        "logging.level.root=WARN"})
@Transactional
class ProductMasterDataPgIT {
    @Autowired
    ProductSpuService service;
    @Autowired
    ProductQueryService query;
    @Autowired
    ProductBatchService batch;
    @Autowired
    ProductUomService uom;
    @Autowired
    ProductTagService tags;
    @Autowired
    ProductCategoryService categories;
    @Autowired
    ProductSkuOptionQueryService optionService;
    @Autowired
    ProductSkuOptionDao optionDao;
    @Autowired
    JdbcTemplate jdbc;
    private String prefix;
    private int sequence;

    @BeforeEach
    void operator() {
        prefix = "P1-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT);
        var employee = new RequestEmployee();
        employee.setEmployeeId(1L);
        employee.setActualName("PCO-1 IT");
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        SmartRequestUtil.setRequestUser(employee);
    }

    @AfterEach
    void clearOperator() {
        SmartRequestUtil.remove();
    }

    /**
     * SKU 编码在整个库内唯一，所以每个商品都要换新编码。
     */
    private String code() {
        return prefix + "-" + (++sequence);
    }

    private ProductSpuAddForm product(String unit) {
        var form = new ProductSpuAddForm();
        var code = code();
        form.setSpuCode(code);
        form.setName(code + "商品");
        form.setCategoryId(jdbc.queryForObject("SELECT id FROM product_category WHERE category_code='FRESH-FRUIT' AND deleted=FALSE", Long.class));
        form.setStatus("OFF_SHELF");
        var sku = ProductAggregateValidatorTest.sku(code + "A", true, "大");
        sku.setSpecName("大");
        sku.setSaleUnit(unit);
        sku.setProductType("NON_STANDARD");
        sku.setStatus("ON_SHELF");
        form.setSkuList(new ArrayList<>(List.of(sku)));
        return form;
    }

    /**
     * 详情页原样回填，用来验证「改一个无关字段」会不会被新增校验误伤。
     */
    private ProductSpuUpdateForm edit(Long id) {
        var detail = query.detail(id);
        var form = new ProductSpuUpdateForm();
        BeanUtils.copyProperties(detail, form);
        form.setSkuList(detail.getSkuList().stream().map(vo -> {
            var sku = new ProductSkuForm();
            BeanUtils.copyProperties(vo, sku);
            return sku;
        }).toList());
        form.setImages(detail.getImages().stream().map(vo -> {
            var img = new ProductImageForm();
            BeanUtils.copyProperties(vo, img);
            return img;
        }).toList());
        form.setTagIds(detail.getTags().stream().map(ProductSpuTagVO::getTagId).toList());
        return form;
    }

    private Long skuId(Long spuId) {
        return jdbc.queryForObject("SELECT id FROM product_sku WHERE spu_id=? AND deleted=FALSE", Long.class, spuId);
    }

    private Integer version(Long spuId) {
        return query.detail(spuId).getVersion();
    }

    private ProductDeleteForm deletion(Long spuId) {
        var form = new ProductDeleteForm();
        form.setSpuId(spuId);
        form.setVersion(version(spuId));
        return form;
    }

    private void conflict(Runnable action, ScmErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(e.getErrorCode()).isSameAs(code));
    }

    private Long tag(String suffix) {
        var form = new ProductTagAddForm();
        form.setTagCode(prefix + suffix);
        form.setName(prefix + suffix);
        form.setStatus("ENABLED");
        return tags.add(form);
    }

    private ProductTagVO tagRow(Long id) {
        var query = new ProductAssistantQueryForm();
        query.setKeyword(prefix);
        return tags.list(query).stream().filter(v -> v.getTagId().equals(id)).findFirst().orElseThrow();
    }

    private ProductUomVO uomRow(Long id) {
        var query = new ProductAssistantQueryForm();
        query.setKeyword(prefix);
        return uom.list(query).stream().filter(v -> v.getUomId().equals(id)).findFirst().orElseThrow();
    }

    private ProductUomKeyForm uomKey(ProductUomVO vo) {
        var form = new ProductUomKeyForm();
        form.setUomId(vo.getUomId());
        form.setVersion(vo.getVersion());
        return form;
    }

    private ProductTagKeyForm tagKey(ProductTagVO vo) {
        var form = new ProductTagKeyForm();
        form.setTagId(vo.getTagId());
        form.setVersion(vo.getVersion());
        return form;
    }

    private void disableTag(Long id) {
        var form = new ProductTagUpdateForm();
        BeanUtils.copyProperties(tagRow(id), form);
        form.setStatus("DISABLED");
        tags.update(form);
    }

    private ProductBatchItemForm item(Long spuId, Integer version) {
        var item = new ProductBatchItemForm();
        item.setSpuId(spuId);
        item.setVersion(version);
        return item;
    }

    private ProductSpuQueryForm search() {
        var form = new ProductSpuQueryForm();
        form.setPageNum(1L);
        form.setPageSize(50L);
        form.setKeyword(prefix);
        return form;
    }

    private List<Long> hits(ProductSpuQueryForm form) {
        return query.query(form).getList().stream().map(ProductSpuVO::getSpuId).toList();
    }

    private List<String> selectableSkuCodes() {
        var form = new ProductSkuOptionQueryForm();
        form.setKeyword(prefix);
        form.setLimit(50);
        return optionService.optionList(form).options().stream().map(ProductSkuOptionVO::getSkuCode).toList();
    }

    @Test
    void uomIsUniqueByCodeAndNameAndRetiredOnceReferenced() {
        assertThat(uom.options()).extracting(ProductUomVO::getName).contains("kg", "箱", "把");
        var add = new ProductUomAddForm();
        add.setUomCode("UOM-" + prefix);
        add.setName(prefix + "单位");
        add.setCategory("WEIGHT");
        add.setPrecisionScale(2);
        add.setStatus("ENABLED");
        add.setSortOrder(900);
        Long id = uom.add(add);
        assertThat(uomRow(id).getReferencedCount()).isZero();
        var sameName = new ProductUomAddForm();
        BeanUtils.copyProperties(add, sameName);
        sameName.setUomCode("UOM-" + prefix + "X");
        conflict(() -> uom.add(sameName), UOM_NAME_DUPLICATE);
        var sameCode = new ProductUomAddForm();
        BeanUtils.copyProperties(add, sameCode);
        sameCode.setName(prefix + "另一单位");
        conflict(() -> uom.add(sameCode), UOM_CODE_DUPLICATE);
        // 单位名称是业务表的记账字符串，改名等于让既有数据指向别的单位：编辑表单里根本没有这两个字段。
        assertThat(Arrays.stream(ProductUomUpdateForm.class.getDeclaredFields()).map(Field::getName).toList())
                .doesNotContain("name", "uomCode");
        var stale = new ProductUomUpdateForm();
        BeanUtils.copyProperties(uomRow(id), stale);
        stale.setVersion(9);
        conflict(() -> uom.update(stale), VERSION_CONFLICT);

        var form = product(prefix + "单位");
        Long spu = service.add(form);
        assertThat(uomRow(id).getReferencedCount()).isEqualTo(1);
        conflict(() -> uom.delete(uomKey(uomRow(id))), UOM_REFERENCED);
        // 停用只挡住新引用，已按该单位记账的商品照旧。
        var retire = new ProductUomUpdateForm();
        BeanUtils.copyProperties(uomRow(id), retire);
        retire.setStatus("DISABLED");
        uom.update(retire);
        assertThat(uomRow(id).getName()).isEqualTo(prefix + "单位");
        conflict(() -> service.add(product(prefix + "单位")), UOM_NOT_USABLE);
        assertThat(query.detail(spu).getSkuList().getFirst().getSaleUnit()).isEqualTo(prefix + "单位");
        service.delete(deletion(spu));
        uom.delete(uomKey(uomRow(id)));
        conflict(() -> uom.delete(uomKey(id, 0)), UOM_NOT_FOUND);
    }

    private ProductUomKeyForm uomKey(Long id, Integer version) {
        var form = new ProductUomKeyForm();
        form.setUomId(id);
        form.setVersion(version);
        return form;
    }

    @Test
    void dictionaryOnlyGuardsUnitsWrittenFromNowOn() {
        Long spu = service.add(product("kg"));
        // 字典晚于既有商品建立：历史值「板」不在字典里，原样保存必须继续通过。
        jdbc.update("UPDATE product_sku SET sale_unit='板' WHERE spu_id=?", spu);
        service.update(edit(spu));
        assertThat(query.detail(spu).getSkuList().getFirst().getSaleUnit()).isEqualTo("板");
        var unknown = edit(spu);
        unknown.getSkuList().getFirst().setSaleUnit("不存在单位");
        conflict(() -> service.update(unknown), UOM_NOT_USABLE);
        var known = edit(spu);
        known.getSkuList().getFirst().setSaleUnit("箱");
        service.update(known);
        assertThat(query.detail(spu).getSkuList().getFirst().getSaleUnit()).isEqualTo("箱");
    }

    @Test
    void masterStatusOnlyNarrowsTheSelectableScope() {
        var form = product("kg");
        form.setMasterStatus("DISABLED");
        Long spu = service.add(form);
        var skuCode = query.detail(spu).getSkuList().getFirst().getSkuCode();
        assertThat(selectableSkuCodes()).doesNotContain(skuCode);
        // 写入路径不动：已存在的 SKU 仍按 id 解析，历史单据不会被查不到商品。
        assertThat(optionDao.selectByIds(List.of(skuId(spu)))).extracting(ProductSkuOptionVO::getSkuCode).containsExactly(skuCode);
        var enable = edit(spu);
        enable.setMasterStatus("ENABLED");
        service.update(enable);
        assertThat(selectableSkuCodes()).containsExactly(skuCode);
        var archived = product("kg");
        archived.setMasterStatus("ARCHIVED");
        Long archivedId = service.add(archived);
        assertThat(selectableSkuCodes()).containsExactly(skuCode);
        assertThat(query.detail(archivedId).getSkuList()).extracting(ProductSkuVO::getSaleUnit).containsExactly("kg");
    }

    @Test
    void archivedProductCannotStayOnShelfAtServiceAndDatabaseLevel() {
        var form = product("kg");
        form.setStatus("ON_SHELF");
        form.setMasterStatus("ARCHIVED");
        conflict(() -> service.add(form), MASTER_STATUS_SALE_CONFLICT);
        // 被校验拒绝的 update 在抛出前已改过 MyBatis 一级缓存里的实体（没有写入语句就不清缓存），
        // 同一事务内再读同一行会读到那个脏对象，所以拒绝断言和后续流程各用一个商品。
        Long rejected = service.add(product("kg"));
        var archive = edit(rejected);
        archive.setStatus("ON_SHELF");
        archive.setMasterStatus("ARCHIVED");
        conflict(() -> service.update(archive), MASTER_STATUS_SALE_CONFLICT);
        Long spu = service.add(product("kg"));
        var off = edit(spu);
        off.setMasterStatus("ARCHIVED");
        service.update(off);
        var backOnline = new ProductStatusForm();
        backOnline.setSpuId(spu);
        backOnline.setVersion(version(spu));
        backOnline.setStatus("ON_SHELF");
        conflict(() -> service.updateStatus(backOnline), MASTER_STATUS_SALE_CONFLICT);
        // CHECK 是服务层之外的兜底。PG 的语句级错误会终止整个事务，所以这条必须放在最后。
        assertThatThrownBy(() -> jdbc.update("UPDATE product_spu SET status='ON_SHELF' WHERE id=?", spu))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deleteIsBlockedOnceTheProductHasPricingOrBusinessFacts() {
        Long clean = service.add(product("kg"));
        service.delete(deletion(clean));
        conflict(() -> query.detail(clean), PRODUCT_NOT_FOUND);
        Long priced = service.add(product("kg"));
        jdbc.update("INSERT INTO customer_type_price(customer_type_id,sku_id,unit_price,effective_from,created_by) VALUES (?,?,1.5,?,?)",
                1L, skuId(priced), OffsetDateTime.now(), "pco1-it");
        conflict(() -> service.delete(deletion(priced)), PRODUCT_BUSINESS_REFERENCED);
        // 撤销价格行也不解锁：删除保护看的是「是否产生过事实」，不是「现在还挂着价格」。
        jdbc.update("UPDATE customer_type_price SET deleted=TRUE WHERE sku_id=?", skuId(priced));
        conflict(() -> service.delete(deletion(priced)), PRODUCT_BUSINESS_REFERENCED);
    }

    /**
     * 供应商关系与客户可见性只是主数据关联，按方案 §11.2 不拦删除。
     */
    @Test
    void supplierRelationDoesNotBlockProductDeletion() {
        Long linked = service.add(product("kg"));
        // 本库不建外键，关联行只需存在即可验证删除口径，不依赖 supplier 种子数据。
        jdbc.update("INSERT INTO supplier_sku(supplier_id,sku_id,supplier_code_snapshot,supplier_name_snapshot,sku_code_snapshot,sku_name_snapshot,purchase_unit) "
                + "VALUES (999999,?, 'S-IT','IT 供应商','SKU','IT 规格','kg')", skuId(linked));
        service.delete(deletion(linked));
        conflict(() -> query.detail(linked), PRODUCT_NOT_FOUND);
    }

    @Test
    void tagStaysVisibleOnBoundProductsAndOnlyBlocksNewBindings() {
        var sameName = new ProductTagAddForm();
        sameName.setTagCode(prefix + "DUP");
        sameName.setName(prefix + "KEEP");
        sameName.setStatus("ENABLED");
        Long bound = tag("KEEP");
        conflict(() -> tags.add(sameName), TAG_NAME_DUPLICATE);
        Long retire = tag("RETIRE"), off = tag("OFF");
        var form = product("kg");
        form.setTagIds(List.of(bound, retire));
        Long spu = service.add(form);
        assertThat(query.detail(spu).getTags()).extracting(ProductSpuTagVO::getTagId).containsExactlyInAnyOrder(bound, retire);
        conflict(() -> {
            var other = product("kg");
            other.setTagIds(List.of(off, 999999L));
            service.add(other);
        }, TAG_NOT_FOUND);
        conflict(() -> tags.delete(tagKey(tagRow(bound))), TAG_REFERENCED);

        disableTag(retire);
        var keepHistory = edit(spu);
        keepHistory.setBrandName("测试品牌");
        service.update(keepHistory);
        var saved = query.detail(spu);
        assertThat(saved.getBrandName()).isEqualTo("测试品牌");
        assertThat(saved.getTags()).extracting(ProductSpuTagVO::getStatus).containsExactlyInAnyOrder("ENABLED", "DISABLED");
        // 已绑定的停用标签可以原样保留，但「新挂」一个停用标签必须被拒。
        disableTag(off);
        var addDisabled = edit(spu);
        addDisabled.setTagIds(List.of(bound, retire, off));
        conflict(() -> service.update(addDisabled), TAG_NOT_USABLE);
        var dropRetired = edit(spu);
        dropRetired.setTagIds(List.of(bound));
        service.update(dropRetired);
        conflict(() -> {
            var rebind = edit(spu);
            rebind.setTagIds(List.of(bound, retire));
            service.update(rebind);
        }, TAG_NOT_USABLE);
        // 商品档案删除后不再占住标签，标签本身才可清理。
        service.delete(deletion(spu));
        assertThat(tagRow(bound).getProductCount()).isZero();
        tags.delete(tagKey(tagRow(bound)));
        tags.delete(tagKey(tagRow(off)));
        conflict(() -> tags.delete(tagKey(off, 0)), TAG_NOT_FOUND);
    }

    private ProductTagKeyForm tagKey(Long id, Integer version) {
        var form = new ProductTagKeyForm();
        form.setTagId(id);
        form.setVersion(version);
        return form;
    }

    @Test
    void batchRejectsEveryStaleRowBeforeWritingAnything() {
        Long a = service.add(product("kg")), b = service.add(product("kg"));
        var status = new ProductSpuBatchStatusForm();
        status.setItems(List.of(item(a, version(a)), item(b, version(b) + 9)));
        status.setMasterStatus("DISABLED");
        var partial = batch.updateStatus(status);
        assertThat(partial.getUpdatedCount()).isZero();
        assertThat(partial.getFailures()).extracting(ProductBatchResultVO.Failure::getSpuId).containsExactly(b);
        assertThat(query.detail(a).getMasterStatus()).isEqualTo("ENABLED");
        status.setItems(List.of(item(a, version(a)), item(b, version(b))));
        assertThat(batch.updateStatus(status).getUpdatedCount()).isEqualTo(2);
        assertThat(query.detail(a).getMasterStatus()).isEqualTo("DISABLED");
        assertThat(query.detail(b).getVersion()).isEqualTo(1);
        // 归档 + 在售的非法组合同样整批不落库，失败按行回报。
        var archived = new ProductSpuBatchStatusForm();
        archived.setStatus("ON_SHELF");
        archived.setMasterStatus("ARCHIVED");
        archived.setItems(List.of(item(a, version(a)), item(b, version(b))));
        assertThat(batch.updateStatus(archived).getFailures()).extracting(ProductBatchResultVO.Failure::getReasonCode)
                .containsExactly(MASTER_STATUS_SALE_CONFLICT.getCode(), MASTER_STATUS_SALE_CONFLICT.getCode());
        assertThat(query.detail(a).getStatus()).isEqualTo("OFF_SHELF");
        assertThat(query.detail(a).getMasterStatus()).isEqualTo("DISABLED");
    }

    @Test
    void batchAppliesCategoryAndEveryTagModeToWholeSelection() {
        Long a = service.add(product("kg")), b = service.add(product("kg"));
        Long root = category(null, "1"), second = category(root, "2"), leaf = category(second, "3");
        var category = new ProductSpuBatchCategoryForm();
        category.setCategoryId(leaf);
        category.setItems(List.of(item(a, version(a)), item(b, version(b))));
        assertThat(batch.updateCategory(category).getUpdatedCount()).isEqualTo(2);
        assertThat(query.detail(a).getCategoryPath()).endsWith("1 / 2 / 3");

        Long first = tag("M1"), secondTag = tag("M2"), retired = tag("M3");
        var add = batchTags("ADD", List.of(first, secondTag), a, b);
        assertThat(batch.updateTags(add).getUpdatedCount()).isEqualTo(2);
        assertThat(query.detail(a).getTags()).extracting(ProductSpuTagVO::getTagId).containsExactlyInAnyOrder(first, secondTag);
        batch.updateTags(batchTags("REMOVE", List.of(secondTag), a));
        assertThat(query.detail(a).getTags()).extracting(ProductSpuTagVO::getTagId).containsExactly(first);
        assertThat(query.detail(b).getTags()).hasSize(2);
        // 批量打标语义是「新引用」，停用标签即使已在商品上也不再允许被批量挂上；摘除不受影响。
        disableTag(retired);
        conflict(() -> batch.updateTags(batchTags("ADD", List.of(retired), a)), TAG_NOT_USABLE);
        batch.updateTags(batchTags("REMOVE", List.of(first, secondTag), a, b));
        assertThat(query.detail(a).getTags()).isEmpty();
        // REPLACE 传空集合就是清空。
        batch.updateTags(batchTags("REPLACE", List.of(secondTag), a));
        var replace = batchTags("REPLACE", List.of(), a, b);
        assertThat(batch.updateTags(replace).getUpdatedCount()).isEqualTo(2);
        assertThat(query.detail(a).getTags()).isEmpty();
        assertThat(query.detail(b).getTags()).isEmpty();
    }

    private ProductSpuBatchTagForm batchTags(String mode, List<Long> tagIds, Long... spuIds) {
        var form = new ProductSpuBatchTagForm();
        form.setMode(mode);
        form.setTagIds(tagIds);
        form.setItems(Arrays.stream(spuIds).map(id -> item(id, version(id))).toList());
        return form;
    }

    private Long category(Long parent, String suffix) {
        var form = new ProductCategoryAddForm();
        form.setParentId(parent);
        form.setCategoryCode(prefix + suffix);
        form.setName(suffix);
        form.setStatus("ENABLED");
        return categories.add(form);
    }

    @Test
    void advancedFiltersCoverMnemonicTagMasterStatusStorageFlagsAndCreatedRange() {
        var form = product("kg");
        form.setMnemonicCode(prefix + "ZJ");
        form.setStorageMethod("CHILLED");
        Long tagged = tag("SEARCH");
        form.setTagIds(List.of(tagged));
        Long spu = service.add(form);
        assertThat(hits(search())).containsExactly(spu);
        var byMnemonic = search();
        byMnemonic.setKeyword(prefix + "zj");
        assertThat(hits(byMnemonic)).containsExactly(spu);
        var byTag = search();
        byTag.setTagIds(List.of(tagged));
        assertThat(hits(byTag)).containsExactly(spu);
        var byMissingTag = search();
        byMissingTag.setTagIds(List.of(tag("OTHER")));
        assertThat(hits(byMissingTag)).isEmpty();
        var byMaster = search();
        byMaster.setMasterStatus("DISABLED");
        assertThat(hits(byMaster)).isEmpty();
        var byStorage = search();
        byStorage.setStorageMethod("CHILLED");
        assertThat(hits(byStorage)).containsExactly(spu);
        var otherStorage = search();
        otherStorage.setStorageMethod("FROZEN");
        assertThat(hits(otherStorage)).isEmpty();
        // 直连改库不会清 MyBatis 一级缓存，所以下面三个探测必须用互不相同的条件组合。
        assertThat(hits(flag(false, false))).containsExactly(spu);
        jdbc.update("UPDATE product_sku SET barcode=? WHERE spu_id=?", prefix + "BAR", spu);
        assertThat(hits(flag(true, false))).containsExactly(spu);
        assertThat(hits(flag(true, true))).isEmpty();
        jdbc.update("UPDATE product_spu SET created_at=now()-interval '7 days' WHERE id=?", spu);
        var recent = search();
        recent.setCreatedFrom(OffsetDateTime.now().minusDays(1));
        assertThat(hits(recent)).isEmpty();
        var window = search();
        window.setCreatedFrom(OffsetDateTime.now().minusDays(30));
        window.setCreatedTo(OffsetDateTime.now());
        assertThat(hits(window)).containsExactly(spu);
    }

    private ProductSpuQueryForm flag(boolean hasBarcode, boolean hasPrimaryImage) {
        var form = search();
        form.setHasBarcode(hasBarcode);
        form.setHasPrimaryImage(hasPrimaryImage);
        return form;
    }

    @Test
    void everyProductEndpointPermissionIsSeededInMenuTree() {
        Set<String> declared = new LinkedHashSet<>();
        for (Class<?> controller : List.of(ProductController.class, ProductCategoryController.class, ProductSkuController.class,
                ProductUomController.class, ProductTagController.class)) {
            for (Method method : controller.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers())) continue;
                var permission = method.getAnnotation(SaCheckPermission.class);
                assertThat(permission).as("%s#%s 必须声明 @SaCheckPermission", controller.getSimpleName(), method.getName()).isNotNull();
                declared.addAll(List.of(permission.value()));
            }
        }
        var seeded = jdbc.queryForList("SELECT DISTINCT api_perms FROM t_menu WHERE api_perms IS NOT NULL", String.class);
        assertThat(seeded).as("权限码没进菜单种子就会对所有人不可用").containsAll(declared);
        assertThat(declared).contains("scm:product:batch", "scm:product:uom:add", "scm:product:tag:delete");
        assertThat(declared).allSatisfy(code -> assertThat(code).startsWith("scm:product"));
    }
}
