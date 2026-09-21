package net.lab1024.sa.admin.module.scm.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.lab1024.sa.admin.AdminApplication;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.product.dao.*;
import net.lab1024.sa.admin.module.scm.product.domain.form.*;
import net.lab1024.sa.admin.module.scm.product.domain.vo.*;
import net.lab1024.sa.admin.module.scm.product.service.*;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.admin.test.PgITPaths;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import net.lab1024.sa.base.module.support.file.service.FileService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes = AdminApplication.class, properties = {
        "project.log-directory=" + PgITPaths.DEFAULT_LOG_DIR,
        "file.storage.local.upload-path=" + PgITPaths.DEFAULT_UPLOAD_PATH,
        "file.storage.local.url-prefix=http://127.0.0.1:18082",
        "logging.level.root=WARN"})
@Transactional
class ProductPgIT {
    @Autowired
    ProductSpuService service;
    @Autowired
    ProductQueryService query;
    @Autowired
    ProductCategoryService categories;
    @Autowired
    ProductSkuDao skuDao;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ObjectMapper json;
    @Autowired
    FileService files;
    private String prefix;

    @BeforeEach
    void operator() {
        prefix = "W1-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT);
        var employee = new RequestEmployee();
        employee.setEmployeeId(1L);
        employee.setActualName("W1 IT");
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        SmartRequestUtil.setRequestUser(employee);
    }

    @AfterEach
    void clearOperator() {
        SmartRequestUtil.remove();
    }

    private ProductSpuAddForm product() {
        var form = new ProductSpuAddForm();
        form.setSpuCode(prefix);
        form.setName(prefix + "商品");
        form.setAlias(prefix + "别名");
        form.setCategoryId(jdbc.queryForObject("SELECT id FROM product_category WHERE category_code='FRESH-FRUIT' AND deleted=FALSE", Long.class));
        form.setStatus("OFF_SHELF");
        form.setSkuList(new ArrayList<>(List.of(sku("A", true, "大"), sku("B", false, "小"))));
        return form;
    }

    private ProductSkuForm sku(String suffix, boolean primary, String spec) {
        var sku = ProductAggregateValidatorTest.sku(prefix + suffix, primary, spec);
        sku.setSpecName(spec);
        sku.setSaleUnit("kg");
        sku.setProductType("NON_STANDARD");
        sku.setStatus("ON_SHELF");
        sku.setBarcode(prefix + "BAR" + suffix);
        return sku;
    }

    private ProductSpuUpdateForm update(Long id) {
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
        return form;
    }

    private ProductDeleteForm deletion(Long id, int version) {
        var form = new ProductDeleteForm();
        form.setSpuId(id);
        form.setVersion(version);
        return form;
    }

    private void conflict(Runnable action, int code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(e.getErrorCode().getCode()).isEqualTo(code));
    }

    @Test
    void deltaKeepsIdentitySwitchesDefaultAndSoftDeletesOnlyRemovedSku() {
        Long id = service.add(product());
        var initial = query.detail(id);
        Long kept = initial.getSkuList().getFirst().getSkuId();
        Long removed = initial.getSkuList().getLast().getSkuId();
        var update = update(id);
        var keep = update.getSkuList().getFirst();
        keep.setDefaultFlag(false);
        keep.setMarketPrice(new BigDecimal("2.3456"));
        var added = sku("C", true, "中");
        update.setSkuList(List.of(keep, added));
        service.update(update);
        var saved = query.detail(id);
        assertThat(saved.getVersion()).isEqualTo(1);
        assertThat(saved.getSkuList().getFirst().getSkuId()).isEqualTo(kept);
        assertThat(saved.getSkuList().getFirst().getVersion()).isEqualTo(1);
        assertThat(saved.getDefaultSku().getSkuCode()).isEqualTo(prefix + "C");
        assertThat(jdbc.queryForObject("SELECT deleted FROM product_sku WHERE id=?", Boolean.class, removed)).isTrue();
        assertThat(jdbc.queryForObject("SELECT created_by FROM product_spu WHERE id=?", String.class, id)).isEqualTo("1:1");
    }

    @Test
    void switchesDefaultBetweenTwoExistingSkuRows() {
        Long id = service.add(product());
        var form = update(id);
        var ids = form.getSkuList().stream().map(ProductSkuForm::getSkuId).toList();
        form.getSkuList().getFirst().setDefaultFlag(false);
        form.getSkuList().getLast().setDefaultFlag(true);
        service.update(form);
        assertThat(query.detail(id).getDefaultSku().getSkuId()).isEqualTo(ids.getLast());
        assertThat(query.detail(id).getSkuList()).extracting(ProductSkuVO::getSkuId).containsExactlyElementsOf(ids);
    }

    @Test
    void rejectsStaleSpuVersionAfterStatusChange() {
        Long id = service.add(product());
        var stale = update(id);
        var status = new ProductStatusForm();
        status.setSpuId(id);
        status.setVersion(0);
        status.setStatus("ON_SHELF");
        service.updateStatus(status);
        conflict(() -> service.update(stale), 40921);
        assertThat(query.detail(id).getStatus()).isEqualTo("ON_SHELF");
    }

    @Test
    void detectsIndependentSkuVersionConflict() {
        Long id = service.add(product());
        var form = update(id);
        jdbc.update("UPDATE product_sku SET version=version+1 WHERE id=?", form.getSkuList().getFirst().getSkuId());
        conflict(() -> service.update(form), 40921);
    }

    @Test
    void rejectsCrossProductSkuAndMissingDefault() {
        Long id = service.add(product());
        var form = update(id);
        form.getSkuList().getFirst().setSkuId(-1L);
        conflict(() -> service.update(form), 40920);
    }

    @Test
    void deletesWholeAggregateAndAllowsCodeReuse() {
        var form = product();
        Long id = service.add(form);
        service.delete(deletion(id, 0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_sku WHERE spu_id=? AND deleted=TRUE", Integer.class, id)).isEqualTo(2);
        conflict(() -> query.detail(id), 40420);
        assertThat(service.add(form)).isNotEqualTo(id);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "商品", "别名", "A", "BARA"})
    void searchesEveryLegacyField(String suffix) {
        Long id = service.add(product());
        var form = new ProductSpuQueryForm();
        form.setPageNum(1L);
        form.setPageSize(20L);
        form.setKeyword(prefix + suffix);
        assertThat(query.query(form).getList()).extracting(ProductSpuVO::getSpuId).contains(id);
    }

    @Test
    void ancestorCategoryFilterIsV2EnhancementAndJsonbAndPriceContractSurvive() throws Exception {
        Long id = service.add(product());
        var form = new ProductSpuQueryForm();
        form.setPageNum(1L);
        form.setPageSize(20L);
        form.setKeyword(prefix);
        form.setCategoryId(jdbc.queryForObject("SELECT id FROM product_category WHERE category_code='FRESH' AND deleted=FALSE", Long.class));
        var page = query.query(form);
        assertThat(page.getList()).extracting(ProductSpuVO::getSpuId).containsExactly(id);
        assertThat(page.getList().getFirst().getCategoryPath()).isEqualTo("生鲜 / 果蔬 / 水果");
        var tree = json.readTree(json.writeValueAsString(query.detail(id)));
        assertThat(tree.at("/skuList/0/marketPrice").asText()).isEqualTo("1.2000");
        assertThat(tree.at("/skuList/0/specValues/规格").asText()).isEqualTo("大");
    }

    private ProductImageForm upload(String name) {
        byte[] png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aRZkAAAAASUVORK5CYII=");
        var uploaded = files.fileUpload(new MockMultipartFile("file", name, "image/png", png), 1, SmartRequestUtil.getRequestUser());
        assertThat(uploaded.getOk()).isTrue();
        var image = new ProductImageForm();
        image.setFileKey(uploaded.getData().getFileKey());
        image.setPrimaryFlag(true);
        return image;
    }

    @Test
    void usesSmartAdminFilesAndSwitchesPrimaryImageWithoutReplacingIdentity() {
        var form = product();
        var a = upload("w1-a.png");
        var b = upload("w1-b.png");
        b.setPrimaryFlag(false);
        form.setImages(List.of(a, b));
        Long id = service.add(form);
        var initial = query.detail(id);
        var edit = update(id);
        edit.getImages().getFirst().setPrimaryFlag(false);
        edit.getImages().getLast().setPrimaryFlag(true);
        edit.getImages().getLast().setFileName("forged.txt");
        service.update(edit);
        var saved = query.detail(id);
        assertThat(saved.getImages()).extracting(ProductImageVO::getImageId).containsExactlyElementsOf(initial.getImages().stream().map(ProductImageVO::getImageId).toList());
        assertThat(saved.getImages().getLast().getFileName()).isEqualTo("w1-b.png");
        assertThat(saved.getPrimaryImageUrl()).isEqualTo(saved.getImages().getLast().getFileUrl());
    }

    @Test
    void rejectsUnknownFileReference() {
        var form = product();
        var image = new ProductImageForm();
        image.setFileKey("missing-file");
        form.setImages(List.of(image));
        conflict(() -> service.add(form), 40026);
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
    void categoryDepthDeleteGuardsAndVersionAreEnforced() {
        Long root = category(null, "1");
        Long second = category(root, "2");
        Long leaf = category(second, "3");
        assertThat(categories.detail(leaf).getLevel()).isEqualTo(3);
        conflict(() -> category(leaf, "4"), 40010);
        var deletion = new ProductCategoryDeleteForm();
        deletion.setCategoryId(root);
        deletion.setVersion(0);
        conflict(() -> categories.delete(deletion), 40910);
        var edit = new ProductCategoryUpdateForm();
        BeanUtils.copyProperties(categories.detail(leaf), edit);
        edit.setName("renamed");
        categories.update(edit);
        conflict(() -> categories.update(edit), 40921);
        var p = product();
        p.setCategoryId(leaf);
        service.add(p);
        deletion.setCategoryId(leaf);
        deletion.setVersion(1);
        conflict(() -> categories.delete(deletion), 40911);
    }

    @ParameterizedTest
    @ValueSource(strings = {"default", "price", "json", "primary"})
    void postgresConstraintsProvideIndependentBackstop(String type) {
        Long id = service.add(product());
        assertThatThrownBy(() -> {
            switch (type) {
                case "default" -> jdbc.update("UPDATE product_sku SET is_default=TRUE WHERE spu_id=?", id);
                case "price" -> jdbc.update("UPDATE product_sku SET market_price=-1 WHERE spu_id=?", id);
                case "json" -> jdbc.update("UPDATE product_sku SET spec_values='[]'::jsonb WHERE spu_id=?", id);
                case "primary" ->
                        jdbc.update("INSERT INTO product_image(spu_id,file_key,file_url,is_primary) VALUES (?,'a','a',TRUE),(?,'b','b',TRUE)", id, id);
                default -> throw new AssertionError(type);
            }
        }).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
