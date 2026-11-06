package com.xsy.scm.product;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xsy.scm.admin.XsyApplication;
import com.xsy.scm.admin.module.system.login.domain.RequestEmployee;
import com.xsy.scm.admin.test.PgITPaths;
import com.xsy.scm.base.common.domain.*;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.enumeration.UserTypeEnum;
import com.xsy.scm.base.common.exception.BusinessException;
import com.xsy.scm.base.common.util.XsyRequestUtil;
import com.xsy.scm.base.handler.GlobalExceptionHandler;
import com.xsy.scm.base.module.support.file.constant.FileFolderTypeEnum;
import com.xsy.scm.base.module.support.file.service.FileService;
import com.xsy.scm.base.module.support.securityprotect.service.SecurityFileService;
import com.xsy.scm.common.ScmBaselineMigrationPgITBase;
import com.xsy.scm.common.ScmW3PgITBase;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.error.ScmErrorCode;
import com.xsy.scm.common.error.ScmExportErrorCode;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.handler.ScmExceptionHandler;
import com.xsy.scm.common.no.ScmBusinessNoService;
import com.xsy.scm.common.no.ScmBusinessNoType;
import com.xsy.scm.order.domain.entity.SalesOrderItemEntity;
import com.xsy.scm.order.service.OrderBundleSnapshotService;
import com.xsy.scm.product.constant.ProductErrorCode;
import com.xsy.scm.product.controller.*;
import com.xsy.scm.product.controller.ProductController;
import com.xsy.scm.product.controller.ProductExcelController;
import com.xsy.scm.product.controller.ProductImageCenterController;
import com.xsy.scm.product.dao.*;
import com.xsy.scm.product.dao.ProductBundleComponentDao;
import com.xsy.scm.product.dao.ProductCategoryDao;
import com.xsy.scm.product.dao.ProductImageDao;
import com.xsy.scm.product.dao.ProductSkuDao;
import com.xsy.scm.product.dao.ProductSpuDao;
import com.xsy.scm.product.dao.ProductTagDao;
import com.xsy.scm.product.dao.ProductTagRelationDao;
import com.xsy.scm.product.dao.ProductUomDao;
import com.xsy.scm.product.domain.entity.ProductCategoryEntity;
import com.xsy.scm.product.domain.entity.ProductImageEntity;
import com.xsy.scm.product.domain.entity.ProductSkuEntity;
import com.xsy.scm.product.domain.entity.ProductSpuEntity;
import com.xsy.scm.product.domain.entity.ProductTagEntity;
import com.xsy.scm.product.domain.entity.ProductUomEntity;
import com.xsy.scm.product.domain.form.*;
import com.xsy.scm.product.domain.form.ProductBundleReplaceForm;
import com.xsy.scm.product.domain.form.ProductCategoryAddForm;
import com.xsy.scm.product.domain.form.ProductImageCenterForms;
import com.xsy.scm.product.domain.form.ProductImageForm;
import com.xsy.scm.product.domain.form.ProductSkuForm;
import com.xsy.scm.product.domain.form.ProductSpuAddForm;
import com.xsy.scm.product.domain.form.ProductSpuQueryForm;
import com.xsy.scm.product.domain.form.ProductSpuUpdateForm;
import com.xsy.scm.product.domain.form.ProductTagAddForm;
import com.xsy.scm.product.domain.vo.*;
import com.xsy.scm.product.domain.vo.ProductImageCenterVO;
import com.xsy.scm.product.domain.vo.ProductImageMatchTargetVO;
import com.xsy.scm.product.domain.vo.ProductImageVO;
import com.xsy.scm.product.domain.vo.ProductSkuVO;
import com.xsy.scm.product.domain.vo.ProductSpuTagVO;
import com.xsy.scm.product.domain.vo.ProductSpuVO;
import com.xsy.scm.product.manager.BundleSalePriceAllocator.Component;
import com.xsy.scm.product.manager.BundleSalePriceAllocator.Share;
import com.xsy.scm.product.manager.BundleSalePriceAllocator;
import com.xsy.scm.product.manager.ProductAggregateValidator;
import com.xsy.scm.product.manager.ProductImageChangeSet;
import com.xsy.scm.product.manager.ProductImageSyncManager;
import com.xsy.scm.product.manager.ProductSkuChangeSet;
import com.xsy.scm.product.manager.ProductSkuSyncManager;
import com.xsy.scm.product.permission.ProductPermission;
import com.xsy.scm.product.service.*;
import com.xsy.scm.product.service.ProductBundleService;
import com.xsy.scm.product.service.ProductCategoryService;
import com.xsy.scm.product.service.ProductImageCenterService;
import com.xsy.scm.product.service.ProductImportService.ImportMode;
import com.xsy.scm.product.service.ProductImportService;
import com.xsy.scm.product.service.ProductImportWorkbookSupport;
import com.xsy.scm.product.service.ProductImportWriteService;
import com.xsy.scm.product.service.ProductQueryService;
import com.xsy.scm.product.service.ProductSpuService;
import com.xsy.scm.product.service.ProductTagService;
import com.xsy.scm.product.service.ProductUomService;
import jakarta.validation.Validation;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static com.xsy.scm.product.constant.ProductErrorCode.*;
import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProductTest {

    @Nested
    class ProductAggregateValidatorTest {
        private final ProductAggregateValidator productAggregateValidator = new ProductAggregateValidator();

        static ProductSkuForm sku(String code, boolean primary, String spec) {
            var sku = new ProductSkuForm();
            sku.setSkuCode(code);
            sku.setDefaultFlag(primary);
            sku.setSpecValues(Map.of("规格", spec));
            sku.setMarketPrice(new BigDecimal("1.2000"));
            return sku;
        }

        static ProductSpuAddForm form(ProductSkuForm... skus) {
            var form = new ProductSpuAddForm();
            form.setSkuList(new ArrayList<>(Arrays.asList(skus)));
            return form;
        }

        void rejects(ProductSpuAddForm form, int code) {
            assertThatThrownBy(() -> productAggregateValidator.validateSpu(form)).isInstanceOfSatisfying(ScmBusinessException.class,
                    e -> assertThat(e.getErrorCode().getCode()).isEqualTo(code));
        }

        @Test
        void acceptsZeroPriceAndBlankBarcodes() {
            var a = sku("A", true, "大");
            a.setMarketPrice(BigDecimal.ZERO);
            a.setBarcode(" ");
            var b = sku("B", false, "小");
            b.setBarcode(null);
            assertThatCode(() -> productAggregateValidator.validateSpu(form(a, b))).doesNotThrowAnyException();
        }

        @Test
        void rejectsEmptyAndMissingOrMultipleDefaults() {
            rejects(form(), 40020);
            rejects(form(sku("A", false, "大")), 40021);
            rejects(form(sku("A", true, "大"), sku("B", true, "小")), 40021);
        }

        @Test
        void rejectsNormalizedBarcodes() {
            // 商品规格编码的唯一性由 uk_product_sku_code 唯一索引兜底，ProductSpuService 把
            // 约束冲突翻译成 40022；聚合校验器只负责条码/规格/价格这类行内判据，
            // 这里不再断言「 a 」与「A」在内存里被判重。
            var a = sku("A", true, "大");
            var b = sku("B", false, "小");
            a.setBarcode(" 123 ");
            b.setBarcode("123");
            rejects(form(a, b), 40023);
        }

        @Test
        void rejectsNormalizedSpecificationsAndNegativeOrMissingPrice() {
            var a = sku("A", true, "大");
            var b = sku("B", false, "小");
            a.setSpecValues(Map.of(" SIZE ", " LARGE ", "color", " RED"));
            b.setSpecValues(Map.of("Color", "red ", "size", "large"));
            rejects(form(a, b), 40024);
            a.setMarketPrice(new BigDecimal("-0.0001"));
            rejects(form(a), 40025);
            a.setMarketPrice(null);
            rejects(form(a), 40025);
        }

        @Test
        void rejectsMultiplePrimaryImagesAndRepeatedFileKeys() {
            var form = form(sku("A", true, "大"));
            var a = new ProductImageForm();
            var b = new ProductImageForm();
            a.setFileKey("a");
            b.setFileKey("b");
            a.setPrimaryFlag(true);
            b.setPrimaryFlag(true);
            form.setImages(List.of(a, b));
            rejects(form, 40026);
            b.setPrimaryFlag(false);
            b.setFileKey("a");
            rejects(form, 40026);
        }
    }

    @Nested
    @WebMvcTest(ProductController.class)
    @AutoConfigureMockMvc(addFilters = false)
    @ContextConfiguration(classes = {ProductController.class, ScmExceptionHandler.class, GlobalExceptionHandler.class})
    class ProductControllerTest {
        @Autowired
        MockMvc mvc;
        @MockitoBean
        ProductSpuService service;
        @MockitoBean
        ProductBatchService batch;
        @MockitoBean
        ProductQueryService query;
        @MockitoBean(name = "systemEnvironment")
        SystemEnvironment environment;

        @Test
        void returnsXSYPaginationEnvelope() throws Exception {
            var page = new PageResult<>();
            page.setPageNum(1L);
            page.setPageSize(20L);
            page.setTotal(0L);
            page.setPages(0L);
            page.setList(List.of());
            page.setEmptyFlag(true);
            doReturn(page).when(query).query(any());
            mvc.perform(post("/scm/product/query").contentType(MediaType.APPLICATION_JSON).content("{\"pageNum\":1,\"pageSize\":20}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.list").isArray())
                    .andExpect(jsonPath("$.data.pageNum").value(1)).andExpect(jsonPath("$.data.emptyFlag").value(true));
        }

        @Test
        void validatesPageBoundsAndEmptySku() throws Exception {
            for (String body : List.of("{\"pageNum\":1,\"pageSize\":501}", "{\"pageNum\":0,\"pageSize\":20}"))
                mvc.perform(post("/scm/product/query").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(jsonPath("$.code").value(30001));
            mvc.perform(post("/scm/product/add").contentType(MediaType.APPLICATION_JSON).content("{\"skuList\":[]}"))
                    .andExpect(jsonPath("$.code").value(30001));
        }

        @Test
        void returnsHttp200With40921AndPreservesMissingProductCode() throws Exception {
            doThrow(new ScmBusinessException(VERSION_CONFLICT)).when(service).delete(any());
            mvc.perform(post("/scm/product/delete").contentType(MediaType.APPLICATION_JSON).content("{\"spuId\":1,\"version\":0}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(40921)).andExpect(jsonPath("$.ok").value(false));
            when(query.detail(1L)).thenThrow(new ScmBusinessException(PRODUCT_NOT_FOUND));
            mvc.perform(get("/scm/product/detail/1")).andExpect(jsonPath("$.code").value(40420));
        }

        @Test
        void leavesXSYBusinessAndUnexpectedExceptionsWithGlobalHandler() throws Exception {
            when(environment.isProd()).thenReturn(true);
            when(query.detail(1L)).thenThrow(new BusinessException("system module error"));
            mvc.perform(get("/scm/product/detail/1")).andExpect(jsonPath("$.code").value(10001)).andExpect(jsonPath("$.msg").value("system module error"));
            when(query.detail(2L)).thenThrow(new IllegalStateException("private details"));
            mvc.perform(get("/scm/product/detail/2")).andExpect(jsonPath("$.code").value(10001));
        }
    }

    @Nested
    @SpringBootTest(classes = XsyApplication.class, properties = {
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
            XsyRequestUtil.setRequestUser(employee);
        }

        @AfterEach
        void clearOperator() {
            XsyRequestUtil.remove();
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
                    FileFolderTypeEnum.PUBLIC_IMAGE.getValue(), XsyRequestUtil.getRequestUser());
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
            // V147 起 SPU 编码由后端发号，造数传入的字符串不再写入数据库，只能与写入数据库值比较
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
            // 降级后的旧主图仍是图集图：类型不随主图状态漂移，is_primary 是唯一主图记录
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
        void rejectsBindingNonImageDirectoryKey() {
            Long spuId = newSpu();
            byte[] png = Base64.getDecoder().decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aRZkAAAAASUVORK5CYII=");
            var uploaded = files.fileUpload(new MockMultipartFile("file", "ic-private.png", "image/png", png),
                    FileFolderTypeEnum.COMMON.getValue(), XsyRequestUtil.getRequestUser());
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
            setPrimary.setSpuId(9_000_000_001L);
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
        void detailImageTypeRemainsWritable() {
            Long spuId = newSpu();
            Long image = imageIds(spuId).getFirst();
            // 详情图仍是合法取值（类型只表达内容角色，与 is_primary 无关）
            jdbc.update("UPDATE xsy_product_image SET image_type='DETAIL' WHERE id=?", image);

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

        /** 内容角色是库内记录，不接受表单申报：即使回写行谎称自己是图集图，同步链也必须按现值写入数据库。 */
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

    @Nested
    @SpringBootTest(classes = XsyApplication.class, properties = {
            "project.log-directory=" + PgITPaths.DEFAULT_LOG_DIR,
            "file.storage.local.upload-path=" + PgITPaths.DEFAULT_UPLOAD_PATH,
            "file.storage.local.url-prefix=http://127.0.0.1:18082",
            "logging.level.root=WARN"})
    class ProductImportUpdatePgIT {
        // Legacy headers from previously downloaded templates remain accepted.
        private static final List<String> HEADERS = List.of("模板版本", "SPU ID", "SPU版本", "SKU ID", "SKU版本",
                "SPU编码", "商品名称", "别名", "分类编码", "助记码", "品牌", "产地", "储存方式", "保质期天数", "标签编码",
                "商品上下架", "SKU编码", "条码", "规格名称", "销售单位", "商品类型", "市场价", "SKU上下架", "默认SKU", "排序");
        private static final int SPU_ID = 1;
        private static final int SPU_VERSION = 2;
        private static final int SKU_ID = 3;
        private static final int SKU_VERSION = 4;
        private static final int ALIAS = 7;
        private static final int TAG_CODES = 14;
        private static final int SKU_CODE = 16;
        private static final int BARCODE = 17;
        private static final int MARKET_PRICE = 21;

        @Autowired
        ProductImportService imports;
        @Autowired
        ProductSpuService service;
        @Autowired
        ProductTagService tags;
        @Autowired
        FileService files;
        @Autowired
        JdbcTemplate jdbc;

        private String prefix;
        private int productSeq;
        private String lastProductName;
        private final List<Long> createdSpuIds = new ArrayList<>();
        private final List<Long> createdTagIds = new ArrayList<>();
        private final List<String> createdFileKeys = new ArrayList<>();

        @BeforeEach
        void operator() {
            prefix = "IU-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase(Locale.ROOT);
            var employee = new RequestEmployee();
            employee.setEmployeeId(1L);
            employee.setActualName("Import Update IT");
            employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
            XsyRequestUtil.setRequestUser(employee);
        }

        @AfterEach
        void cleanUp() {
            XsyRequestUtil.remove();
            for (var spuId : createdSpuIds) {
                jdbc.update("DELETE FROM xsy_product_tag_relation WHERE spu_id=?", spuId);
                jdbc.update("DELETE FROM xsy_product_image WHERE spu_id=?", spuId);
                jdbc.update("DELETE FROM xsy_product_sku WHERE spu_id=?", spuId);
                jdbc.update("DELETE FROM xsy_product_spu WHERE id=?", spuId);
            }
            for (var tagId : createdTagIds) jdbc.update("DELETE FROM xsy_product_tag WHERE id=?", tagId);
            // 本类不用测试事务回滚，上传行会真的提交；用例自建的商品图用完即回收，
            // 只留本地磁盘上的探针文件，不给库留下没有业务引用的附件
            for (var fileKey : createdFileKeys) jdbc.update("DELETE FROM t_file WHERE file_key=?", fileKey);
        }

        @Test
        void priceOnlyUpdateKeepsDetailImageType() throws Exception {
            var product = product(true);
            var image = jdbc.queryForList("SELECT id FROM xsy_product_image WHERE spu_id=? AND deleted=FALSE ORDER BY id",
                    Long.class, product).getFirst();
            assertThat(jdbc.queryForObject("SELECT image_type FROM xsy_product_image WHERE id=?", String.class, image))
                    .isEqualTo("GALLERY");
            // 详情图只可能来自历史行或专门的入口：这里用裸 SQL 造出该前置状态
            assertThat(jdbc.update("UPDATE xsy_product_image SET image_type='DETAIL' WHERE id=?", image)).isEqualTo(1);

            var target = skuIdOf(product, 0);
            var result = imports.importFile(updateRows(row(product, target, cell(MARKET_PRICE, "9.0000"))), ImportMode.UPDATE);
            assertThat(result.getTotalErrors()).isZero();
            assertThat(price(target)).isEqualByComparingTo("9.0000");

            // 改价会带着既有图片走同一条同步链：内容角色与主图记录都必须原样保留
            var after = jdbc.queryForMap("SELECT image_type, is_primary, version FROM xsy_product_image WHERE id=?", image);
            assertThat(after.get("image_type")).isEqualTo("DETAIL");
            assertThat(after.get("is_primary")).isEqualTo(true);
            assertThat((Integer) after.get("version")).isGreaterThan(0);
        }

        @Test
        void onlyFilledColumnsChangeAndUnlistedSkuSurvives() throws Exception {
            var product = product();
            var keepMe = skuIdOf(product, 1);
            var result = imports.importFile(updateRows(row(product, keepMe, cell(MARKET_PRICE, "3.4500"))), ImportMode.UPDATE);
            assertThat(result.getTotalErrors()).isZero();
            assertThat(result.getUpdatedProducts()).isEqualTo(1);
            assertThat(result.getMode()).isEqualTo("UPDATE");

            var sku = jdbc.queryForMap("SELECT market_price, sale_unit, product_type, status, is_default, spec_values::text AS specs,"
                    + " version FROM xsy_product_sku WHERE id=?", keepMe);
            assertThat((BigDecimal) sku.get("market_price")).isEqualByComparingTo("3.4500");
            // 文件里这些列全是空白：单位、类型、上下架、默认标记与商品规格资料都必须还是原值（本行是非默认 SKU）
            assertThat(sku.get("sale_unit")).isEqualTo("份");
            assertThat(sku.get("product_type")).isEqualTo("STANDARD");
            assertThat(sku.get("status")).isEqualTo("ON_SHELF");
            assertThat(sku.get("is_default")).isEqualTo(false);
            assertThat(String.valueOf(sku.get("specs"))).contains("1kg/箱");
            assertThat((Integer) sku.get("version")).isGreaterThan(0);

            var spu = jdbc.queryForMap("SELECT name, alias, description, storage_method, version FROM xsy_product_spu WHERE id=?", product);
            assertThat(spu.get("name")).isEqualTo(lastProductName);
            assertThat(spu.get("alias")).isEqualTo("原别名");
            assertThat(spu.get("description")).isEqualTo("原详情描述，不能被只改市场价的导入清空");
            assertThat(spu.get("storage_method")).isEqualTo("CHILLED");
            assertThat((Integer) spu.get("version")).isEqualTo(1);

            // 未出现在文件里的第二个 SKU 不能被删掉，默认标记也必须还留在它身上；标签关系同理
            assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_product_sku WHERE spu_id=? AND deleted=FALSE", Integer.class, product)).isEqualTo(2);
            assertThat(jdbc.queryForList("SELECT id FROM xsy_product_sku WHERE spu_id=? AND deleted=FALSE AND is_default=TRUE", Long.class, product))
                    .containsExactly(skuIdOf(product, 0));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_product_tag_relation WHERE spu_id=? AND deleted=FALSE", Integer.class, product)).isEqualTo(1);
        }

        @Test
        void staleVersionRejectsTheWholeBatchBeforeAnyWrite() throws Exception {
            var product = product();
            var target = skuIdOf(product, 0);
            var result = imports.importFile(updateRows(row(product, target,
                    cell(MARKET_PRICE, "8.8800"), cell(SPU_VERSION, "99"))), ImportMode.UPDATE);
            assertThat(result.getErrors()).anySatisfy(error -> {
                assertThat(error.getCode()).isEqualTo("VERSION_CONFLICT");
                assertThat(error.getColumn()).isEqualTo("商品版本");
            });
            assertThat(result.getUpdatedProducts()).isZero();
            assertThat(price(target)).isEqualByComparingTo("1.2000");
            assertThat(spuVersion(product)).isZero();
        }

        @Test
        void writePhaseConflictRollsBackTheEarlierProductToo() throws Exception {
            var first = product();
            var second = product();
            var firstPriceTarget = skuIdOf(first, 0);
            var secondPriceTarget = skuIdOf(second, 0);
            // 第二个商品的 SKU 改成第一个商品既有条码：唯一索引在写阶段才炸，整批必须一起回滚
            var clash = jdbc.queryForObject("SELECT barcode FROM xsy_product_sku WHERE id=? AND deleted=FALSE", String.class, firstPriceTarget);
            assertThat(clash).isNotBlank();
            var result = imports.importFile(updateRows(
                    row(first, firstPriceTarget, cell(MARKET_PRICE, "5.0000")),
                    row(second, secondPriceTarget, cell(MARKET_PRICE, "6.0000"), cell(BARCODE, clash))), ImportMode.UPDATE);
            assertThat(result.getErrors()).singleElement().satisfies(error -> {
                assertThat(error.getCode()).isEqualTo("40023");
                assertThat(error.getMessage()).contains("本批数据未保存");
            });
            assertThat(result.getUpdatedProducts()).isZero();
            assertThat(price(firstPriceTarget)).isEqualByComparingTo("1.2000");
            assertThat(price(secondPriceTarget)).isEqualByComparingTo("1.2000");
            assertThat(spuVersion(first)).isZero();
            assertThat(spuVersion(second)).isZero();
        }

        @Test
        void clearMarkerRemovesMarkedValuesAndKeepsEverythingElse() throws Exception {
            var product = product();
            var target = skuIdOf(product, 0);
            var result = imports.importFile(updateRows(row(product, target,
                    cell(ALIAS, ProductImportService.CLEAR_TOKEN), cell(TAG_CODES, ProductImportService.CLEAR_TOKEN),
                    cell(BARCODE, ProductImportService.CLEAR_TOKEN))), ImportMode.UPDATE);
            assertThat(result.getTotalErrors()).isZero();
            assertThat(jdbc.queryForObject("SELECT alias FROM xsy_product_spu WHERE id=?", String.class, product)).isNull();
            assertThat(jdbc.queryForObject("SELECT barcode FROM xsy_product_sku WHERE id=?", String.class, target)).isNull();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_product_tag_relation WHERE spu_id=? AND deleted=FALSE",
                    Integer.class, product)).isZero();
            // 未标记的列一个字都没动：描述与市场价仍是原值
            assertThat(jdbc.queryForObject("SELECT description FROM xsy_product_spu WHERE id=?", String.class, product))
                    .isEqualTo("原详情描述，不能被只改市场价的导入清空");
            assertThat(price(target)).isEqualByComparingTo("1.2000");
        }

        // ------------------------------------------------------------------
        // fixtures
        // ------------------------------------------------------------------

        /** 两 SKU 商品：首个 SKU 为默认且带条码，另带别名、描述与标签，作为「不能被导入抹掉」的原值。 */
        private Long product() {
            return product(false);
        }

        private Long product(boolean withImage) {
            productSeq++;
            lastProductName = code("A") + " 商品";
            var form = new ProductSpuAddForm();
            form.setSpuCode(code("SPU"));
            form.setName(lastProductName);
            form.setAlias("原别名");
            form.setDescription("原详情描述，不能被只改市场价的导入清空");
            form.setStorageMethod("CHILLED");
            form.setCategoryId(jdbc.queryForObject("SELECT id FROM xsy_product_category WHERE category_code='FRESH-FRUIT' AND deleted=FALSE", Long.class));
            form.setStatus("OFF_SHELF");
            var tagId = tag();
            form.setTagIds(List.of(tagId));
            var primary = sku(true, "1", "500g/份");
            var extra = sku(false, "2", "1kg/箱");
            form.setSkuList(new ArrayList<>(List.of(primary, extra)));
            if (withImage) form.setImages(new ArrayList<>(List.of(galleryImage())));
            var id = service.add(form);
            createdSpuIds.add(id);
            return id;
        }

        /** 商品图必须引用文件模块里真实存在的 public/image/ 对象，所以夹具走真上传而不是编造 fileKey。 */
        private ProductImageForm galleryImage() {
            byte[] png = Base64.getDecoder().decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aRZkAAAAASUVORK5CYII=");
            var uploaded = files.fileUpload(new MockMultipartFile("file", "iu-" + prefix + productSeq + ".png", "image/png", png),
                    FileFolderTypeEnum.PUBLIC_IMAGE.getValue(), XsyRequestUtil.getRequestUser());
            assertThat(uploaded.getOk()).isTrue();
            createdFileKeys.add(uploaded.getData().getFileKey());
            var image = new ProductImageForm();
            image.setFileKey(uploaded.getData().getFileKey());
            image.setPrimaryFlag(true);
            image.setSortOrder(0);
            return image;
        }

        private ProductSkuForm sku(boolean defaultFlag, String tag, String specName) {
            var sku = new ProductSkuForm();
            sku.setSkuCode(code("SKU" + tag));
            sku.setBarcode(code("BAR" + tag));
            sku.setSpecName(specName);
            sku.setSpecValues(Map.of("规格", specName));
            sku.setSaleUnit("份");
            sku.setProductType("STANDARD");
            sku.setMarketPrice(new BigDecimal("1.2000"));
            sku.setStatus("ON_SHELF");
            sku.setDefaultFlag(defaultFlag);
            sku.setSortOrder(defaultFlag ? 0 : 1);
            return sku;
        }

        private Long tag() {
            var form = new ProductTagAddForm();
            form.setTagCode(code("TAG"));
            form.setName(form.getTagCode());
            form.setStatus("ENABLED");
            var id = tags.add(form);
            createdTagIds.add(id);
            return id;
        }

        private String code(String suffix) {
            return prefix + productSeq + "-" + suffix;
        }

        /** 商品 SKU 按 id 升序稳定定位，避免依赖插入顺序的不确定返回。 */
        private Long skuIdOf(Long spuId, int index) {
            return jdbc.queryForList("SELECT id FROM xsy_product_sku WHERE spu_id=? AND deleted=FALSE ORDER BY id", Long.class, spuId).get(index);
        }

        private Integer skuVersion(Long skuId) {
            return jdbc.queryForObject("SELECT version FROM xsy_product_sku WHERE id=?", Integer.class, skuId);
        }

        private int spuVersion(Long spuId) {
            return jdbc.queryForObject("SELECT version FROM xsy_product_spu WHERE id=?", Integer.class, spuId);
        }

        private BigDecimal price(Long skuId) {
            return jdbc.queryForObject("SELECT market_price FROM xsy_product_sku WHERE id=?", BigDecimal.class, skuId);
        }

        private String[] row(Long spuId, Long skuId, String[]... overrides) {
            var cells = new String[HEADERS.size()];
            Arrays.fill(cells, "");
            cells[0] = ProductImportService.TEMPLATE_VERSION;
            cells[SPU_ID] = String.valueOf(spuId);
            cells[SPU_VERSION] = String.valueOf(spuVersion(spuId));
            cells[SKU_ID] = String.valueOf(skuId);
            cells[SKU_VERSION] = String.valueOf(skuVersion(skuId));
            cells[SKU_CODE] = jdbc.queryForObject("SELECT sku_code FROM xsy_product_sku WHERE id=?", String.class, skuId);
            for (var override : overrides) cells[Integer.parseInt(override[0])] = override[1];
            return cells;
        }

        private String[] cell(int column, String value) {
            return new String[]{String.valueOf(column), value};
        }

        private MockMultipartFile updateRows(String[]... rows) throws Exception {
            try (var book = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
                var sheet = book.createSheet("商品更新导入");
                var header = sheet.createRow(0);
                for (int column = 0; column < HEADERS.size(); column++) header.createCell(column).setCellValue(HEADERS.get(column));
                int number = 1;
                for (var row : rows) {
                    var excelRow = sheet.createRow(number++);
                    for (int column = 0; column < row.length; column++) excelRow.createCell(column).setCellValue(row[column]);
                }
                book.write(out);
                return new MockMultipartFile("file", "update.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
            }
        }
    }

    @Nested
    @SpringBootTest(classes = XsyApplication.class, properties = {
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
            XsyRequestUtil.setRequestUser(employee);
        }

        @AfterEach
        void clearOperator() {
            XsyRequestUtil.remove();
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
            form.setCategoryId(jdbc.queryForObject("SELECT id FROM xsy_product_category WHERE category_code='FRESH-FRUIT' AND deleted=FALSE", Long.class));
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
            return jdbc.queryForObject("SELECT id FROM xsy_product_sku WHERE spu_id=? AND deleted=FALSE", Long.class, spuId);
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
            jdbc.update("UPDATE xsy_product_sku SET sale_unit='板' WHERE spu_id=?", spu);
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
        void deleteIsBlockedOnceTheProductHasPricingOrBusinessFacts() {
            Long clean = service.add(product("kg"));
            service.delete(deletion(clean));
            conflict(() -> query.detail(clean), PRODUCT_NOT_FOUND);
            Long priced = service.add(product("kg"));
            jdbc.update("INSERT INTO xsy_customer_agreement_price(customer_id,sku_id,unit_price,effective_from,created_by) VALUES (?,?,1.5,?,?)",
                    1L, skuId(priced), OffsetDateTime.now(), "pco1-it");
            conflict(() -> service.delete(deletion(priced)), PRODUCT_BUSINESS_REFERENCED);
            // 撤销价格行也不解锁：删除保护看的是「是否产生过记录」，不是「现在还挂着价格」。
            jdbc.update("UPDATE xsy_customer_agreement_price SET deleted=TRUE WHERE sku_id=?", skuId(priced));
            conflict(() -> service.delete(deletion(priced)), PRODUCT_BUSINESS_REFERENCED);
        }

        /**
         * 供应商关系与客户可见性只是主数据关联，不拦删除。
         */
        @Test
        void supplierRelationDoesNotBlockProductDeletion() {
            Long linked = service.add(product("kg"));
            // 本库不建外键，关联行只需存在即可验证删除规则，不依赖 supplier 种子数据。
            jdbc.update("INSERT INTO xsy_supplier_sku(supplier_id,sku_id,supplier_code_snapshot,supplier_name_snapshot,sku_code_snapshot,sku_name_snapshot,purchase_unit) "
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
            status.setStatus("ON_SHELF");
            var partial = batch.updateStatus(status);
            assertThat(partial.getUpdatedCount()).isZero();
            assertThat(partial.getFailures()).extracting(ProductBatchResultVO.Failure::getSpuId).containsExactly(b);
            assertThat(query.detail(a).getStatus()).isEqualTo("OFF_SHELF");
            status.setItems(List.of(item(a, version(a)), item(b, version(b))));
            assertThat(batch.updateStatus(status).getUpdatedCount()).isEqualTo(2);
            assertThat(query.detail(a).getStatus()).isEqualTo("ON_SHELF");
            assertThat(query.detail(b).getVersion()).isEqualTo(1);
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
            var byStorage = search();
            byStorage.setStorageMethod("CHILLED");
            assertThat(hits(byStorage)).containsExactly(spu);
            var otherStorage = search();
            otherStorage.setStorageMethod("FROZEN");
            assertThat(hits(otherStorage)).isEmpty();
            // 直连改库不会清 MyBatis 一级缓存，所以下面三个探测必须用互不相同的条件组合。
            assertThat(hits(flag(false, false))).containsExactly(spu);
            jdbc.update("UPDATE xsy_product_sku SET barcode=? WHERE spu_id=?", prefix + "BAR", spu);
            assertThat(hits(flag(true, false))).containsExactly(spu);
            assertThat(hits(flag(true, true))).isEmpty();
            jdbc.update("UPDATE xsy_product_spu SET created_at=now()-interval '7 days' WHERE id=?", spu);
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

    @Nested
    @SpringBootTest(classes = XsyApplication.class, properties = {
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
            XsyRequestUtil.setRequestUser(employee);
        }

        @AfterEach
        void clearOperator() {
            XsyRequestUtil.remove();
        }

        private ProductSpuAddForm product() {
            var form = new ProductSpuAddForm();
            form.setSpuCode(prefix);
            form.setName(prefix + "商品");
            form.setAlias(prefix + "别名");
            form.setCategoryId(jdbc.queryForObject("SELECT id FROM xsy_product_category WHERE category_code='FRESH-FRUIT' AND deleted=FALSE", Long.class));
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
        void omittedSaleUnitDefaultsToJin() {
            var form = product();
            var defaultUnit = ProductAggregateValidatorTest.sku(prefix + "J", true, "默认单位");
            defaultUnit.setSpecName("默认单位");
            defaultUnit.setProductType("NON_STANDARD");
            defaultUnit.setStatus("ON_SHELF");
            // 不设置 saleUnit：新建表单字段默认斤，服务端按这个值校验单位字典并写入数据库
            form.setSkuList(new ArrayList<>(List.of(defaultUnit)));

            Long id = service.add(form);

            assertThat(query.detail(id).getDefaultSku().getSaleUnit()).isEqualTo("斤");
        }

        @Test
        void explicitBlankSaleUnitStillFailsBeanValidation() {
            var sku = ProductAggregateValidatorTest.sku(prefix + "BLANK", true, "空白单位");
            sku.setSaleUnit(" ");
            try (var factory = Validation.buildDefaultValidatorFactory()) {
                assertThat(factory.getValidator().validate(sku))
                        .anyMatch(violation -> "saleUnit".equals(violation.getPropertyPath().toString()));
            }
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
            // V147：新增规格由服务端发号，客户端传入的编码不被采信，且与保留规格的编码不同
            String addedSkuCode = saved.getDefaultSku().getSkuCode();
            assertThat(addedSkuCode).isNotEqualTo(prefix + "C");
            assertThat(addedSkuCode).isNotEqualTo(initial.getSkuList().getFirst().getSkuCode());
            assertThat(jdbc.queryForObject("SELECT deleted FROM xsy_product_sku WHERE id=?", Boolean.class, removed)).isTrue();
            assertThat(jdbc.queryForObject("SELECT created_by FROM xsy_product_spu WHERE id=?", String.class, id)).isEqualTo("1:1");
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
            jdbc.update("UPDATE xsy_product_sku SET version=version+1 WHERE id=?", form.getSkuList().getFirst().getSkuId());
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
            assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_product_sku WHERE spu_id=? AND deleted=TRUE", Integer.class, id)).isEqualTo(2);
            conflict(() -> query.detail(id), 40420);
            assertThat(service.add(form)).isNotEqualTo(id);
        }

        @Test
        void searchesEveryLegacyField() {
            Long id = service.add(product());
            var detail = query.detail(id);
            // V147 起 SPU / SKU 编码由后端发号，按编码检索只能用写入数据库值，不能沿用造数时传入的字符串
            List<String> keywords = List.of(
                    "",
                    detail.getSpuCode(),
                    prefix + "商品",
                    prefix + "别名",
                    detail.getSkuList().getFirst().getSkuCode(),
                    prefix + "BARA");
            for (String keyword : keywords) {
                var form = new ProductSpuQueryForm();
                form.setPageNum(1L);
                form.setPageSize(20L);
                form.setKeyword(keyword);
                assertThat(query.query(form).getList()).as("keyword=%s", keyword)
                        .extracting(ProductSpuVO::getSpuId).contains(id);
            }
        }

        @Test
        void ancestorCategoryFilterIsV2EnhancementAndJsonbAndPriceContractSurvive() throws Exception {
            Long id = service.add(product());
            var form = new ProductSpuQueryForm();
            form.setPageNum(1L);
            form.setPageSize(20L);
            form.setKeyword(prefix);
            form.setCategoryId(jdbc.queryForObject("SELECT id FROM xsy_product_category WHERE category_code='FRESH' AND deleted=FALSE", Long.class));
            var page = query.query(form);
            assertThat(page.getList()).extracting(ProductSpuVO::getSpuId).containsExactly(id);
            assertThat(page.getList().getFirst().getCategoryPath()).isEqualTo("生鲜 / 果蔬 / 水果");
            var tree = json.readTree(json.writeValueAsString(query.detail(id)));
            assertThat(tree.at("/skuList/0/marketPrice").asText()).isEqualTo("1.2000");
            assertThat(tree.at("/skuList/0/specValues/规格").asText()).isEqualTo("大");
        }

        private ProductImageForm upload(String name) {
            return upload(name, FileFolderTypeEnum.PUBLIC_IMAGE.getValue());
        }
        private ProductImageForm upload(String name,int folder) {
            byte[] png=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aRZkAAAAASUVORK5CYII=");
            var uploaded=files.fileUpload(new MockMultipartFile("file",name,"image/png",png),folder,XsyRequestUtil.getRequestUser());
            assertThat(uploaded.getOk()).isTrue();
            var image = new ProductImageForm();
            image.setFileKey(uploaded.getData().getFileKey());
            image.setPrimaryFlag(true);
            return image;
        }

        @Test
        void usesXSYFilesAndSwitchesPrimaryImageWithoutReplacingIdentity() {
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
            // 展示名可以由图片中心或本次编辑按业务需要重写（例如按商品编码重命名），
            // 但绑定的 fileKey 不会跟着变：换的是展示名，不是文件身份。
            assertThat(saved.getImages().getLast().getFileName()).isEqualTo("forged.txt");
            assertThat(saved.getImages().getLast().getFileKey()).isEqualTo(initial.getImages().getLast().getFileKey());
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
        /**
         * 通用目录的 key 虽然在文件模块里真实存在（且同样公开），但它不是图片中心上传的商品图，
         * 绑定会绕过图片中心的校验，因此必须拒绝。
         */
        @Test void rejectsBindingNonImageDirectoryImage() {
            var form=product(); form.setImages(new ArrayList<>(List.of(upload("w1-private.png",FileFolderTypeEnum.COMMON.getValue()))));
            conflict(() -> service.add(form),40038);
        }
        /**
         * 商品图只能引用 public/image/ 前缀，两层各自取证：换绑其它目录由 Java 侧拒绝（40038，前端提示可定位），
         * 绕过服务层直写由数据库 CHECK 拒绝 —— 只有 Java 判断时，任何旁路写入都能重新造出
         * 「非商品图目录的活商品图」，而读侧会按商品资产把它展示给所有查看者。
         */
        @Test void rejectsNonImageDirectoryKeyOnServiceLayer() {
            var legacyKey=upload("w1-legacy-private.png",FileFolderTypeEnum.COMMON.getValue()).getFileKey();
            var form=product(); form.setImages(new ArrayList<>(List.of(upload("w1-public.png"))));
            Long id=service.add(form);

            var rebind=update(id); rebind.getImages().getFirst().setFileKey(legacyKey);
            conflict(() -> service.update(rebind),40038);

        }
        private Long category(Long parent,String suffix) {
            var form=new ProductCategoryAddForm(); form.setParentId(parent); form.setCategoryCode(prefix+suffix); form.setName(suffix); form.setStatus("ENABLED"); return categories.add(form);
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
    }

    @Nested
    class ProductUnitTest {
        @Nested

        class BundleSalePriceAllocatorTest {
            @Test
            void splitsIndependentBundlePriceByMarketAmountsNotUnitPrices() {
                List<Share> shares = BundleSalePriceAllocator.allocate(price("80.0000"), List.of(
                        component(101L, "2.0000", "10.0000"),
                        component(102L, "3.0000", "10.0000")));

                assertThat(shares).hasSize(2);
                assertThat(shares.get(0).referenceAmount()).isEqualByComparingTo("20.0000");
                assertThat(shares.get(1).referenceAmount()).isEqualByComparingTo("30.0000");
                assertThat(shares.get(0).allocatedSaleAmount()).isEqualByComparingTo("32.0000");
                assertThat(shares.get(1).allocatedSaleAmount()).isEqualByComparingTo("48.0000");
                assertThat(total(shares)).isEqualByComparingTo("80.0000");
            }

            @Test
            void putsRoundingDifferenceInLastComponent() {
                List<Share> shares = BundleSalePriceAllocator.allocate(price("1.0000"), List.of(
                        component(101L, "1.0000", "1.0000"),
                        component(102L, "1.0000", "1.0000"),
                        component(103L, "1.0000", "1.0000")));

                assertThat(shares.get(0).allocatedSaleAmount()).isEqualByComparingTo("0.3333");
                assertThat(shares.get(1).allocatedSaleAmount()).isEqualByComparingTo("0.3333");
                assertThat(shares.get(2).allocatedSaleAmount()).isEqualByComparingTo("0.3334");
                assertThat(total(shares)).isEqualByComparingTo("1.0000");
            }

            @Test
            void tinyAndZeroBundlePricesNeverCreateNegativeAllocations() {
                var components = List.of(component(1L, "1", "1"), component(2L, "1", "1"),
                        component(3L, "1", "1"));
                var tiny = BundleSalePriceAllocator.allocate(price("0.0001"), components);
                assertThat(total(tiny)).isEqualByComparingTo("0.0001");
                assertThat(tiny).allSatisfy(row ->
                        assertThat(row.allocatedSaleAmount()).isGreaterThanOrEqualTo(BigDecimal.ZERO));

                var free = BundleSalePriceAllocator.allocate(price("0.0000"), components);
                assertThat(free).allSatisfy(row ->
                        assertThat(row.allocatedSaleAmount()).isEqualByComparingTo("0.0000"));
                assertThat(total(free)).isEqualByComparingTo("0.0000");
            }

            @Test
            void prohibitsMissingMarketPriceInvalidQuantityAndDuplicateComponents() {
                assertThatThrownBy(() -> BundleSalePriceAllocator.allocate(price("80"), List.of()))
                        .isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> BundleSalePriceAllocator.allocate(price("80"), List.of(
                        component(1L, "1", "10"), component(1L, "2", "20"))))
                        .isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> BundleSalePriceAllocator.allocate(price("80"), List.of(
                        component(1L, "0", "10")))).isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> BundleSalePriceAllocator.allocate(price("80"), List.of(
                        component(1L, "1", "0")))).isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> BundleSalePriceAllocator.allocate(price("80"), List.of(
                        component(1L, "1", null)))).isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> BundleSalePriceAllocator.allocate(price("-1"), List.of(
                        component(1L, "1", "10")))).isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> BundleSalePriceAllocator.allocate(price("1.00001"), List.of(
                        component(1L, "1", "10")))).isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> BundleSalePriceAllocator.allocate(price("100000000000000.0000"), List.of(
                        component(1L, "1", "10")))).isInstanceOf(IllegalArgumentException.class);
            }

            private static Component component(Long sku, String qty, String market) {
                return new Component(sku, price(qty), market == null ? null : price(market));
            }

            private static BigDecimal price(String value) {
                return new BigDecimal(value);
            }

            private static BigDecimal total(List<Share> shares) {
                return shares.stream().map(Share::allocatedSaleAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            }
        }



        @Nested

        class ProductCategoryLevelTest {
            private final ProductCategoryDao productCategoryDao = mock(ProductCategoryDao.class);
            private final ProductCategoryService productCategoryService = new ProductCategoryService(productCategoryDao, mock(com.xsy.scm.common.no.ScmBusinessNoService.class), mock(ProductSpuDao.class));

            @Test
            void derivesLevelFromEnabledParent() {
                var parent = new ProductCategoryEntity();
                parent.setId(1L);
                parent.setLevel(2);
                parent.setStatus("ENABLED");
                when(productCategoryDao.selectById(1L)).thenReturn(parent);
                assertThat(productCategoryService.resolveLevel(1L, null)).isEqualTo(3);
                assertThat(productCategoryService.resolveLevel(null, null)).isEqualTo(1);
            }

            @Test
            void rejectsFourthLevelDisabledSelfAndMissingParents() {
                var parent = new ProductCategoryEntity();
                parent.setId(1L);
                parent.setLevel(3);
                parent.setStatus("ENABLED");
                when(productCategoryDao.selectById(1L)).thenReturn(parent);
                rejects(1L, null, 40010);
                parent.setLevel(1);
                parent.setStatus("DISABLED");
                rejects(1L, null, 40011);
                rejects(1L, 1L, 40011);
                rejects(2L, null, 40410);
            }

            private void rejects(Long parent, Long self, int code) {
                assertThatThrownBy(() -> productCategoryService.resolveLevel(parent, self)).isInstanceOfSatisfying(ScmBusinessException.class,
                        e -> assertThat(e.getErrorCode().getCode()).isEqualTo(code));
            }
        }

        @Nested

        class ProductExportLimitTest {
            private final ProductQueryService query = mock(ProductQueryService.class);
            private final ProductExcelController controller = new ProductExcelController(mock(ProductImportService.class),
                query, mock(SecurityFileService.class));

            @ParameterizedTest
            @ValueSource(booleans = {false, true})
            void rejectsMoreProductsThanTheLimitWithoutStartingAFile(boolean selected) {
                var form = form(selected);
                var page = page(List.of(), 100001L);
                when(query.query(any())).thenReturn(page);
                var response = new MockHttpServletResponse();
                assertLimit(form, response);
                assertThat(form.getPageNum()).isEqualTo(1L);
                assertThat(form.getPageSize()).isEqualTo(100001L);
                assertThat(response.getContentAsByteArray()).isEmpty();
            }

            @ParameterizedTest
            @ValueSource(booleans = {false, true})
            void rejectsTooManyExpandedSkuRows(boolean selected) {
                var spu = product();
                spu.setSkuList(Collections.nCopies(100001, new ProductSkuVO()));
                when(query.query(any())).thenReturn(page(List.of(spu), 1L));
                var response = new MockHttpServletResponse();
                assertLimit(form(selected), response);
                assertThat(response.getContentAsByteArray()).isEmpty();
            }

            @ParameterizedTest
            @ValueSource(booleans = {false, true})
            void completeSmallResultsAreStillReadableWorkbooks(boolean selected) throws Exception {
                when(query.query(any())).thenReturn(page(List.of(product()), 1L));
                var response = new MockHttpServletResponse();
                controller.export(form(selected), response);
                try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(response.getContentAsByteArray()))) {
                    assertThat(workbook.getSheetAt(0).getLastRowNum()).isEqualTo(1);
                    assertThat(workbook.getSheetAt(0).getRow(1).getCell(selected ? 0 : 5).getStringCellValue())
                        .isEqualTo("测试商品");
                }
            }

            private void assertLimit(ProductSpuQueryForm form, MockHttpServletResponse response) {
                assertThatThrownBy(() -> controller.export(form, response))
                    .isInstanceOfSatisfying(ScmBusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ScmExportErrorCode.EXPORT_ROW_LIMIT_EXCEEDED));
            }

            private ProductSpuQueryForm form(boolean selected) {
                var form = new ProductSpuQueryForm();
                if (selected) form.setExportColumnKeys(List.of("spuName"));
                return form;
            }

            private ProductSpuVO product() {
                var spu = new ProductSpuVO();
                spu.setSpuId(1L);
                spu.setName("测试商品");
                spu.setStatus("ENABLED");
                spu.setSkuList(List.of());
                return spu;
            }

            private PageResult<ProductSpuVO> page(List<ProductSpuVO> rows, Long total) {
                var page = new PageResult<ProductSpuVO>();
                page.setList(rows);
                page.setTotal(total);
                return page;
            }
        }

        @Nested

        class ProductImageChangeSetTest {
            @Test
            void primarySwitchPreservesIdentityAndSeparatesRemoval() {
                var a = new ProductImageEntity();
                a.setId(1L);
                var b = new ProductImageEntity();
                b.setId(2L);
                var keep = new ProductImageForm();
                keep.setImageId(2L);
                keep.setVersion(3);
                keep.setPrimaryFlag(true);
                var add = new ProductImageForm();
                var changes = ProductImageChangeSet.between(List.of(a, b), List.of(keep, add));
                assertThat(changes.removedIds()).containsExactly(1L);
                assertThat(changes.updated()).containsExactly(keep);
                assertThat(changes.inserted()).containsExactly(add);
            }

            @Test
            void foreignReferenceIsRejected() {
                var foreign = new ProductImageForm();
                foreign.setImageId(9L);
                foreign.setVersion(0);
                assertThatThrownBy(() -> ProductImageChangeSet.between(List.of(), List.of(foreign)))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(e.getErrorCode().getCode()).isEqualTo(40922));
            }
        }

        @Nested

        class ProductImportServiceTest {
            private final ProductCategoryDao productCategoryDao = mock(ProductCategoryDao.class);
            private final ProductTagDao productTagDao = mock(ProductTagDao.class);
            private final ProductUomDao productUomDao = mock(ProductUomDao.class);
            private final ProductSpuDao productSpuDao = mock(ProductSpuDao.class);
            private final ProductSkuDao productSkuDao = mock(ProductSkuDao.class);
            private final ProductImageDao productImageDao = mock(ProductImageDao.class);
            private final ProductTagService productTagService = mock(ProductTagService.class);
            private final ProductImportWorkbookSupport productImportWorkbookSupport = new ProductImportWorkbookSupport();
            private final ProductImportWriteService productImportWriteService = mock(ProductImportWriteService.class);
            private final ProductImportService productImportService = new ProductImportService(productCategoryDao, productTagDao,
                    productUomDao, productSpuDao, productSkuDao, productImageDao, productTagService,
                    productImportWorkbookSupport, productImportWriteService);

            {
                // CREATE 逐行会预判单位「在字典且 ENABLED」（与 ProductUomService.assertUsable 同判据），
                // 夹具里用到的计量单位都得存在，否则每条用例都会先撞上 UOM_NOT_USABLE。
                var kg = new ProductUomEntity();
                kg.setName("份");
                kg.setStatus("ENABLED");
                var jin = new ProductUomEntity();
                jin.setName("斤");
                jin.setStatus("ENABLED");
                when(productUomDao.selectList(any())).thenReturn(java.util.List.of(kg, jin));
            }

            private static final String[] HEADERS = {"模板版本", "商品编码", "商品名称", "别名", "分类编码", "助记码",
                    "品牌", "产地", "储存方式", "保质期天数", "标签编码", "商品状态", "商品规格编码", "条码", "商品规格名称",
                    "销售单位", "商品类型", "市场价", "商品规格状态", "默认商品规格", "排序"};

            private static final String[] UPDATE_HEADERS = {"模板版本", "商品ID", "商品版本", "商品规格ID", "商品规格版本",
                    "商品编码", "商品名称", "别名", "分类编码", "助记码", "品牌", "产地", "储存方式", "保质期天数", "标签编码",
                    "商品状态", "商品规格编码", "条码", "商品规格名称", "销售单位", "商品类型", "市场价", "商品规格状态", "默认商品规格", "排序"};

            /** 更新模板的列下标：定位键在最前，其余列留空即保持库内原值。 */
            private static final int SPU_VERSION = 2;
            private static final int SKU_ID = 3;
            private static final int SKU_VERSION = 4;
            private static final int SPU_CODE = 5;
            private static final int ALIAS = 7;
            private static final int BRAND_NAME = 10;
            private static final int TAG_CODES = 14;
            private static final int SKU_CODE = 16;
            private static final int BARCODE = 17;
            private static final int SPEC_NAME = 18;
            private static final int SALE_UNIT = 19;
            private static final int PRODUCT_TYPE = 20;
            private static final int MARKET_PRICE = 21;
            private static final int SKU_STATUS = 22;
            private static final int DEFAULT_SKU = 23;

            @BeforeEach
            void catalog() {
                var category = new ProductCategoryEntity();
                category.setId(100L);
                category.setCategoryCode("FRESH-FRUIT");
                category.setStatus("ENABLED");
                category.setLevel(3);
                // 二级分类：存在且启用，但不能挂商品，用于验证层级在逐行校验就被拦下
                var parent = new ProductCategoryEntity();
                parent.setId(99L);
                parent.setCategoryCode("VEGETABLE");
                parent.setStatus("ENABLED");
                parent.setLevel(2);
                when(productCategoryDao.selectList(any())).thenReturn(List.of(category, parent));
                var tag = new ProductTagEntity();
                tag.setId(200L);
                tag.setTagCode("HOT");
                tag.setStatus("ENABLED");
                when(productTagDao.selectList(any())).thenReturn(List.of(tag));
                when(productSpuDao.selectList(any())).thenReturn(List.of(currentSpu()));
                when(productSkuDao.selectList(any())).thenReturn(List.of(currentSku()));
                when(productImageDao.selectList(any())).thenReturn(List.of());
                var binding = new ProductSpuTagVO();
                binding.setSpuId(7L);
                binding.setTagId(200L);
                when(productTagService.bySpuIds(any())).thenReturn(Map.of(7L, List.of(binding)));
            }

            private ProductSpuEntity currentSpu() {
                var spu = new ProductSpuEntity();
                spu.setId(7L);
                spu.setVersion(3);
                spu.setSpuCode("SPU0001");
                spu.setName("示例蔬菜");
                spu.setCategoryId(100L);
                spu.setStatus("ON_SHELF");
                spu.setStorageMethod("CHILLED");
                spu.setDescription("原有详情描述");
                return spu;
            }

            private ProductSkuEntity currentSku() {
                var sku = new ProductSkuEntity();
                sku.setId(70L);
                sku.setSpuId(7L);
                sku.setVersion(2);
                sku.setSkuCode("SKU0001");
                sku.setSpecName("500g/份");
                sku.setSpecValues(Map.of("规格", "500g/份"));
                sku.setSaleUnit("份");
                sku.setProductType("STANDARD");
                sku.setMarketPrice(new BigDecimal("9.9000"));
                sku.setStatus("ON_SHELF");
                sku.setDefaultFlag(true);
                sku.setSortOrder(0);
                return sku;
            }

            /** 更新模式的单行文件：默认只改市场价，其余列留空表示保持原值。 */
            private MockMultipartFile updateWorkbook(Consumer<XSSFWorkbook> edit) throws Exception {
                return sheet(UPDATE_HEADERS, book -> {
                    var row = book.getSheetAt(0).createRow(1);
                    String[] data = {"1.0", "7", "3", "70", "2", "SPU0001", "", "", "", "", "", "", "", "", "",
                            "", "SKU0001", "", "", "", "", "12.3400", "", "", ""};
                    for (int i = 0; i < data.length; i++) row.createCell(i).setCellValue(data[i]);
                    edit.accept(book);
                });
            }

            private MockMultipartFile sheet(String[] headers, Consumer<XSSFWorkbook> edit) throws Exception {
                try (var book = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
                    var sheet = book.createSheet("商品导入");
                    var header = sheet.createRow(0);
                    for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
                    edit.accept(book);
                    book.write(bytes);
                    return new MockMultipartFile("file", "products.xlsx",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes.toByteArray());
                }
            }

            private MockMultipartFile workbook(Consumer<XSSFWorkbook> edit) throws Exception {
                return sheet(HEADERS, book -> {
                    // 物理空行不得让 Excel 第 4 行的定位前移到第 3 行
                    book.getSheetAt(0).createRow(1);
                    var row = book.getSheetAt(0).createRow(2);
                    String[] data = {"1.0", "SPU0001", "示例蔬菜", "", "FRESH-FRUIT", "", "", "本地", "CHILLED",
                            "", "HOT", "ON_SHELF", "SKU0001", "", "500g/份", "份", "STANDARD", "9.9000", "ON_SHELF", "是", "0"};
                    for (int i = 0; i < data.length; i++) row.createCell(i).setCellValue(data[i]);
                    edit.accept(book);
                });
            }

            @Test
            void newCreateTemplateUsesGroupingMarkerAndBackendCodes() throws Exception {
                byte[] content = productImportService.buildTemplate(ImportMode.CREATE);
                try (var workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(content))) {
                    var header = workbook.getSheetAt(0).getRow(0);
                    assertThat(header.getCell(1).getStringCellValue()).isEqualTo("导入商品分组");
                    assertThat(header.getCell(12).getStringCellValue()).isEqualTo("条码");
                    assertThat(header.getLastCellNum()).isEqualTo((short) 20);
                }
                when(productImportWriteService.writeAll(anyList())).thenReturn(List.of(501L));
                var response = productImportService.importFile(new MockMultipartFile("file", "products.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content), ImportMode.CREATE);
                assertThat(response.getTotalErrors()).isZero();
                assertThat(response.getImportedProducts()).isEqualTo(1);
                verify(productImportWriteService).writeAll(argThat((List<ProductSpuAddForm> forms) ->
                        forms.size() == 1 && forms.getFirst().getSpuCode() == null
                                && forms.getFirst().getSkuList().getFirst().getSkuCode() == null));
            }

            @Test
            void validSingleSkuWritesThroughOnce() throws Exception {
                when(productImportWriteService.writeAll(anyList())).thenReturn(List.of(5001L));
                var result = productImportService.importFile(workbook(book -> {
                }), ImportMode.CREATE);
                assertThat(result.getTotalErrors()).isZero();
                assertThat(result.getImportedProducts()).isEqualTo(1);
                assertThat(result.getSpuIds()).containsExactly(5001L);
                verify(productImportWriteService).writeAll(argThat((List<ProductSpuAddForm> forms) -> {
                    var form = forms.getFirst();
                    return form.getSpuCode() == null
                            && form.getSkuList().getFirst().getSkuCode() == null
                            && form.getCategoryId().equals(100L)
                            && form.getTagIds().contains(200L)
                            && form.getSkuList().getFirst().getMarketPrice().toPlainString().equals("9.9000")
                            && Boolean.TRUE.equals(form.getSkuList().getFirst().getDefaultFlag());
                }));
            }

            @Test
            void blankRowDoesNotShiftRowDiagnosis() throws Exception {
                var result = productImportService.importFile(workbook(b -> b.getSheetAt(0).getRow(2).getCell(11).setCellValue("MAYBE")), ImportMode.CREATE);
                assertThat(result.getErrors()).anySatisfy(error -> {
                    assertThat(error.getRowNumber()).isEqualTo(3);
                    assertThat(error.getColumn()).isEqualTo("商品状态");
                    assertThat(error.getCode()).isEqualTo("ENUM_INVALID");
                });
                verifyNoInteractions(productImportWriteService);
            }

            @Test
            void secondLevelCategoryIsReportedPerRowBeforeWrite() throws Exception {
                var result = productImportService.importFile(workbook(b -> b.getSheetAt(0).getRow(2).getCell(4).setCellValue("VEGETABLE")),
                        ImportMode.CREATE);
                // 分类存在且启用，只有层级不合规：必须在逐行校验就指到单元格，而不是等写库报 CATEGORY_PARENT_INVALID
                assertThat(result.getErrors()).singleElement().satisfies(error -> {
                    assertThat(error.getRowNumber()).isEqualTo(3);
                    assertThat(error.getColumn()).isEqualTo("分类编码");
                    assertThat(error.getCode()).isEqualTo("CATEGORY_LEVEL_INVALID");
                });
                verifyNoInteractions(productImportWriteService);
            }

            /**
             * 单位与标签的「在字典 + 启用」必须在逐行阶段指到单元格。
             *
             * <p>写入口 {@code ProductSpuService.add} 走 {@code uom.assertUsable} 与
             * {@code productTagDao.assertUsable}：填了停用单位或停用标签时，逐行阶段就得把错误指到单元格，
             * 否则要等整批写库抛 40027/40028 才知道是哪一行。
             */
            @Test
            void unknownSaleUnitIsReportedPerRowBeforeWrite() throws Exception {
                // 列下标 15 是 CREATE 模板里的「销售单位」；常量 SALE_UNIT=19 是更新模板（前置 5 个定位键）的偏移
                var result = productImportService.importFile(workbook(b -> b.getSheetAt(0).getRow(2).getCell(15).setCellValue("吨")),
                        ImportMode.CREATE);
                assertThat(result.getErrors()).singleElement().satisfies(error -> {
                    assertThat(error.getRowNumber()).isEqualTo(3);
                    assertThat(error.getColumn()).isEqualTo("销售单位");
                    assertThat(error.getCode()).isEqualTo("UOM_NOT_USABLE");
                });
                verifyNoInteractions(productImportWriteService);
            }

            @Test
            void blankSaleUnitFallsBackToJin() throws Exception {
                when(productImportWriteService.writeAll(anyList())).thenReturn(List.of(5001L));
                // 列下标 15 是 CREATE 模板里的「销售单位」；留空时按默认单位斤校验并写入数据库
                var result = productImportService.importFile(workbook(b -> b.getSheetAt(0).getRow(2).getCell(15).setCellValue("")),
                        ImportMode.CREATE);
                assertThat(result.getTotalErrors()).isZero();
                verify(productImportWriteService).writeAll(argThat((List<ProductSpuAddForm> forms) ->
                        "斤".equals(forms.getFirst().getSkuList().getFirst().getSaleUnit())));
            }

            @Test
            void disabledTagIsReportedPerRowBeforeWrite() throws Exception {
                var disabled = new ProductTagEntity();
                disabled.setId(200L);
                disabled.setTagCode("HOT");
                disabled.setStatus("DISABLED");
                when(productTagDao.selectList(any())).thenReturn(List.of(disabled));

                var result = productImportService.importFile(workbook(b -> {
                }), ImportMode.CREATE);
                assertThat(result.getErrors()).singleElement().satisfies(error -> {
                    assertThat(error.getRowNumber()).isEqualTo(3);
                    assertThat(error.getColumn()).isEqualTo("标签编码");
                    assertThat(error.getCode()).isEqualTo("TAG_NOT_USABLE");
                });
                verifyNoInteractions(productImportWriteService);
            }

            /** 别名 / 助记码 / 品牌 / 产地的逐行上限与 ProductSpuAddForm 的 @Size 及库里 VARCHAR 同数值。 */
            @Test
            void overlongOptionalSpuColumnsAreReportedPerRow() throws Exception {
                var result = productImportService.importFile(workbook(b -> {
                    b.getSheetAt(0).getRow(2).getCell(3).setCellValue("长".repeat(151));
                    b.getSheetAt(0).getRow(2).getCell(6).setCellValue("牌".repeat(101));
                }), ImportMode.CREATE);
                assertThat(result.getErrors()).hasSize(2).extracting("column")
                        .containsExactlyInAnyOrder("别名", "品牌");
                assertThat(result.getErrors()).allSatisfy(error -> assertThat(error.getCode()).isEqualTo("TOO_LONG"));
                verifyNoInteractions(productImportWriteService);
            }

            @Test
            void headerMismatchBlocksBeforeWrite() throws Exception {
                var result = productImportService.importFile(workbook(b -> b.getSheetAt(0).getRow(0).getCell(1).setCellValue("错误列")), ImportMode.CREATE);
                assertThat(result.getErrors()).anySatisfy(error -> {
                    assertThat(error.getRowNumber()).isEqualTo(1);
                    assertThat(error.getCode()).isEqualTo("HEADER_INVALID");
                });
                verifyNoInteractions(productImportWriteService);
            }

            @Test
            void formulaCellIsRejected() throws Exception {
                var result = productImportService.importFile(workbook(b -> b.getSheetAt(0).getRow(2).getCell(17).setCellFormula("1+1")), ImportMode.CREATE);
                assertThat(result.getErrors()).extracting("code").contains("CELL_INVALID");
                verifyNoInteractions(productImportWriteService);
            }

            @Test
            void extraSheetIsRejected() throws Exception {
                var result = productImportService.importFile(workbook(b -> b.createSheet("更多")), ImportMode.CREATE);
                assertThat(result.getErrors()).extracting("code").contains("SHEET_COUNT");
                verifyNoInteractions(productImportWriteService);
            }

            @Test
            void unknownCategoryCodeIsRejected() throws Exception {
                var result = productImportService.importFile(workbook(b -> b.getSheetAt(0).getRow(2).getCell(4).setCellValue("NOPE")), ImportMode.CREATE);
                assertThat(result.getErrors()).anySatisfy(error -> assertThat(error.getCode()).isEqualTo("CATEGORY_NOT_FOUND"));
                verifyNoInteractions(productImportWriteService);
            }

            @Test
            void missingDefaultSkuIsRejected() throws Exception {
                var result = productImportService.importFile(workbook(b -> b.getSheetAt(0).getRow(2).getCell(19).setCellValue("否")), ImportMode.CREATE);
                assertThat(result.getErrors()).anySatisfy(error -> assertThat(error.getCode()).isEqualTo("DEFAULT_SKU_INVALID"));
                verifyNoInteractions(productImportWriteService);
            }

            @Test
            void duplicateSpecWithinProductIsRejected() throws Exception {
                var result = productImportService.importFile(workbook(book -> {
                    var sheet = book.getSheetAt(0);
                    var second = sheet.createRow(3);
                    String[] data = {"1.0", "SPU0001", "示例蔬菜", "", "FRESH-FRUIT", "", "", "本地", "CHILLED",
                            "", "HOT", "ON_SHELF", "SKU0001", "", "500g/份", "份", "STANDARD", "18.0000", "ON_SHELF", "否", "1"};
                    for (int i = 0; i < data.length; i++) second.createCell(i).setCellValue(data[i]);
                }), ImportMode.CREATE);
                assertThat(result.getErrors()).anySatisfy(error -> {
                    assertThat(error.getCode()).isEqualTo("SKU_SPEC_DUPLICATE");
                    assertThat(error.getRowNumber()).isEqualTo(4);
                });
                verifyNoInteractions(productImportWriteService);
            }

            @Test
            void inconsistentGroupFieldsRejected() throws Exception {
                var result = productImportService.importFile(workbook(book -> {
                    var sheet = book.getSheetAt(0);
                    var second = sheet.createRow(3);
                    String[] data = {"1.0", "SPU0001", "示例蔬菜", "", "FRESH-PRODUCE", "", "", "本地", "CHILLED",
                            "", "HOT", "ON_SHELF", "SKU0002", "", "1kg/份", "份", "STANDARD", "18.0000", "ON_SHELF", "否", "1"};
                    for (int i = 0; i < data.length; i++) second.createCell(i).setCellValue(data[i]);
                }), ImportMode.CREATE);
                assertThat(result.getErrors()).anySatisfy(error -> assertThat(error.getCode()).isEqualTo("HEADER_CONFLICT"));
                verifyNoInteractions(productImportWriteService);
            }

            @Test
            void writeFailureRollsBackWholeBatchAndLocatesGroup() throws Exception {
                when(productImportWriteService.writeAll(anyList())).thenThrow(new ProductImportWriteService.ImportProductException(0,
                        new ScmBusinessException(ProductErrorCode.PRODUCT_CODE_DUPLICATE)));
                var result = productImportService.importFile(workbook(book -> {
                }), ImportMode.CREATE);
                assertThat(result.getImportedProducts()).isZero();
                assertThat(result.getErrors()).singleElement().satisfies(error -> {
                    assertThat(error.getRowNumber()).isEqualTo(3);
                    assertThat(error.getCode()).isEqualTo("40923");
                    assertThat(error.getMessage()).contains("本批数据未保存");
                });
            }

            @Test
            void emptyDataIsRejected() throws Exception {
                var result = productImportService.importFile(workbook(book -> book.getSheetAt(0).removeRow(book.getSheetAt(0).getRow(2))), ImportMode.CREATE);
                assertThat(result.getErrors()).extracting("code").contains("FILE_EMPTY");
                verifyNoInteractions(productImportWriteService);
            }

            @Test
            void malformedFileIsRejected() throws Exception {
                var file = new MockMultipartFile("file", "broken.xlsx", "application/octet-stream", new byte[]{1, 2, 3});
                assertThat(productImportService.importFile(file, ImportMode.CREATE).getErrors()).extracting("code").contains("FILE_INVALID");
                verifyNoInteractions(productImportWriteService);
            }

            @Test
            void templateHeaderRoundTripsAndIsParseable() throws Exception {
                byte[] template = productImportService.buildTemplate(ImportMode.CREATE);
                when(productImportWriteService.writeAll(anyList())).thenReturn(List.of(1L));
                var result = productImportService.importFile(new MockMultipartFile("file", "t.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", template), ImportMode.CREATE);
                // 模板示例行自带合法编码，能进入校验阶段（分类编码在 mock 目录里不存在 → 只报 CATEGORY_NOT_FOUND，无 HEADER 错误）
                assertThat(result.getErrors()).noneMatch(e -> e.getCode().equals("HEADER_INVALID") || e.getCode().equals("SHEET_COUNT"));
            }

            @Test
            void updateModeKeepsBlankColumnsAtCurrentValue() throws Exception {
                when(productImportWriteService.writeUpdates(anyList())).thenReturn(1);
                var result = productImportService.importFile(updateWorkbook(book -> {
                }), ImportMode.UPDATE);
                assertThat(result.getMode()).isEqualTo("UPDATE");
                assertThat(result.getTotalErrors()).isZero();
                assertThat(result.getUpdatedProducts()).isEqualTo(1);
                assertThat(result.getSpuIds()).containsExactly(7L);
                verify(productImportWriteService).writeUpdates(argThat((List<ProductSpuUpdateForm> forms) -> {
                    var form = forms.getFirst();
                    var sku = form.getSkuList().getFirst();
                    // 文件只填了市场价：名称、描述、分类、标签与 SKU 其余列都必须回到库内原值
                    return form.getSpuId().equals(7L) && form.getVersion().equals(3)
                            && "SPU0001".equals(form.getSpuCode())
                            && "示例蔬菜".equals(form.getName())
                            && "原有详情描述".equals(form.getDescription())
                            && Long.valueOf(100L).equals(form.getCategoryId())
                            && List.of(200L).equals(form.getTagIds())
                            && Long.valueOf(70L).equals(sku.getSkuId()) && sku.getVersion().equals(2)
                            && "SKU0001".equals(sku.getSkuCode()) && "500g/份".equals(sku.getSpecName())
                            && Map.of("规格", "500g/份").equals(sku.getSpecValues())
                            && "份".equals(sku.getSaleUnit()) && "ON_SHELF".equals(sku.getStatus())
                            && Boolean.TRUE.equals(sku.getDefaultFlag())
                            && "12.3400".equals(sku.getMarketPrice().toPlainString());
                }));
            }

            @Test
            void updateModeClearsOnlyMarkedNullableAttributes() throws Exception {
                when(productImportWriteService.writeUpdates(anyList())).thenReturn(1);
                var spu = currentSpu();
                spu.setAlias("原别名");
                spu.setBrandName("原品牌");
                when(productSpuDao.selectList(any())).thenReturn(List.of(spu));
                var sku = currentSku();
                sku.setBarcode("BC-0001");
                when(productSkuDao.selectList(any())).thenReturn(List.of(sku));

                var result = productImportService.importFile(updateWorkbook(book -> {
                    var row = book.getSheetAt(0).getRow(1);
                    for (int column : new int[]{ALIAS, BRAND_NAME, TAG_CODES, BARCODE})
                        row.getCell(column).setCellValue(ProductImportService.CLEAR_TOKEN);
                }), ImportMode.UPDATE);

                assertThat(result.getTotalErrors()).isZero();
                verify(productImportWriteService).writeUpdates(argThat((List<ProductSpuUpdateForm> forms) -> {
                    var form = forms.getFirst();
                    var merged = form.getSkuList().getFirst();
                    // 只有带标记的列被清；名称、编码与规格等未标记列仍是库内原值
                    return form.getAlias() == null && form.getBrandName() == null
                            && form.getTagIds().isEmpty() && merged.getBarcode() == null
                            && "示例蔬菜".equals(form.getName()) && "SKU0001".equals(merged.getSkuCode())
                            && "12.3400".equals(merged.getMarketPrice().toPlainString());
                }));
            }

            @Test
            void updateModeRejectsClearMarkerOnGuardedColumn() throws Exception {
                var result = productImportService.importFile(updateWorkbook(book -> book.getSheetAt(0).getRow(1)
                        .getCell(DEFAULT_SKU).setCellValue(ProductImportService.CLEAR_TOKEN)), ImportMode.UPDATE);
                assertThat(result.getErrors()).anySatisfy(error -> {
                    assertThat(error.getCode()).isEqualTo("CLEAR_NOT_ALLOWED");
                    assertThat(error.getColumn()).isEqualTo("默认商品规格");
                });
                verifyNoInteractions(productImportWriteService);
            }

            @Test
            void updateModeKeepsUnlistedExistingSkuAsDefault() throws Exception {
                when(productImportWriteService.writeUpdates(anyList())).thenReturn(1);
                var result = productImportService.importFile(updateWorkbook(book -> {
                    var row = book.getSheetAt(0).getRow(1);
                    // 整份文件只登记一个新 SKU：既存默认 SKU 70 没出现，仍要保留且继续算默认，否则会把补规格变成删原规格
                    row.getCell(SKU_ID).setCellValue("");
                    row.getCell(SKU_VERSION).setCellValue("");
                    row.getCell(SKU_CODE).setCellValue("SKU0002");
                    row.getCell(SPEC_NAME).setCellValue("1kg/份");
                    row.getCell(SALE_UNIT).setCellValue("份");
                    row.getCell(PRODUCT_TYPE).setCellValue("STANDARD");
                    row.getCell(MARKET_PRICE).setCellValue("18.0000");
                    row.getCell(SKU_STATUS).setCellValue("ON_SHELF");
                    row.getCell(DEFAULT_SKU).setCellValue("否");
                }), ImportMode.UPDATE);
                assertThat(result.getTotalErrors()).isZero();
                verify(productImportWriteService).writeUpdates(argThat((List<ProductSpuUpdateForm> forms) -> {
                    var merged = forms.getFirst().getSkuList();
                    return merged.size() == 2 && Long.valueOf(70L).equals(merged.getFirst().getSkuId())
                            && Boolean.TRUE.equals(merged.getFirst().getDefaultFlag())
                            && merged.get(1).getSkuId() == null && "SKU0002".equals(merged.get(1).getSkuCode())
                            && "18.0000".equals(merged.get(1).getMarketPrice().toPlainString());
                }));
            }

            @Test
            void updateModeRejectsStaleVersionWithoutWriting() throws Exception {
                var result = productImportService.importFile(updateWorkbook(book -> book.getSheetAt(0).getRow(1)
                        .getCell(SPU_VERSION).setCellValue("4")), ImportMode.UPDATE);
                assertThat(result.getErrors()).anySatisfy(error -> {
                    assertThat(error.getCode()).isEqualTo("VERSION_CONFLICT");
                    assertThat(error.getColumn()).isEqualTo("商品版本");
                    assertThat(error.getRowNumber()).isEqualTo(2);
                });
                verifyNoInteractions(productImportWriteService);
            }

            @Test
            void updateModeRefusesToRetypeBusinessCodes() throws Exception {
                var result = productImportService.importFile(updateWorkbook(book -> {
                    var row = book.getSheetAt(0).getRow(1);
                    row.getCell(SPU_CODE).setCellValue("SPU9999");
                    row.getCell(SKU_CODE).setCellValue("SKU9999");
                }), ImportMode.UPDATE);
                assertThat(result.getErrors()).extracting("code").contains("FIELD_LOCKED");
                assertThat(result.getErrors()).allSatisfy(error -> {
                    if (error.getCode().equals("FIELD_LOCKED"))
                        assertThat(List.of("商品编码", "商品规格编码")).contains(error.getColumn());
                });
                verifyNoInteractions(productImportWriteService);
            }

            @Test
            void createFileCannotBeImportedAsUpdate() throws Exception {
                var result = productImportService.importFile(workbook(book -> {
                }), ImportMode.UPDATE);
                assertThat(result.getErrors()).extracting("code").contains("HEADER_INVALID");
                verifyNoInteractions(productImportWriteService);
            }

            @Test
            void updateWriteFailureRollsBackWholeBatchAndLocatesRow() throws Exception {
                when(productImportWriteService.writeUpdates(anyList())).thenThrow(new ProductImportWriteService.ImportProductException(0,
                        new ScmBusinessException(ProductErrorCode.VERSION_CONFLICT)));
                var result = productImportService.importFile(updateWorkbook(book -> {
                }), ImportMode.UPDATE);
                assertThat(result.getUpdatedProducts()).isZero();
                assertThat(result.getErrors()).singleElement().satisfies(error -> {
                    assertThat(error.getRowNumber()).isEqualTo(2);
                    assertThat(error.getCode()).isEqualTo("40921");
                    assertThat(error.getMessage()).contains("本批数据未保存");
                });
            }

            @Test
            void updateTemplateRoundTripsAgainstOwnHeaders() throws Exception {
                when(productImportWriteService.writeUpdates(anyList())).thenReturn(1);
                byte[] template = productImportService.buildTemplate(ImportMode.UPDATE);
                var result = productImportService.importFile(new MockMultipartFile("file", "u.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", template), ImportMode.UPDATE);
                // 示例行按更新表头解析成功即无 HEADER 错；定位键在 mock 现状里不存在 → 只报业务错
                assertThat(result.getErrors()).noneMatch(e -> e.getCode().equals("HEADER_INVALID") || e.getCode().equals("SHEET_COUNT"));
                assertThat(result.getErrors()).extracting("code").contains("PRODUCT_NOT_FOUND");
            }
        }

        @Nested

        class ProductInternalCodePolicyTest {
            private final ScmBusinessNoService numbers = mock(ScmBusinessNoService.class);

            @Test
            void createsSpuUsingSystemCodeDespiteClientCode() {
                var spuDao = mock(ProductSpuDao.class);
                var service = new ProductSpuService(spuDao, numbers, mock(ProductCategoryService.class),
                        mock(ProductAggregateValidator.class), mock(ProductSkuSyncManager.class),
                        mock(ProductImageSyncManager.class), mock(ProductUomService.class),
                        mock(ProductTagService.class));
                var form = new ProductSpuAddForm();
                form.setSpuCode("CLIENT-SPU");
                form.setName("红富士苹果");
                form.setCategoryId(10L);
                form.setSkuList(List.of(new ProductSkuForm()));
                form.setImages(List.of());
                form.setTagIds(List.of());
                when(numbers.next(ScmBusinessNoType.PRODUCT_SPU)).thenReturn("SPU000008");
                org.mockito.Mockito.doAnswer(invocation -> {
                    ((ProductSpuEntity) invocation.getArgument(0)).setId(66L);
                    return 1;
                }).when(spuDao).insert(org.mockito.ArgumentMatchers.any(ProductSpuEntity.class));

                try (var operator = mockStatic(ScmOperator.class)) {
                    operator.when(ScmOperator::current).thenReturn("test:1");
                    service.add(form);
                }

                var captured = ArgumentCaptor.forClass(ProductSpuEntity.class);
                verify(spuDao).insert(captured.capture());
                assertThat(captured.getValue().getSpuCode()).isEqualTo("SPU000008");
            }

            @Test
            void createsCategoryUsingSystemCodeDespiteClientCode() {
                var dao = mock(ProductCategoryDao.class);
                var service = new ProductCategoryService(dao, numbers, mock(ProductSpuDao.class));
                var form = new ProductCategoryAddForm();
                form.setCategoryCode("CLIENT-CATEGORY");
                form.setName("生鲜");
                when(numbers.next(ScmBusinessNoType.PRODUCT_CATEGORY)).thenReturn("CAT000008");

                try (var operator = mockStatic(ScmOperator.class)) {
                    operator.when(ScmOperator::current).thenReturn("test:1");
                    service.add(form);
                }
                var captured = ArgumentCaptor.forClass(ProductCategoryEntity.class);
                verify(dao).insert(captured.capture());
                assertThat(captured.getValue().getCategoryCode()).isEqualTo("CAT000008");
            }

            @Test
            void createsTagUsingSystemCodeDespiteClientCode() {
                var dao = mock(ProductTagDao.class);
                var service = new ProductTagService(dao, numbers, mock(ProductTagRelationDao.class));
                var form = new ProductTagAddForm();
                form.setTagCode("CLIENT-TAG");
                form.setName("精选");
                when(numbers.next(ScmBusinessNoType.PRODUCT_TAG)).thenReturn("TAG000008");

                try (var operator = mockStatic(ScmOperator.class)) {
                    operator.when(ScmOperator::current).thenReturn("test:1");
                    service.add(form);
                }

                var captured = ArgumentCaptor.forClass(ProductTagEntity.class);
                verify(dao).insert(captured.capture());
                assertThat(captured.getValue().getTagCode()).isEqualTo("TAG000008");
            }
        }

        @Nested

        class ProductSkuChangeSetTest {
            @Test
            void preservesExistingIdentityAndSeparatesInsertUpdateAndRemove() {
                var old = new ProductSkuEntity();
                old.setId(7L);
                old.setVersion(2);
                var removed = new ProductSkuEntity();
                removed.setId(8L);
                var keep = new ProductSkuForm();
                keep.setSkuId(7L);
                keep.setVersion(2);
                var add = new ProductSkuForm();
                var changes = ProductSkuChangeSet.between(List.of(old, removed), List.of(keep, add));
                assertThat(changes.updated()).containsExactly(keep);
                assertThat(changes.inserted()).containsExactly(add);
                assertThat(changes.removedIds()).containsExactly(8L);
                assertThat(keep.getSkuId()).isEqualTo(7L);
                assertThat(keep.getVersion()).isEqualTo(2);
            }

            @Test
            void rejectsForeignAndRepeatedIdentity() {
                var old = new ProductSkuEntity();
                old.setId(7L);
                var foreign = new ProductSkuForm();
                foreign.setSkuId(8L);
                foreign.setVersion(0);
                assertThatThrownBy(() -> ProductSkuChangeSet.between(List.of(old), List.of(foreign)))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(e.getErrorCode().getCode()).isEqualTo(40920));
                foreign.setSkuId(7L);
                assertThatThrownBy(() -> ProductSkuChangeSet.between(List.of(old), List.of(foreign, foreign))).isInstanceOf(ScmBusinessException.class);
            }
        }

        @Nested

        class ProductSkuInternalCodeTest {
            private final ProductSkuDao skuDao = mock(ProductSkuDao.class);
            private final ScmBusinessNoService numbers = mock(ScmBusinessNoService.class);
            private final ProductSkuSyncManager manager = new ProductSkuSyncManager(
                    skuDao, numbers, mock(ProductBundleComponentDao.class));

            @Test
            void newSkuGetsBackendCodeNotClientValue() {
                var form = new ProductSkuForm();
                form.setSkuCode("CLIENT-OVERRIDE");
                form.setSpecName("5kg/箱");
                form.setSaleUnit("箱");
                when(numbers.next(ScmBusinessNoType.PRODUCT_SKU)).thenReturn("SKU000128");

                try (var operator = org.mockito.Mockito.mockStatic(ScmOperator.class)) {
                    operator.when(ScmOperator::current).thenReturn("test:1");
                    manager.sync(22L, new ProductSkuChangeSet(List.of(form), List.of(), List.of()));
                }

                var result = ArgumentCaptor.forClass(ProductSkuEntity.class);
                verify(skuDao).insert(result.capture());
                assertThat(result.getValue().getSkuCode()).isEqualTo("SKU000128");
                assertThat(result.getValue().getSpuId()).isEqualTo(22L);
            }

            @Test
            void existingSkuRetainsCodeEvenIfClientAttemptsToOverwrite() {
                var existing = new ProductSkuEntity();
                existing.setId(88L);
                existing.setSpuId(22L);
                existing.setSkuCode("LEGACY-SKU");
                when(skuDao.selectById(88L)).thenReturn(existing);
                when(skuDao.updateById(org.mockito.ArgumentMatchers.any(ProductSkuEntity.class))).thenReturn(1);
                var form = new ProductSkuForm();
                form.setSkuId(88L);
                form.setVersion(0);
                form.setSkuCode("CLIENT-REWRITE");
                form.setSpecName("散称");
                form.setSaleUnit("kg");

                try (var operator = org.mockito.Mockito.mockStatic(ScmOperator.class)) {
                    operator.when(ScmOperator::current).thenReturn("test:1");
                    manager.sync(22L, new ProductSkuChangeSet(List.of(), List.of(form), List.of()));
                }

                var result = ArgumentCaptor.forClass(ProductSkuEntity.class);
                verify(skuDao).updateById(result.capture());
                assertThat(result.getValue().getSkuCode()).isEqualTo("LEGACY-SKU");
                verifyNoInteractions(numbers);
            }
        }
    }

    @Nested
    class ScmBundleServicePgIT extends ScmW3PgITBase {
        @Autowired
        private ProductBundleService bundleService;

        @Autowired
        private OrderBundleSnapshotService snapshotService;

        @Test
        void replaceBundleComponentsPersistsAndIncrementsSkuVersion() {
            Long bundle = createStandardSku("BUNDLE");
            Long apple = createStandardSku("APPLE");
            Long pear = createStandardSku("PEAR");

            int version = skuVersion(bundle);
            bundleService.replace(form(bundle, version, item(apple, "2.0000"), item(pear, "3.0000")));

            assertThat(bundleService.components(bundle)).hasSize(2);
            assertThat(skuVersion(bundle)).isEqualTo(version + 1);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_product_bundle_component "
                    + "WHERE bundle_sku_id=? AND deleted=FALSE", Integer.class, bundle)).isEqualTo(2);

            bundleService.replace(form(bundle, version + 1, item(pear, "1.0000")));

            assertThat(bundleService.components(bundle)).hasSize(1);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_product_bundle_component WHERE bundle_sku_id=? AND deleted=TRUE",
                    Integer.class, bundle)).isEqualTo(2);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_product_bundle_component WHERE bundle_sku_id=? AND deleted=FALSE",
                    Integer.class, bundle)).isEqualTo(1);
        }

        @Test
        void emptyComponentListClearsBundleAndBumpsSkuVersion() {
            Long bundle = createStandardSku("CLEAR_BUNDLE");
            Long apple = createStandardSku("CLEAR_APPLE");
            bundleService.replace(form(bundle, skuVersion(bundle), item(apple, "1.0000")));
            int version = skuVersion(bundle);

            bundleService.replace(form(bundle, version));

            assertThat(bundleService.components(bundle)).isEmpty();
            assertThat(skuVersion(bundle)).isEqualTo(version + 1);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_product_bundle_component WHERE bundle_sku_id=? AND deleted=TRUE",
                    Integer.class, bundle)).isEqualTo(1);
        }

        @Test
        void realBundleConfigurationFreezesAllocationWithoutReservingStockAtConfirmation() {
            Long bundle = createStandardSku("FREEZE_BUNDLE");
            Long apple = createStandardSku("FREEZE_APPLE");
            Long pear = createStandardSku("FREEZE_PEAR");
            bundleService.replace(form(bundle, skuVersion(bundle), item(apple, "2.0000"), item(pear, "3.0000")));
            Long warehouse = jdbc.queryForObject(
                    "SELECT id FROM xsy_warehouse WHERE deleted=FALSE ORDER BY id LIMIT 1", Long.class);

            SalesOrderItemEntity orderItem = new SalesOrderItemEntity();
            orderItem.setOrderId(990001L);
            orderItem.setId(990002L);
            orderItem.setSkuId(bundle);
            orderItem.setActualQuantity(new BigDecimal("1.0000"));
            orderItem.setLockedUnitPrice(new BigDecimal("80.0000"));

            assertThat(snapshotService.freeze(orderItem, warehouse)).isTrue();
            assertThat(jdbc.queryForObject(
                    "SELECT sum(allocated_sale_amount_snapshot) FROM xsy_order_bundle_component "
                    + "WHERE order_item_id=990002", BigDecimal.class)).isEqualByComparingTo("80.0000");
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_order_bundle_component "
                    + "WHERE order_item_id=990002 AND warehouse_id=? AND market_unit_price_snapshot=10.0000",
                    Integer.class, warehouse)).isEqualTo(2);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_inventory_reservation WHERE source_document_id=990001",
                    Integer.class)).isZero();
        }

        @Test
        void duplicateComponentIsRejectedBeforeItWritesPartialConfiguration() {
            Long bundle = createStandardSku("DUP_BUNDLE");
            Long apple = createStandardSku("DUP_APPLE");

            assertThatThrownBy(() -> bundleService.replace(
                    form(bundle, skuVersion(bundle), item(apple, "1.0000"), item(apple, "2.0000"))))
                    .isInstanceOf(ScmBusinessException.class);

            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_product_bundle_component WHERE bundle_sku_id=? AND deleted=FALSE",
                    Integer.class, bundle)).isZero();
        }

        @Test
        void nestedBundleIsRejected() {
            Long outer = createStandardSku("OUTER");
            Long inner = createStandardSku("INNER");
            Long leaf = createStandardSku("LEAF");
            bundleService.replace(form(inner, skuVersion(inner), item(leaf, "1.0000")));

            assertThatThrownBy(() -> bundleService.replace(
                    form(outer, skuVersion(outer), item(inner, "1.0000"))))
                    .isInstanceOf(ScmBusinessException.class);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_product_bundle_component WHERE bundle_sku_id=? AND deleted=FALSE",
                    Integer.class, outer)).isZero();
        }

        private Long createStandardSku(String suffix) {
            ProductSpuAddForm spu = new ProductSpuAddForm();
            spu.setSpuCode(prefix + suffix);
            spu.setName(prefix + suffix + "商品");
            spu.setStatus("ON_SHELF");
            spu.setCategoryId(jdbc.queryForObject(
                    "SELECT id FROM xsy_product_category WHERE category_code='FRESH-FRUIT' AND deleted=FALSE",
                    Long.class));

            ProductSkuForm sku = new ProductSkuForm();
            sku.setSkuCode(prefix + suffix + "-K");
            sku.setSpecName("规格" + suffix);
            sku.setSpecValues(new LinkedHashMap<>(Map.of("规格", suffix)));
            sku.setSaleUnit("kg");
            sku.setProductType("STANDARD");
            sku.setMarketPrice(new BigDecimal("10.0000"));
            sku.setStatus("ON_SHELF");
            sku.setDefaultFlag(true);
            spu.setSkuList(new ArrayList<>(List.of(sku)));

            Long spuId = productSpuService.add(spu);
            return jdbc.queryForObject("SELECT id FROM xsy_product_sku WHERE spu_id=? AND deleted=FALSE",
                    Long.class, spuId);
        }

        private int skuVersion(Long skuId) {
            return jdbc.queryForObject("SELECT version FROM xsy_product_sku WHERE id=?", Integer.class, skuId);
        }

        private static ProductBundleReplaceForm form(Long bundleSkuId, int version,
                ProductBundleReplaceForm.Component... components) {
            ProductBundleReplaceForm form = new ProductBundleReplaceForm();
            form.setBundleSkuId(bundleSkuId);
            form.setVersion(version);
            form.setComponents(List.of(components));
            return form;
        }

        private static ProductBundleReplaceForm.Component item(Long skuId, String quantity) {
            ProductBundleReplaceForm.Component item = new ProductBundleReplaceForm.Component();
            item.setSkuId(skuId);
            item.setQuantityPerBundleSaleUnit(quantity);
            return item;
        }
    }

    @Nested
    @DisplayName("FA-3 商品图 key 搬运（PG IT）")
    class ScmProductImageKeyMigrationPgIT extends ScmBaselineMigrationPgITBase {
        private static final String V58 = "V58__scm_product_image_public_file_key.sql";

        /** 搬运段标记；改标记名会让取段断言直接失败，而不是静默跑一段空 SQL。 */
        private static final String BEGIN = "-- FA3 BEGIN";

        private static final String END = "-- FA3 END";

        @Test
        @DisplayName("搬运：key 改到 public/image/、t_file 同步（含 folder_type）、关系行收回，重跑一次结果不变")
        void movesLegacyKeysIntoPublicImagePrefix() {
            // CHECK 描述的是搬运之后的形态；先摘掉才能摆出搬运前的库状态（用例事务回滚，不留痕）
            dropPublicPrefixCheck();
            Long spu = 900001L;
            // 两种历史形状：直接落在目录下的文件名，以及带子目录的尾段（曾经的公告图目录形态）
            String flat = "private/common/" + tail("flat");
            String nested = "private/common/8f3d/notice/" + tail("nested");
            seedFile(flat);
            seedFile(nested);
            seedImage(spu, flat, true);
            seedImage(spu, nested, false);
            seedRelation(flat, spu);
            seedRelation(nested, spu);
            // 已软删的历史行不是搬运对象
            String tombstone = "private/common/" + tail("tomb");
            seedFile(tombstone);
            seedImage(spu, tombstone, false, true);

            runRewrite();

            assertThat(activeKeysOf(spu)).containsExactlyInAnyOrder(
                    "public/image/" + tail("flat"), "public/image/8f3d/notice/" + tail("nested"));
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_product_image WHERE deleted = FALSE AND file_key NOT LIKE 'public/%'",
                    Integer.class)).as("活行里不再有任何非公开前缀的商品图").isZero();
            assertThat(jdbc.queryForObject("SELECT folder_type FROM t_file WHERE file_key = ?", Integer.class,
                    "public/image/" + tail("flat")))
                    .as("folder_type 必须与 key 前缀一起改，否则同一行里两种规则并存").isEqualTo(5);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM t_file WHERE file_key = ?", Integer.class,
                    "public/image/8f3d/notice/" + tail("nested"))).isEqualTo(1);
            assertThat(activeRelationCount(flat)).as("搬到公开前缀后关系行是死键，必须收回").isZero();
            assertThat(activeRelationCount(nested)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM t_file_relation WHERE file_key = ?", Integer.class,
                    flat)).as("软删除而不是物理删：授权变更要可追溯").isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT file_key FROM t_file WHERE file_key = ?", String.class, tombstone))
                    .isEqualTo(tombstone);

            // 可重入：第二次执行不改写任何行、也不报错
            List<String> afterFirst = activeKeysOf(spu);
            runRewrite();
            assertThat(activeKeysOf(spu)).isEqualTo(afterFirst);
        }

        @Test
        @DisplayName("两条历史 key 折叠成同一个目标时中止，不留下互相覆盖的结果")
        void refusesWhenTwoLegacyKeysCollapseToTheSameTarget() {
            dropPublicPrefixCheck();
            String sharedTail = tail("collide");
            seedFile("private/common/" + sharedTail);
            seedFile("private/notice/" + sharedTail);
            seedImage(900002L, "private/common/" + sharedTail, true);
            seedImage(900003L, "private/notice/" + sharedTail, true);

            assertThatThrownBy(this::runRewrite)
                    .rootCause()
                    .as("折叠冲突必须在改写前中止：静默覆盖会让一个商品的图指向另一个商品的对象")
                    .hasMessageContaining("折叠");
        }

        @Test
        @DisplayName("目标 key 已被其它文件占用时中止，不改写任何一方")
        void refusesWhenTargetKeyIsAlreadyTaken() {
            dropPublicPrefixCheck();
            String legacy = "private/common/" + tail("taken");
            String occupied = "public/image/" + tail("taken");
            seedFile(legacy);
            seedFile(occupied);
            seedImage(900004L, legacy, true);

            assertThatThrownBy(this::runRewrite)
                    .rootCause()
                    .hasMessageContaining("已被其它文件占用");
            // 探针之后不再有语句：被拒的执行会让本用例的 Spring 事务进入 25P02
        }

        @Test
        @DisplayName("活行挂私有 key 被 CHECK 拒绝，软删行保留原 key 仍然合法")
        void checkAllowsOnlyPublicKeysOnActiveRows() {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE conname = ?", Integer.class,
                    "ck_product_image_public_file_key")).as("V58 已把不变量落进数据库").isEqualTo(1);
            String legacy = "private/common/" + tail("check");
            seedFile(legacy);
            // 软删分支放在前面：CHECK 无法做成部分约束，历史行的原 key 就是靠这个分支留下来的
            seedImage(900006L, legacy, true, true);
            assertThat(jdbc.queryForObject("SELECT deleted FROM xsy_product_image WHERE spu_id = ?", Boolean.class,
                    900006L)).isTrue();
            // 越界探针放在最后（同上：25P02 之后没有可执行的语句）
            assertThatThrownBy(() -> seedImage(900005L, legacy, true))
                    .hasMessageContaining("ck_product_image_public_file_key");
        }

        @Test
        @DisplayName("空 file_key 无法机械搬运：迁移直接拒绝，不猜目标名")
        void refusesToGuessTargetForBlankKey() {
            dropPublicPrefixCheck();
            seedImage(900007L, " ", true);
            assertThatThrownBy(this::runRewrite)
                    .rootCause()
                    .hasMessageContaining("为空");
        }

        // ==================== 夹具 ====================

        private void runRewrite() {
            jdbc.execute(migrationSection(V58, BEGIN, END));
        }

        private void dropPublicPrefixCheck() {
            jdbc.execute("ALTER TABLE xsy_product_image DROP CONSTRAINT ck_product_image_public_file_key");
        }

        /** 尾段带上用例前缀，避免与同库里其它数据相撞。 */
        private String tail(String label) {
            return prefix.toLowerCase() + "-" + label + ".jpg";
        }

        private void seedFile(String fileKey) {
            jdbc.update("INSERT INTO t_file (folder_type, file_name, file_size, file_key, file_type, create_time) "
                            + "VALUES (1, ?, 1, ?, 'jpg', CURRENT_TIMESTAMP)",
                    fileKey.substring(fileKey.lastIndexOf('/') + 1), fileKey);
        }

        private void seedImage(Long spuId, String fileKey, boolean primary) {
            seedImage(spuId, fileKey, primary, false);
        }

        private void seedImage(Long spuId, String fileKey, boolean primary, boolean deleted) {
            jdbc.update("INSERT INTO xsy_product_image (spu_id, file_key, file_name, file_size, is_primary, sort_order, "
                            + "version, deleted, created_at, updated_at, created_by, updated_by) "
                            + "VALUES (?, ?, ?, 1, ?, 0, 0, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'it', 'it')",
                    spuId, fileKey, fileKey.substring(fileKey.lastIndexOf('/') + 1), primary, deleted);
        }

        private void seedRelation(String fileKey, Long spuId) {
            jdbc.update("INSERT INTO t_file_relation (file_key, biz_type, biz_id) VALUES (?, 'PRODUCT', ?)",
                    fileKey, spuId);
        }

        private List<String> activeKeysOf(Long spuId) {
            return jdbc.queryForList("SELECT file_key FROM xsy_product_image WHERE spu_id = ? AND deleted = FALSE "
                    + "ORDER BY file_key", String.class, spuId);
        }

        private int activeRelationCount(String fileKey) {
            return jdbc.queryForObject("SELECT count(*) FROM t_file_relation WHERE file_key = ? "
                    + "AND deleted_flag = FALSE", Integer.class, fileKey);
        }
    }
}
