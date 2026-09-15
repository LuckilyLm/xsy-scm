package net.lab1024.sa.admin.module.scm.customer;

import com.fasterxml.jackson.databind.JsonNode;
import net.lab1024.sa.admin.module.scm.common.ScmW2PgITBase;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerAddForm;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerQueryForm;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerStatusForm;
import net.lab1024.sa.admin.module.scm.customer.domain.vo.CustomerDetailVO;
import net.lab1024.sa.admin.module.scm.customer.domain.vo.CustomerOptionVO;
import net.lab1024.sa.admin.module.scm.customer.domain.vo.CustomerVO;
import net.lab1024.sa.admin.module.scm.customer.service.CustomerQueryService;
import net.lab1024.sa.admin.module.scm.customer.service.CustomerService;
import net.lab1024.sa.admin.module.scm.supplier.domain.form.SupplierAddForm;
import net.lab1024.sa.admin.module.scm.supplier.service.SupplierService;
import net.lab1024.sa.base.common.domain.PageParam;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 客户读路径在真实 PostgreSQL 上的行为（T11）。
 *
 * <p>重点是两件在纯单测里证明不了的事：
 * <ul>
 *   <li><b>批量补全</b>（类型名 / 上级客户名 / 业务员名 / 供应商名）真的从关联表读到了名字；</li>
 *   <li><b>金额契约</b>——{@code NUMERIC(18,4)} 序列化成 4 位小数字符串，且 {@code null} 保持 {@code null}
 *       （不能被写成 {@code "0.0000"}，否则「未设置账期」与「账期为 0」会被混淆）。</li>
 * </ul>
 */
@DisplayName("客户：读路径与补全（PG IT）")
class CustomerQueryServiceIT extends ScmW2PgITBase {

    @Autowired
    private CustomerService service;

    @Autowired
    private CustomerQueryService queryService;

    @Autowired
    private SupplierService supplierService;

    private Long enterpriseTypeId() {
        return customerTypeId("ENTERPRISE");
    }

    private CustomerAddForm form(String suffix) {
        CustomerAddForm form = new CustomerAddForm();
        form.setCustomerCode(prefix + suffix);
        form.setName("客户" + suffix);
        form.setCustomerTypeId(enterpriseTypeId());
        return form;
    }

    private CustomerQueryForm queryForm() {
        CustomerQueryForm form = new CustomerQueryForm();
        form.setPageNum(1L);
        form.setPageSize(50L);
        form.setKeyword(prefix);
        return form;
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "CONTACT-NAME", "13800001111"})
    @DisplayName("关键字覆盖编码 / 名称 / 联系人 / 联系电话")
    void keywordCoversCodeNameContactAndPhone(String field) {
        CustomerAddForm form = form("K1");
        form.setContactName(prefix + "CONTACT-NAME");
        form.setContactPhone("13800001111");
        Long id = service.add(form);

        CustomerQueryForm query = queryForm();
        query.setKeyword(field.isEmpty() ? prefix : field);
        assertThat(queryService.query(query).getList()).extracting(CustomerVO::getCustomerId).contains(id);
    }

    @Test
    @DisplayName("状态 / 类型 / 结算方式 / 上级客户筛选都生效")
    void filtersAreApplied() {
        Long groupTypeId = customerTypeId("GROUP");
        Long group = service.add(groupForm("G1", groupTypeId));
        CustomerAddForm childForm = form("F1");
        childForm.setParentCustomerId(group);
        Long child = service.add(childForm);

        CustomerStatusForm status = new CustomerStatusForm();
        status.setCustomerId(child);
        status.setVersion(0);
        status.setStatus("COOPERATING");
        service.updateStatus(status);

        CustomerQueryForm byStatus = queryForm();
        byStatus.setStatus("COOPERATING");
        assertThat(queryService.query(byStatus).getList()).extracting(CustomerVO::getCustomerId)
                .containsExactly(child);

        CustomerQueryForm byType = queryForm();
        byType.setCustomerTypeId(groupTypeId);
        assertThat(queryService.query(byType).getList()).extracting(CustomerVO::getCustomerId)
                .containsExactly(group);

        CustomerQueryForm bySettleMode = queryForm();
        bySettleMode.setSettleMode("GROUP");
        assertThat(queryService.query(bySettleMode).getList()).extracting(CustomerVO::getCustomerId)
                .containsExactly(group);

        CustomerQueryForm byParent = queryForm();
        byParent.setParentCustomerId(group);
        assertThat(queryService.query(byParent).getList()).extracting(CustomerVO::getCustomerId)
                .containsExactly(child);
    }

    private CustomerAddForm groupForm(String suffix, Long groupTypeId) {
        CustomerAddForm form = new CustomerAddForm();
        form.setCustomerCode(prefix + suffix);
        form.setName("集团" + suffix);
        form.setCustomerTypeId(groupTypeId);
        form.setSettleMode("GROUP");
        return form;
    }

    @Test
    @DisplayName("列表与详情批量补全类型名 / 上级客户名 / 业务员名 / 供应商名")
    void enrichmentResolvesNamesFromRelatedTables() {
        Long groupTypeId = customerTypeId("GROUP");
        Long group = service.add(groupForm("G2", groupTypeId));
        Long employeeId = anyEmployeeId();

        SupplierAddForm supplierForm = new SupplierAddForm();
        supplierForm.setSupplierCode(prefix + "-SUP");
        supplierForm.setName("供应商" + prefix);
        Long supplierId = supplierService.add(supplierForm);

        CustomerAddForm form = form("E1");
        form.setParentCustomerId(group);
        form.setSellerId(employeeId);
        form.setSupplierId(supplierId);
        Long id = service.add(form);

        CustomerVO row = queryService.query(queryForm()).getList().stream()
                .filter(vo -> vo.getCustomerId().equals(id)).findFirst().orElseThrow();
        assertThat(row.getCustomerTypeName()).isEqualTo("企业");
        assertThat(row.getParentCustomerName()).isEqualTo("集团G2");
        assertThat(row.getSellerName()).isNotBlank();

        CustomerDetailVO detail = queryService.detail(id);
        assertThat(detail.getCustomerTypeName()).isEqualTo("企业");
        assertThat(detail.getParentCustomerName()).isEqualTo("集团G2");
        assertThat(detail.getSellerName()).isNotBlank();
        assertThat(detail.getSupplierName()).isEqualTo("供应商" + prefix);
    }

    @Test
    @DisplayName("金额契约：授信额度序列化为 4 位小数字符串，未设置的账期阈值保持 null")
    void decimalContractSurvivesSerialization() throws Exception {
        CustomerAddForm form = form("J1");
        form.setCreditLimit("1234.5");
        Long id = service.add(form);

        JsonNode detail = json.readTree(json.writeValueAsString(queryService.detail(id)));
        assertThat(detail.get("creditLimit").asText()).isEqualTo("1234.5000");
        assertThat(detail.get("creditAmountThreshold").isNull())
                .as("null 不得被序列化成 0.0000").isTrue();

        CustomerAddForm withThreshold = form("J2");
        withThreshold.setCreditPeriodType("BY_AMOUNT");
        withThreshold.setCreditAmountThreshold("0");
        Long thresholdId = service.add(withThreshold);
        JsonNode threshold = json.readTree(json.writeValueAsString(queryService.detail(thresholdId)));
        assertThat(threshold.get("creditAmountThreshold").asText()).isEqualTo("0.0000");
    }

    @Test
    @DisplayName("详情不存在 → 40430；下拉返回全部活动客户并带状态")
    void detailMissingAndOptionList() {
        expectCode(() -> queryService.detail(-1L), 40430);

        Long id = service.add(form("O1"));
        List<CustomerOptionVO> options = queryService.optionList();
        CustomerOptionVO mine = options.stream()
                .filter(vo -> vo.getCustomerId().equals(id)).findFirst().orElseThrow();
        assertThat(mine.getStatus()).isEqualTo("POTENTIAL");
        assertThat(mine.getCustomerCode()).isEqualTo((prefix + "O1").toUpperCase(Locale.ROOT));
    }

    @Test
    @DisplayName("排序白名单：合法列通过，未知列 40000")
    void sortWhitelistIsEnforced() {
        Long id = service.add(form("S1"));

        PageParam.SortItem legal = new PageParam.SortItem();
        legal.setIsAsc(false);
        legal.setColumn("updated_at");
        CustomerQueryForm good = queryForm();
        good.setSortItemList(List.of(legal));
        assertThat(queryService.query(good).getList()).extracting(CustomerVO::getCustomerId).contains(id);

        PageParam.SortItem illegal = new PageParam.SortItem();
        illegal.setIsAsc(true);
        illegal.setColumn("credit_limit");
        CustomerQueryForm bad = queryForm();
        bad.setSortItemList(List.of(illegal));
        expectCode(() -> queryService.query(bad), 40000);
    }
}
