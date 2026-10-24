package com.xsy.scm.product;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Validation;
import net.lab1024.sa.admin.AdminApplication;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.product.controller.ProductImageCenterController;
import com.xsy.scm.product.domain.vo.ProductImageMatchTargetVO;
import com.xsy.scm.product.permission.ProductPermission;
import com.xsy.scm.product.domain.form.ProductImageForm;
import com.xsy.scm.product.domain.form.ProductImageCenterForms;
import com.xsy.scm.product.domain.form.ProductSkuForm;
import com.xsy.scm.product.domain.form.ProductSpuAddForm;
import com.xsy.scm.product.domain.vo.ProductImageCenterVO;
import com.xsy.scm.product.domain.vo.ProductImageVO;
import com.xsy.scm.product.manager.ProductImageChangeSet;
import com.xsy.scm.product.manager.ProductImageSyncManager;
import com.xsy.scm.product.service.ProductImageCenterService;
import com.xsy.scm.product.service.ProductSpuService;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.admin.test.PgITPaths;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import net.lab1024.sa.base.module.support.file.constant.FileFolderTypeEnum;
import net.lab1024.sa.base.module.support.file.service.FileService;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * 图片中心的真实库集成测试：验证写操作全部收敛到既有同步链路，
 * public/image/ 前缀与「每 SPU 至多一张主图」不被绕过。
 *
 * <p>V49 之后主图唯一事实只有 {@code is_primary}（由部分唯一索引在库里保证）；
 * {@code image_type} 只表达图集 / 详情图的内容角色，不随主图切换而变。
 */
@SpringBootTest(classes = AdminApplication.class, properties = {
        "project.log-directory=" + PgITPaths.DEFAULT_LOG_DIR,
        "file.storage.local.upload-path=" + PgITPaths.DEFAULT_UPLOAD_PATH,
        "file.storage.local.url-prefix=http://127.0.0.1:18082",
        "logging.level.root=WARN"})
@Transactional
class ProductImageCenterPgIT {
    @Autowired
    ProductImageCenterService service;
    @Autowired
    ProductImageSyncManager syncManager;
    @Autowired
    ProductSpuService spus;
    @Autowired
    FileService files;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    SqlSessionTemplate sqlSession;
    private String prefix;

    @BeforeEach
    void operator() {
        prefix = "IC-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT);
        var employee = new RequestEmployee();
        employee.setEmployeeId(1L);
        employee.setActualName("IC IT");
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        SmartRequestUtil.setRequestUser(employee);
    }

    @AfterEach
    void clearOperator() {
        SmartRequestUtil.remove();
    }

    private Long newSpu() {
        return newSpu("");
    }

    private Long newSpu(String suffix) {
        String code = prefix + suffix;
        var form = new ProductSpuAddForm();
        form.setSpuCode(code);
        form.setName(code + "商品");
        form.setCategoryId(jdbc.queryForObject(
                "SELECT id FROM xsy_product_category WHERE category_code='FRESH-FRUIT' AND deleted=FALSE", Long.class));
        form.setStatus("OFF_SHELF");
        var sku = new ProductSkuForm();
        sku.setSkuCode(code + "A");
        sku.setSpecName("大");
        sku.setSaleUnit("kg");
        sku.setProductType("NON_STANDARD");
        sku.setMarketPrice(new java.math.BigDecimal("1.2000"));
        sku.setStatus("ON_SHELF");
        sku.setDefaultFlag(true);
        sku.setSortOrder(0);
        form.setSkuList(new ArrayList<>(List.of(sku)));
        form.setImages(new ArrayList<>(List.of(upload("a"))));
        return spus.add(form);
    }

    private ProductImageForm upload(String name) {
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aRZkAAAAASUVORK5CYII=");
        var uploaded = files.fileUpload(new MockMultipartFile("file", "ic-" + name + ".png", "image/png", png),
                FileFolderTypeEnum.PUBLIC_IMAGE.getValue(), SmartRequestUtil.getRequestUser());
        assertThat(uploaded.getOk()).isTrue();
        var image = new ProductImageForm();
        image.setFileKey(uploaded.getData().getFileKey());
        image.setPrimaryFlag(true);
        return image;
    }

    private String key(String name) {
        return upload(name).getFileKey();
    }

    private List<Long> imageIds(Long spuId) {
        return service.query(spuId).getImages().stream().map(ProductImageVO::getImageId).toList();
    }

    @Test
    void queryReturnsDerivedUrlWithoutPersistingIt() {
        Long spuId = newSpu();
        ProductImageCenterVO vo = service.query(spuId);
        // V147 起 SPU 编码由后端发号，造数传入的字符串不再落库，只能与落库值比较
        assertThat(vo.getSpuCode()).isEqualTo(
                jdbc.queryForObject("SELECT spu_code FROM xsy_product_spu WHERE id = ?", String.class, spuId));
        assertThat(vo.getImages()).hasSize(1);
        assertThat(vo.getImages().getFirst().getFileUrl()).isNotBlank();
        String fileKey = jdbc.queryForObject("SELECT file_key FROM xsy_product_image WHERE spu_id=?", String.class, spuId);
        assertThat(fileKey).startsWith("public/image/");
        // file_url 列已被删除，URL 只能由 fileKey 现算、绝不入库
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_name='xsy_product_image' AND column_name='file_url'",
                Integer.class)).isZero();
    }

    @Test
    void batchBindAddsGalleryImageWithoutStealingPrimary() {
        Long spuId = newSpu();
        var bind = new ProductImageCenterForms.BatchBindForm();
        var item = new ProductImageCenterForms.BindItem();
        item.setSpuId(spuId);
        item.setFileKey(key("b"));
        item.setPrimaryFlag(false);
        item.setSortOrder(1);
        bind.setItems(new ArrayList<>(List.of(item)));
        service.batchBind(bind);
        var after = service.query(spuId);
        assertThat(after.getImages()).hasSize(2);
        assertThat(after.getImages()).filteredOn(i -> Boolean.TRUE.equals(i.getPrimaryFlag())).hasSize(1);
        assertThat(after.getImages().getFirst().getPrimaryFlag()).isTrue();
        // 非主图的绑定行不再被降级成 DETAIL：图集 / 详情图是内容角色，与谁是主图无关
        assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_product_image WHERE spu_id=? AND image_type='GALLERY' AND deleted=FALSE",
                Integer.class, spuId)).isEqualTo(2);
    }

    @Test
    void batchBindNamesImagesBySpuCodeAndIncrementsDuplicates() {
        Long spuId = newSpu();
        String spuCode = jdbc.queryForObject("SELECT spu_code FROM xsy_product_spu WHERE id = ?", String.class, spuId);
        var bind = new ProductImageCenterForms.BatchBindForm();
        bind.setItems(new ArrayList<>(
                List.of(bindItem(spuId, key("rename-a"), false), bindItem(spuId, key("rename-b"), false))));
        service.batchBind(bind);

        List<String> names = jdbc.queryForList(
                "SELECT file_name FROM xsy_product_image WHERE spu_id=? AND deleted=FALSE ORDER BY id", String.class, spuId);
        // 新绑定的图片不再沿用上传文件名，而是按商品编码生成；同商品多张依次递增
        assertThat(names).contains(spuCode + ".png", spuCode + "-2.png");
        assertThat(names).as("夹具主图保持上传时的原始名称").contains("ic-a.png");
    }

    @Test
    void setPrimaryDemotesPreviousAndKeepsTieConstraint() {
        Long spuId = newSpu();
        var bind = new ProductImageCenterForms.BatchBindForm();
        var item = new ProductImageCenterForms.BindItem();
        item.setSpuId(spuId);
        item.setFileKey(key("b"));
        item.setPrimaryFlag(false);
        item.setSortOrder(1);
        bind.setItems(new ArrayList<>(List.of(item)));
        service.batchBind(bind);
        Long second = service.query(spuId).getImages().get(1).getImageId();
        var setPrimary = new ProductImageCenterForms.SetPrimaryForm();
        setPrimary.setSpuId(spuId);
        setPrimary.setImageId(second);
        service.setPrimary(setPrimary);
        var after = service.query(spuId);
        assertThat(after.getImages().get(1).getPrimaryFlag()).isTrue();
        assertThat(after.getImages().get(0).getPrimaryFlag()).isFalse();
        // 降级后的旧主图仍是图集图：类型不随主图状态漂移，is_primary 是唯一主图事实
        assertThat(jdbc.queryForObject("SELECT image_type FROM xsy_product_image WHERE id=?", String.class, second)).isEqualTo("GALLERY");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_product_image WHERE spu_id=? AND image_type='GALLERY' AND deleted=FALSE",
                Integer.class, spuId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_product_image WHERE spu_id=? AND is_primary AND deleted=FALSE",
                Integer.class, spuId)).isEqualTo(1);
    }

    @Test
    void removingPrimaryPromotesNothingButKeepsSinglePrimaryInvariant() {
        Long spuId = newSpu();
        Long only = imageIds(spuId).getFirst();
        var bind = new ProductImageCenterForms.BatchBindForm();
        var item = new ProductImageCenterForms.BindItem();
        item.setSpuId(spuId);
        item.setFileKey(key("c"));
        item.setPrimaryFlag(false);
        bind.setItems(new ArrayList<>(List.of(item)));
        service.batchBind(bind);
        var remove = new ProductImageCenterForms.BatchRemoveForm();
        remove.setSpuId(spuId);
        remove.setImageIds(new ArrayList<>(List.of(only)));
        service.batchRemove(remove);
        var after = service.query(spuId);
        assertThat(after.getImages()).hasSize(1);
        assertThat(after.getImages().getFirst().getPrimaryFlag()).isFalse();
    }

    @Test
    void reorderAppliesTargetOrder() {
        Long spuId = newSpu();
        var bind = new ProductImageCenterForms.BatchBindForm();
        var item = new ProductImageCenterForms.BindItem();
        item.setSpuId(spuId);
        item.setFileKey(key("d"));
        item.setSortOrder(1);
        bind.setItems(new ArrayList<>(List.of(item)));
        service.batchBind(bind);
        List<Long> ids = imageIds(spuId);
        var reorder = new ProductImageCenterForms.ReorderForm();
        reorder.setSpuId(spuId);
        reorder.setOrderedImageIds(new ArrayList<>(List.of(ids.get(1), ids.get(0))));
        service.reorder(reorder);
        assertThat(imageIds(spuId)).containsExactly(ids.get(1), ids.get(0));
    }

    @Test
    void rejectsForeignKeyInRemoveSetPrimaryReorder() {
        Long spuId = newSpu();
        Long foreign = newSpu("-F");
        Long foreignImage = imageIds(foreign).getFirst();

        var remove = new ProductImageCenterForms.BatchRemoveForm();
        remove.setSpuId(spuId);
        remove.setImageIds(new ArrayList<>(List.of(foreignImage)));
        conflict(() -> service.batchRemove(remove), 40922);

        var setPrimary = new ProductImageCenterForms.SetPrimaryForm();
        setPrimary.setSpuId(spuId);
        setPrimary.setImageId(foreignImage);
        conflict(() -> service.setPrimary(setPrimary), 40922);

        var reorder = new ProductImageCenterForms.ReorderForm();
        reorder.setSpuId(spuId);
        reorder.setOrderedImageIds(new ArrayList<>(List.of(foreignImage)));
        conflict(() -> service.reorder(reorder), 40922);
    }

    @Test
    void rejectsBindingPrivateDirectoryKey() {
        Long spuId = newSpu();
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aRZkAAAAASUVORK5CYII=");
        var uploaded = files.fileUpload(new MockMultipartFile("file", "ic-private.png", "image/png", png),
                FileFolderTypeEnum.COMMON.getValue(), SmartRequestUtil.getRequestUser());
        var bind = new ProductImageCenterForms.BatchBindForm();
        var item = new ProductImageCenterForms.BindItem();
        item.setSpuId(spuId);
        item.setFileKey(uploaded.getData().getFileKey());
        bind.setItems(new ArrayList<>(List.of(item)));
        conflict(() -> service.batchBind(bind), 40038);
    }

    @Test
    void rejectsUnknownSpu() {
        var setPrimary = new ProductImageCenterForms.SetPrimaryForm();
        setPrimary.setSpuId(-999L);
        setPrimary.setImageId(1L);
        conflict(() -> service.setPrimary(setPrimary), 40420);
    }

    /**
     * V49 迁移形状：「类型 = 主图」的一致性 CHECK 已移除，主图唯一性改由部分唯一索引保证，
     * 服务层新写入的行归类为图集图。
     */
    @Test
    void galleryModelConstraintsAreApplied() {
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint WHERE conname='ck_product_image_type_primary'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE indexname='uq_product_image_primary_spu'", Integer.class)).isEqualTo(1);
        Long spuId = newSpu();
        assertThat(jdbc.queryForObject("SELECT image_type FROM xsy_product_image WHERE spu_id=? AND deleted=FALSE",
                String.class, spuId)).isEqualTo("GALLERY");
    }

    @Test
    void legacyPrimaryValueIsRejectedAndDetailStillWritable() {
        Long spuId = newSpu();
        Long image = imageIds(spuId).getFirst();
        // 详情图仍是合法取值（类型只表达内容角色，与 is_primary 无关）
        jdbc.update("UPDATE xsy_product_image SET image_type='DETAIL' WHERE id=?", image);
        // 迁移后写回 PRIMARY 必须直接失败，否则会悄悄出现第二个「主图事实」
        assertThatThrownBy(() -> jdbc.update("UPDATE xsy_product_image SET image_type='PRIMARY' WHERE id=?", image))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void primaryUniquenessIgnoresImageType() {
        Long spuId = newSpu();
        var bind = new ProductImageCenterForms.BatchBindForm();
        var item = new ProductImageCenterForms.BindItem();
        item.setSpuId(spuId);
        item.setFileKey(key("e"));
        item.setPrimaryFlag(false);
        item.setSortOrder(1);
        bind.setItems(new ArrayList<>(List.of(item)));
        service.batchBind(bind);
        Long second = imageIds(spuId).get(1);
        jdbc.update("UPDATE xsy_product_image SET image_type='DETAIL' WHERE id=?", second);
        // 唯一索引只看 is_primary：图片归类成详情图也不能绕出「两张主图」
        assertThatThrownBy(() -> jdbc.update("UPDATE xsy_product_image SET is_primary=TRUE WHERE id=?", second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * 把既有行改成详情图：库里历史数据本来就有这个取值，用例用它复现「已存在 DETAIL 行」的前置状态。
     * 走裸 SQL 是因为当前没有任何写入口会产出详情图，这正是要保护的现状。
     */
    private void markDetail(Long imageId) {
        assertThat(jdbc.update("UPDATE xsy_product_image SET image_type='DETAIL' WHERE id=?", imageId)).isEqualTo(1);
    }

    private String typeOf(Long imageId) {
        return jdbc.queryForObject("SELECT image_type FROM xsy_product_image WHERE id=?", String.class, imageId);
    }

    private Long addImage(Long spuId, String name, int sortOrder) {
        var before = imageIds(spuId);
        var bind = new ProductImageCenterForms.BatchBindForm();
        var item = new ProductImageCenterForms.BindItem();
        item.setSpuId(spuId);
        item.setFileKey(key(name));
        item.setPrimaryFlag(false);
        item.setSortOrder(sortOrder);
        bind.setItems(new ArrayList<>(List.of(item)));
        service.batchBind(bind);
        return imageIds(spuId).stream().filter(id -> !before.contains(id)).findFirst().orElseThrow();
    }

    @Test
    void setPrimaryOnGalleryKeepsDetailType() {
        Long spuId = newSpu();
        Long detail = addImage(spuId, "detail", 1);
        markDetail(detail);
        Long gallery = addImage(spuId, "gallery", 2);
        var setPrimary = new ProductImageCenterForms.SetPrimaryForm();
        setPrimary.setSpuId(spuId);
        setPrimary.setImageId(gallery);
        service.setPrimary(setPrimary);
        // 换主图只动 is_primary：详情图不能因为整批回写而被归类成图集
        assertThat(typeOf(detail)).isEqualTo("DETAIL");
        assertThat(typeOf(gallery)).isEqualTo("GALLERY");
        assertThat(jdbc.queryForObject("SELECT is_primary FROM xsy_product_image WHERE id=?", Boolean.class, gallery)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_product_image WHERE spu_id=? AND image_type='DETAIL' AND deleted=FALSE",
                Integer.class, spuId)).isEqualTo(1);
    }

    @Test
    void reorderKeepsEveryImageType() {
        Long spuId = newSpu();
        Long gallery = imageIds(spuId).getFirst();
        Long detail = addImage(spuId, "detail", 1);
        markDetail(detail);
        var reorder = new ProductImageCenterForms.ReorderForm();
        reorder.setSpuId(spuId);
        reorder.setOrderedImageIds(new ArrayList<>(List.of(detail, gallery)));
        service.reorder(reorder);
        assertThat(imageIds(spuId)).containsExactly(detail, gallery);
        assertThat(typeOf(detail)).isEqualTo("DETAIL");
        assertThat(typeOf(gallery)).isEqualTo("GALLERY");
    }

    @Test
    void bindingNewGalleryImageLeavesExistingDetailType() {
        Long spuId = newSpu();
        Long detail = imageIds(spuId).getFirst();
        markDetail(detail);
        Long added = addImage(spuId, "fresh", 1);
        // 新上传的普通商品图仍按图集归类，既存详情图不受整批回写影响
        assertThat(typeOf(added)).isEqualTo("GALLERY");
        assertThat(typeOf(detail)).isEqualTo("DETAIL");
    }

    @Test
    void batchRemoveLeavesSurvivingDetailType() {
        Long spuId = newSpu();
        Long detail = imageIds(spuId).getFirst();
        markDetail(detail);
        Long extra = addImage(spuId, "extra", 1);
        var remove = new ProductImageCenterForms.BatchRemoveForm();
        remove.setSpuId(spuId);
        remove.setImageIds(new ArrayList<>(List.of(extra)));
        service.batchRemove(remove);
        assertThat(imageIds(spuId)).containsExactly(detail);
        assertThat(typeOf(detail)).isEqualTo("DETAIL");
    }

    /** 内容角色是库内事实，不接受表单申报：即使回写行谎称自己是图集图，同步链也必须按现值落库。 */
    @Test
    void syncIgnoresImageTypeClaimedByExistingRow() {
        Long spuId = newSpu();
        Long detail = imageIds(spuId).getFirst();
        markDetail(detail);
        var row = service.query(spuId).getImages().getFirst();
        var form = new ProductImageForm();
        form.setImageId(row.getImageId());
        form.setVersion(row.getVersion());
        form.setFileKey(row.getFileKey());
        form.setPrimaryFlag(row.getPrimaryFlag());
        form.setSortOrder(row.getSortOrder());
        form.setImageType("GALLERY");
        syncManager.sync(spuId, ProductImageChangeSet.between(syncManager.existing(spuId), new ArrayList<>(List.of(form))));
        assertThat(typeOf(detail)).isEqualTo("DETAIL");
    }

    /**
     * 换绑自己就要把「两张主图」和「同一 fileKey 重复绑」挡在写库之前。
     *
     * <p>图片中心不经 {@code validateSpu} 那条路径，这两条规则必须在写库前自己挡：主图唯一
     * 只能靠 V49 的 {@code uq_product_image_primary_spu} 在库里炸出来——抛的是未捕获的
     * {@code DataIntegrityViolationException}（HTTP 500），而不是稳定的 IMAGE_INVALID；
     * 重复 key 则根本没有库级约束，会静默插成两张同图。
     */
    @Test
    void batchBindRejectsSecondPrimaryAndDuplicateKeyBeforeHittingTheUniqueIndex() {
        Long spuId = newSpu();
        var before = imageIds(spuId);
        assertThat(before).as("夹具新建的 SPU 自带一张主图").hasSize(1);

        var secondPrimary = new ProductImageCenterForms.BatchBindForm();
        secondPrimary.setItems(new ArrayList<>(List.of(bindItem(spuId, key("second-primary"), true))));
        assertThatThrownBy(() -> service.batchBind(secondPrimary))
                .isInstanceOfSatisfying(ScmBusinessException.class,
                        e -> assertThat(e.getErrorCode().getCode()).isEqualTo(40026))
                .isNotInstanceOf(DataIntegrityViolationException.class);
        assertThat(imageIds(spuId)).as("被拒的换绑不得留下任何图片").isEqualTo(before);

        var duplicateKey = new ProductImageCenterForms.BatchBindForm();
        String same = key("same-key");
        duplicateKey.setItems(new ArrayList<>(List.of(
                bindItem(spuId, same, false), bindItem(spuId, same, false))));
        conflict(() -> service.batchBind(duplicateKey), 40026);
        assertThat(imageIds(spuId)).as("重复 key 不能静默插成两张同图").isEqualTo(before);
    }

    @Test
    void matchTargetsNormalizesCodesAndReturnsOnlyActiveProducts() {
        Long spuId = newSpu();
        String code = jdbc.queryForObject("SELECT spu_code FROM xsy_product_spu WHERE id = ?", String.class, spuId);
        var form = new ProductImageCenterForms.MatchTargetsForm();
        form.setCodes(new ArrayList<>(List.of("  " + code.toLowerCase(Locale.ROOT) + "  ")));

        List<ProductImageMatchTargetVO> rows = service.matchTargets(form);

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getSpuId()).isEqualTo(spuId);
        assertThat(rows.getFirst().getSpuCode()).isEqualTo(code);
    }

    @Test
    void matchTargetsExcludesDeletedProductsAndKeepsCaseAmbiguity() {
        String code = prefix + "-MATCH";
        Long categoryId = jdbc.queryForObject(
                "SELECT id FROM xsy_product_category WHERE category_code='FRESH-FRUIT' AND deleted=FALSE", Long.class);
        jdbc.update("INSERT INTO xsy_product_spu (spu_code,name,category_id,status) VALUES (?,?,?,'OFF_SHELF')",
                code, code + "商品", categoryId);
        jdbc.update("INSERT INTO xsy_product_spu (spu_code,name,category_id,status) VALUES (?,?,?,'OFF_SHELF')",
                code.toLowerCase(Locale.ROOT), code + "商品小写", categoryId);

        var form = new ProductImageCenterForms.MatchTargetsForm();
        form.setCodes(new ArrayList<>(List.of(code)));
        assertThat(service.matchTargets(form)).as("大小写不同的编码都要作为歧义候选返回").hasSize(2);

        jdbc.update("UPDATE xsy_product_spu SET deleted = TRUE WHERE lower(trim(spu_code)) = ?",
                code.toLowerCase(Locale.ROOT));
        // 直连 JdbcTemplate 的写入不会清 MyBatis 会话缓存，手动清掉再验证删除过滤
        sqlSession.clearCache();
        assertThat(service.matchTargets(form)).as("已删除商品不能参与匹配").isEmpty();
    }

    @Test
    void matchTargetsFormRejectsMoreThan500Codes() {
        var form = new ProductImageCenterForms.MatchTargetsForm();
        form.setCodes(new ArrayList<>(Collections.nCopies(501, "SPU0001")));
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(form)).isNotEmpty();
        }
    }

    @Test
    void matchTargetsEndpointRequiresImageQueryPermission() throws NoSuchMethodException {
        var method = ProductImageCenterController.class.getMethod(
                "matchTargets", ProductImageCenterForms.MatchTargetsForm.class);
        var permission = method.getAnnotation(SaCheckPermission.class);
        assertThat(permission).as("批量匹配必须声明功能权限").isNotNull();
        assertThat(permission.value()).contains(ProductPermission.IMAGE_QUERY);
    }

    private ProductImageCenterForms.BindItem bindItem(Long spuId, String fileKey, boolean primary) {
        var item = new ProductImageCenterForms.BindItem();
        item.setSpuId(spuId);
        item.setFileKey(fileKey);
        item.setPrimaryFlag(primary);
        item.setSortOrder(0);
        return item;
    }

    private void conflict(Runnable action, int code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ScmBusinessException.class,
                e -> assertThat(e.getErrorCode().getCode()).isEqualTo(code));
    }
}
