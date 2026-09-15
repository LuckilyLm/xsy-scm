package net.lab1024.sa.admin.module.scm.customer;

import net.lab1024.sa.admin.module.scm.common.ScmW2PgITBase;
import net.lab1024.sa.admin.module.scm.customer.domain.entity.CustomerTypeEntity;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerAddForm;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerDeleteForm;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerTypeAddForm;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerTypeDeleteForm;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerTypeQueryForm;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerTypeUpdateForm;
import net.lab1024.sa.admin.module.scm.customer.domain.vo.CustomerTypeVO;
import net.lab1024.sa.admin.module.scm.customer.service.CustomerService;
import net.lab1024.sa.admin.module.scm.customer.service.CustomerTypeService;
import net.lab1024.sa.base.common.domain.PageParam;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 客户类型（可维护字典）在真实 PostgreSQL 上的行为（T11）。
 *
 * <p>重点是三件只有在数据库里才能成立的事：
 * partial unique index 让「软删后编码可复用」成立；引用检查必须在同一事务里读得到活动客户；
 * 排序白名单在真实分页路径上生效。
 */
@DisplayName("客户类型：可维护字典（PG IT）")
class CustomerTypeServiceIT extends ScmW2PgITBase {

    @Autowired
    private CustomerTypeService service;

    @Autowired
    private CustomerService customerService;

    private CustomerTypeAddForm form(String suffix, String status) {
        CustomerTypeAddForm form = new CustomerTypeAddForm();
        // 刻意加空白 + 小写，验证归一化真的落到了库里
        form.setTypeCode(" " + prefix + "-" + suffix + " ");
        form.setName("类型" + suffix);
        form.setStatus(status);
        return form;
    }

    private CustomerAddForm customer(String suffix, Long typeId) {
        CustomerAddForm form = new CustomerAddForm();
        form.setCustomerCode(prefix + suffix);
        form.setName("客户" + suffix);
        form.setCustomerTypeId(typeId);
        return form;
    }

    @Test
    @DisplayName("编码归一化落库，初始版本 0，审计写入当前操作人")
    void normalizesCodeAndStampsOperator() {
        Long id = service.add(form("a", "ENABLED"));

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT type_code, name, status, version, deleted, created_by, updated_by "
                        + "FROM customer_type WHERE id = ?", id);
        assertThat(row.get("type_code")).isEqualTo((prefix + "-a").toUpperCase(Locale.ROOT));
        assertThat(row.get("name")).isEqualTo("类型a");
        assertThat(row.get("status")).isEqualTo("ENABLED");
        assertThat(((Number) row.get("version")).intValue()).isZero();
        assertThat(row.get("deleted")).isEqualTo(false);
        assertThat(row.get("created_by")).isEqualTo("1:1");
        assertThat(row.get("updated_by")).isEqualTo("1:1");
    }

    @Test
    @DisplayName("活动编码唯一：重复被拒 40937；软删后编码可复用")
    void duplicateActiveCodeRejectedAndReusableAfterDelete() {
        Long id = service.add(form("a", "ENABLED"));

        expectCode(() -> service.add(form("a", "ENABLED")), 40937);

        CustomerTypeDeleteForm deletion = new CustomerTypeDeleteForm();
        deletion.setTypeId(id);
        deletion.setVersion(0);
        service.delete(deletion);

        Long again = service.add(form("a", "ENABLED"));
        assertThat(again).as("partial unique index 只约束活动行").isNotEqualTo(id);
    }

    @Test
    @DisplayName("下拉只返回 ENABLED；停用类型不可被新客户引用（40431）")
    void optionListIsEnabledOnlyAndDisabledTypeIsNotSelectable() {
        Long enabled = service.add(form("e", "ENABLED"));
        Long disabled = service.add(form("d", "DISABLED"));

        assertThat(service.optionList()).extracting(CustomerTypeVO::getTypeId)
                .contains(enabled).doesNotContain(disabled);

        expectCode(() -> customerService.add(customer("C1", disabled)), 40431);
        assertThat(service.all()).extracting(CustomerTypeEntity::getId)
                .as("内部全量读取包含停用类型").contains(disabled);
    }

    @Test
    @DisplayName("更新推进版本；旧版本再次提交 → 40921")
    void updateAdvancesVersionAndStaleVersionConflicts() {
        Long id = service.add(form("u", "ENABLED"));

        CustomerTypeUpdateForm update = new CustomerTypeUpdateForm();
        update.setTypeId(id);
        update.setVersion(0);
        update.setTypeCode(prefix + "-u");
        update.setName("改名");
        update.setStatus("DISABLED");
        service.update(update);

        assertThat(jdbc.queryForObject("SELECT version FROM customer_type WHERE id = ?", Integer.class, id))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT name FROM customer_type WHERE id = ?", String.class, id))
                .isEqualTo("改名");

        expectCode(() -> service.update(update), 40921);
    }

    @Test
    @DisplayName("被活动客户引用时拒绝删除（40938）；客户软删后即可删除")
    void deleteIsBlockedWhileCustomersReferenceTheType() {
        Long typeId = service.add(form("r", "ENABLED"));
        Long customerId = customerService.add(customer("R1", typeId));

        CustomerTypeDeleteForm deletion = new CustomerTypeDeleteForm();
        deletion.setTypeId(typeId);
        deletion.setVersion(0);
        expectCode(() -> service.delete(deletion), 40938);

        CustomerDeleteForm customerDeletion = new CustomerDeleteForm();
        customerDeletion.setCustomerId(customerId);
        customerDeletion.setVersion(0);
        customerService.delete(customerDeletion);

        service.delete(deletion);
        assertThat(jdbc.queryForObject("SELECT deleted FROM customer_type WHERE id = ?", Boolean.class, typeId))
                .isTrue();
    }

    @Test
    @DisplayName("关键字 / 状态筛选可用，排序白名单拒绝未知列（40000）")
    void queryFiltersAndEnforcesSortWhitelist() {
        Long enabled = service.add(form("q", "ENABLED"));
        service.add(form("qd", "DISABLED"));

        CustomerTypeQueryForm byKeyword = new CustomerTypeQueryForm();
        byKeyword.setPageNum(1L);
        byKeyword.setPageSize(50L);
        byKeyword.setKeyword(prefix);
        assertThat(service.query(byKeyword).getList()).extracting(CustomerTypeVO::getTypeId)
                .contains(enabled);

        CustomerTypeQueryForm byStatus = new CustomerTypeQueryForm();
        byStatus.setPageNum(1L);
        byStatus.setPageSize(50L);
        byStatus.setKeyword(prefix);
        byStatus.setStatus("DISABLED");
        assertThat(service.query(byStatus).getList()).extracting(CustomerTypeVO::getTypeCode)
                .containsExactlyInAnyOrder((prefix + "-QD").toUpperCase(Locale.ROOT));

        PageParam.SortItem illegal = new PageParam.SortItem();
        illegal.setIsAsc(true);
        illegal.setColumn("name; DROP TABLE customer_type");
        CustomerTypeQueryForm badSort = new CustomerTypeQueryForm();
        badSort.setPageNum(1L);
        badSort.setPageSize(10L);
        badSort.setSortItemList(List.of(illegal));
        expectCode(() -> service.query(badSort), 40000);

        PageParam.SortItem legal = new PageParam.SortItem();
        legal.setIsAsc(true);
        legal.setColumn("TYPE_CODE");
        CustomerTypeQueryForm goodSort = new CustomerTypeQueryForm();
        goodSort.setPageNum(1L);
        goodSort.setPageSize(10L);
        goodSort.setKeyword(prefix);
        goodSort.setSortItemList(List.of(legal));
        assertThat(service.query(goodSort).getList()).isNotEmpty();
    }
}
