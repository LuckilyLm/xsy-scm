package net.lab1024.sa.admin.module.scm.product;

import net.lab1024.sa.admin.AdminApplication;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.product.domain.form.ProductImageForm;
import net.lab1024.sa.admin.module.scm.product.domain.form.ProductImageCenterForms;
import net.lab1024.sa.admin.module.scm.product.domain.form.ProductSkuForm;
import net.lab1024.sa.admin.module.scm.product.domain.form.ProductSpuAddForm;
import net.lab1024.sa.admin.module.scm.product.domain.vo.ProductImageCenterVO;
import net.lab1024.sa.admin.module.scm.product.domain.vo.ProductImageVO;
import net.lab1024.sa.admin.module.scm.product.manager.ProductImageChangeSet;
import net.lab1024.sa.admin.module.scm.product.manager.ProductImageSyncManager;
import net.lab1024.sa.admin.module.scm.product.service.ProductImageCenterService;
import net.lab1024.sa.admin.module.scm.product.service.ProductSpuService;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.admin.test.PgITPaths;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import net.lab1024.sa.base.module.support.file.constant.FileFolderTypeEnum;
import net.lab1024.sa.base.module.support.file.service.FileService;
import org.junit.jupiter.api.*;
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
                "SELECT id FROM product_category WHERE category_code='FRESH-FRUIT' AND deleted=FALSE", Long.class));
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
        assertThat(vo.getSpuCode()).isEqualTo(prefix);
        assertThat(vo.getImages()).hasSize(1);
        assertThat(vo.getImages().getFirst().getFileUrl()).isNotBlank();
        String fileKey = jdbc.queryForObject("SELECT file_key FROM product_image WHERE spu_id=?", String.class, spuId);
        assertThat(fileKey).startsWith("public/image/");
        // file_url 列已被删除，URL 只能由 fileKey 现算、绝不入库
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_name='product_image' AND column_name='file_url'",
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
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_image WHERE spu_id=? AND image_type='GALLERY' AND deleted=FALSE",
                Integer.class, spuId)).isEqualTo(2);
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
        assertThat(jdbc.queryForObject("SELECT image_type FROM product_image WHERE id=?", String.class, second)).isEqualTo("GALLERY");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_image WHERE spu_id=? AND image_type='GALLERY' AND deleted=FALSE",
                Integer.class, spuId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_image WHERE spu_id=? AND is_primary AND deleted=FALSE",
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
        assertThat(jdbc.queryForObject("SELECT image_type FROM product_image WHERE spu_id=? AND deleted=FALSE",
                String.class, spuId)).isEqualTo("GALLERY");
    }

    @Test
    void legacyPrimaryValueIsRejectedAndDetailStillWritable() {
        Long spuId = newSpu();
        Long image = imageIds(spuId).getFirst();
        // 详情图仍是合法取值（类型只表达内容角色，与 is_primary 无关）
        jdbc.update("UPDATE product_image SET image_type='DETAIL' WHERE id=?", image);
        // 迁移后写回 PRIMARY 必须直接失败，否则会悄悄出现第二个「主图事实」
        assertThatThrownBy(() -> jdbc.update("UPDATE product_image SET image_type='PRIMARY' WHERE id=?", image))
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
        jdbc.update("UPDATE product_image SET image_type='DETAIL' WHERE id=?", second);
        // 唯一索引只看 is_primary：图片归类成详情图也不能绕出「两张主图」
        assertThatThrownBy(() -> jdbc.update("UPDATE product_image SET is_primary=TRUE WHERE id=?", second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * 把既有行改成详情图：库里历史数据本来就有这个取值，用例用它复现「已存在 DETAIL 行」的前置状态。
     * 走裸 SQL 是因为当前没有任何写入口会产出详情图，这正是要保护的现状。
     */
    private void markDetail(Long imageId) {
        assertThat(jdbc.update("UPDATE product_image SET image_type='DETAIL' WHERE id=?", imageId)).isEqualTo(1);
    }

    private String typeOf(Long imageId) {
        return jdbc.queryForObject("SELECT image_type FROM product_image WHERE id=?", String.class, imageId);
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
        assertThat(jdbc.queryForObject("SELECT is_primary FROM product_image WHERE id=?", Boolean.class, gallery)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_image WHERE spu_id=? AND image_type='DETAIL' AND deleted=FALSE",
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

    private void conflict(Runnable action, int code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ScmBusinessException.class,
                e -> assertThat(e.getErrorCode().getCode()).isEqualTo(code));
    }
}
