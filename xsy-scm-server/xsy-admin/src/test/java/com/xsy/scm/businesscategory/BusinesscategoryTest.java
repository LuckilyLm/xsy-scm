package com.xsy.scm.businesscategory;

import com.xsy.scm.base.common.domain.SystemEnvironment;
import com.xsy.scm.base.handler.GlobalExceptionHandler;
import com.xsy.scm.businesscategory.constant.BusinessCategoryScopeEnum;
import com.xsy.scm.businesscategory.controller.BusinessCategoryController;
import com.xsy.scm.businesscategory.domain.entity.BusinessCategoryEntity;
import com.xsy.scm.businesscategory.domain.form.BusinessCategoryAddForm;
import com.xsy.scm.businesscategory.domain.form.BusinessCategoryDeleteForm;
import com.xsy.scm.businesscategory.domain.form.BusinessCategoryUpdateForm;
import com.xsy.scm.businesscategory.domain.vo.BusinessCategoryTreeVO;
import com.xsy.scm.businesscategory.service.BusinessCategoryService;
import com.xsy.scm.common.ScmW2PgITBase;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.handler.ScmExceptionHandler;
import com.xsy.scm.customer.domain.form.CustomerAddForm;
import com.xsy.scm.customer.service.CustomerService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.api.Nested;

import static com.xsy.scm.businesscategory.constant.BusinessCategoryErrorCode.BUSINESS_CATEGORY_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BusinesscategoryTest {

    @Nested
    @WebMvcTest(BusinessCategoryController.class)
    @AutoConfigureMockMvc(addFilters = false)
    @ContextConfiguration(classes = {BusinessCategoryController.class, ScmExceptionHandler.class,
        GlobalExceptionHandler.class})
    class BusinessCategoryControllerTest {
        @Autowired
        private MockMvc mvc;

        @MockitoBean
        private BusinessCategoryService service;

        @MockitoBean(name = "systemEnvironment")
        private SystemEnvironment environment;

        @Test
        @DisplayName("树接口按 scope 返回数组信封")
        void treeReturnsEnvelope() throws Exception {
            when(service.tree("CUSTOMER")).thenReturn(List.of(new BusinessCategoryTreeVO()));

            mvc.perform(post("/scm/business-category/tree").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"scope\":\"CUSTOMER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());
        }

        @Test
        @DisplayName("新增返回分类主键")
        void addReturnsCategoryId() throws Exception {
            when(service.add(any())).thenReturn(88L);

            mvc.perform(post("/scm/business-category/add").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"scope\":\"CUSTOMER\",\"categoryCode\":\"VEG\",\"name\":\"蔬菜\","
                        + "\"sortOrder\":0,\"status\":\"ENABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(88));
        }

        @Test
        @DisplayName("分类不存在 → 40444")
        void mapsNotFound() throws Exception {
            when(service.detail(1L)).thenThrow(new ScmBusinessException(BUSINESS_CATEGORY_NOT_FOUND));

            mvc.perform(get("/scm/business-category/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40444));
        }

        @Test
        @DisplayName("非法作用域被参数校验拦下")
        void rejectsUnknownScope() throws Exception {
            mvc.perform(post("/scm/business-category/tree").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"scope\":\"UNKNOWN\"}"))
                .andExpect(jsonPath("$.code").value(30001));
        }
    }

    @Nested
    @DisplayName("客户供应商分类：树与约束（PG IT）")
    class BusinessCategoryServiceIT extends ScmW2PgITBase {
        @Autowired
        private BusinessCategoryService service;

        @Autowired
        private CustomerService customerService;

        private BusinessCategoryAddForm form(String scope, String code, String name, Long parentId) {
            BusinessCategoryAddForm form = new BusinessCategoryAddForm();
            form.setScope(scope);
            form.setCategoryCode(code);
            form.setName(name);
            form.setParentId(parentId);
            form.setSortOrder(0);
            form.setStatus("ENABLED");
            return form;
        }

        private BusinessCategoryUpdateForm updateForm(BusinessCategoryEntity entity, Long parentId, String name) {
            BusinessCategoryUpdateForm form = new BusinessCategoryUpdateForm();
            form.setCategoryId(entity.getId());
            form.setVersion(entity.getVersion());
            form.setScope(entity.getScope());
            form.setCategoryCode(entity.getCategoryCode());
            form.setName(name);
            form.setParentId(parentId);
            form.setSortOrder(entity.getSortOrder());
            form.setStatus(entity.getStatus());
            return form;
        }

        private Long seeded(String scope, String code) {
            return jdbc.queryForObject(
                "SELECT id FROM xsy_business_category WHERE scope = ? AND category_code = ? AND deleted = FALSE",
                Long.class, scope, code);
        }

        @Test
        @DisplayName("客户与供应商各自成树，种子分类按作用域隔离")
        void treeIsIsolatedByScope() {
            assertThat(service.tree("CUSTOMER")).extracting(BusinessCategoryTreeVO::getCategoryCode)
                .contains("CUSTOMER_VEG", "CUSTOMER_EGG", "CUSTOMER_MEAT")
                .doesNotContain("SUPPLIER_VEG");
            assertThat(service.tree("SUPPLIER")).extracting(BusinessCategoryTreeVO::getCategoryCode)
                .contains("SUPPLIER_VEG", "SUPPLIER_EGG", "SUPPLIER_MEAT")
                .doesNotContain("CUSTOMER_VEG");

            BusinessCategoryTreeVO veg = service.tree("CUSTOMER").stream()
                .filter(row -> "CUSTOMER_VEG".equals(row.getCategoryCode())).findFirst().orElseThrow();
            assertThat(veg.getCategoryPath()).isEqualTo("蔬菜");
            assertThat(veg.getLevel()).isEqualTo(1);
        }

        @Test
        @DisplayName("最多三级：第四级返回 40045，路径包含完整上级名称")
        void levelStopsAtThree() {
            Long levelTwo = service.add(
                form("CUSTOMER", prefix + "L2", prefix + "二级", seeded("CUSTOMER", "CUSTOMER_VEG")));
            Long levelThree = service.add(form("CUSTOMER", prefix + "L3", prefix + "三级", levelTwo));

            BusinessCategoryEntity third = service.require(levelThree);
            assertThat(third.getLevel()).isEqualTo(3);
            assertThat(service.detail(levelThree).getCategoryPath())
                .isEqualTo("蔬菜 / " + prefix + "二级 / " + prefix + "三级");

            expectCode(
                () -> service.add(form("CUSTOMER", prefix + "L4", prefix + "四级", levelThree)), 40045);
        }

        @Test
        @DisplayName("父级必须同作用域且启用；自引用与跨作用域都返回 40044")
        void parentMustMatchScopeAndBeEnabled() {
            Long customerRoot = seeded("CUSTOMER", "CUSTOMER_VEG");
            expectCode(() -> service.add(form("SUPPLIER", prefix + "CROSS", prefix + "跨域", customerRoot)), 40044);

            Long disabled = service.add(form("CUSTOMER", prefix + "OFF", prefix + "停用父级", null));
            BusinessCategoryEntity entity = service.require(disabled);
            BusinessCategoryUpdateForm disable = updateForm(entity, null, entity.getName());
            disable.setStatus("DISABLED");
            service.update(disable);

            expectCode(() -> service.add(form("CUSTOMER", prefix + "CHILD", prefix + "停用子级", disabled)), 40044);

            Long self = service.add(form("CUSTOMER", prefix + "SELF", prefix + "自引用", null));
            BusinessCategoryEntity selfEntity = service.require(self);
            expectCode(() -> service.update(updateForm(selfEntity, self, selfEntity.getName())), 40044);
        }

        @Test
        @DisplayName("同作用域内编码和名称唯一；不同作用域允许同名")
        void codeAndNameAreUniqueWithinScope() {
            service.add(form("CUSTOMER", prefix + "CODE", prefix + "唯一分类", null));
            expectCode(() -> service.add(form("CUSTOMER", prefix + "CODE", prefix + "另一个", null)), 40950);
            expectCode(() -> service.add(form("CUSTOMER", prefix + "CODE2", prefix + "唯一分类", null)), 40951);

            service.add(form("SUPPLIER", prefix + "CODE", prefix + "唯一分类", null));
        }

        @Test
        @DisplayName("移动分类不能挂到自己的后代下；同层级重命名允许")
        void moveCannotCreateCycleButRenameIsAllowed() {
            Long root = service.add(form("CUSTOMER", prefix + "ROOT", prefix + "根", null));
            Long child = service.add(form("CUSTOMER", prefix + "CHILD", prefix + "子", root));

            BusinessCategoryEntity rootEntity = service.require(root);
            expectCode(() -> service.update(updateForm(rootEntity, child, rootEntity.getName())), 40044);

            BusinessCategoryEntity childEntity = service.require(child);
            service.update(updateForm(childEntity, root, prefix + "子改名"));
            assertThat(service.require(child).getName()).isEqualTo(prefix + "子改名");
        }

        @Test
        @DisplayName("带后代的分类不能跨层级移动，避免后代 level 失真")
        void movingCategoryWithDescendantsAcrossLevelsIsRejected() {
            Long root = service.add(form("CUSTOMER", prefix + "MOVE", prefix + "移动根", null));
            service.add(form("CUSTOMER", prefix + "MOVECHILD", prefix + "移动子", root));
            Long otherRoot = service.add(form("CUSTOMER", prefix + "OTHER", prefix + "另一个根", null));

            BusinessCategoryEntity rootEntity = service.require(root);
            expectCode(() -> service.update(updateForm(rootEntity, otherRoot, rootEntity.getName())), 40044);
        }

        @Test
        @DisplayName("删除受子分类与客户 / 供应商引用守卫；无引用时可软删")
        void deleteChecksChildrenAndReferences() {
            Long root = service.add(form("CUSTOMER", prefix + "DEL", prefix + "待删根", null));
            Long child = service.add(form("CUSTOMER", prefix + "DELCHILD", prefix + "待删子", root));

            BusinessCategoryDeleteForm rootDelete = new BusinessCategoryDeleteForm();
            rootDelete.setCategoryId(root);
            rootDelete.setVersion(service.require(root).getVersion());
            expectCode(() -> service.delete(rootDelete), 40952);

            BusinessCategoryDeleteForm childDelete = new BusinessCategoryDeleteForm();
            childDelete.setCategoryId(child);
            childDelete.setVersion(service.require(child).getVersion());
            service.delete(childDelete);
            expectCode(() -> service.require(child), 40444);

            Long referenced = service.add(form("CUSTOMER", prefix + "REF", prefix + "被引用", null));
            CustomerAddForm customerForm = new CustomerAddForm();
            customerForm.setName(prefix + "引用客户");
            customerForm.setBusinessCategoryId(referenced);
            customerService.add(customerForm);

            BusinessCategoryDeleteForm referencedDelete = new BusinessCategoryDeleteForm();
            referencedDelete.setCategoryId(referenced);
            referencedDelete.setVersion(service.require(referenced).getVersion());
            expectCode(() -> service.delete(referencedDelete), 40953);
        }

        @Test
        @DisplayName("停用分类不能再被客户 / 供应商引用")
        void disabledCategoryCannotBeSelectable() {
            Long id = service.add(form("CUSTOMER", prefix + "DIS", prefix + "停用分类", null));
            BusinessCategoryEntity entity = service.require(id);
            BusinessCategoryUpdateForm disable = updateForm(entity, null, entity.getName());
            disable.setStatus("DISABLED");
            service.update(disable);

            expectCode(() -> service.requireSelectable(id, BusinessCategoryScopeEnum.CUSTOMER), 40954);
        }
    }
}
