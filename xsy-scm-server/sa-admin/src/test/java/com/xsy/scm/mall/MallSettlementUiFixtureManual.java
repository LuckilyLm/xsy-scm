package com.xsy.scm.mall;

import static org.assertj.core.api.Assertions.assertThat;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.customer.domain.entity.CustomerEntity;
import com.xsy.scm.customer.domain.form.CustomerUpdateForm;
import com.xsy.scm.mall.account.domain.form.MallAccountCreateForm;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.mall.account.service.MallAccountService;
import com.xsy.scm.mall.address.domain.form.MallAddressSaveForm;
import com.xsy.scm.mall.address.service.MallAddressService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 显式启用时才向指定隔离库写入结算页面联调客户。 */
class MallSettlementUiFixtureManual extends ScmW5PgITBase {

    @Autowired
    private MallAccountService accountService;

    @Autowired
    private MallAddressService addressService;

    @Test
    @EnabledIfSystemProperty(named = "scm.mall.settlement.ui-fixture", matches = "true")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void createDisposableCustomer() throws IOException {
        String expectedDatabase = System.getProperty("scm.mall.settlement.ui-db", "");
        assertThat(expectedDatabase).matches("xsy_codex_set_[A-Za-z0-9_]+");
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo(expectedDatabase);

        Long customerId = newCustomer();
        Long skuId = newOnShelfSku("SETUI");
        assertThat(jdbc.queryForObject("SELECT cod_allowed FROM xsy_customer WHERE id = ?", Boolean.class, customerId))
                .isFalse();
        String phone = "139" + String.format("%08d", Integer.toUnsignedLong(UUID.randomUUID().hashCode())
                % 100_000_000);
        String temporaryPassword = "SetUi@" + UUID.randomUUID().toString().substring(0, 10);
        String newPassword = "SetUi@" + UUID.randomUUID().toString().substring(0, 10);
        MallAccountCreateForm account = new MallAccountCreateForm();
        account.setCustomerId(customerId);
        account.setLoginPhone(phone);
        account.setTemporaryPassword(temporaryPassword);
        Long accountId = accountService.create(account).getAccountId();

        RequestMallCustomer identity = new RequestMallCustomer();
        identity.setCustomerId(customerId);
        identity.setAccountId(accountId);
        MallAddressSaveForm address = new MallAddressSaveForm();
        address.setReceiverName("结算联调客户");
        address.setReceiverPhone(phone);
        address.setProvinceName("上海市");
        address.setCityName("上海市");
        address.setDistrictName("浦东新区");
        address.setDetailAddress("结算隔离联调路 1 号");
        address.setIsDefault(true);
        Long addressId = addressService.create(identity, address).getFirst().getAddressId();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("database", expectedDatabase);
        result.put("customerId", customerId);
        result.put("skuId", skuId);
        result.put("addressId", addressId);
        result.put("phone", phone);
        result.put("temporaryPassword", temporaryPassword);
        result.put("newPassword", newPassword);
        Path output = Path.of("../../.runtime/set-v130/accounts.json").toAbsolutePath().normalize();
        Files.createDirectories(output.getParent());
        Files.writeString(output, objectMapper.writeValueAsString(result));
    }

    @Test
    @EnabledIfSystemProperty(named = "scm.mall.settlement.open-cod", matches = "true")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void openCodThroughCustomerCommand() throws IOException {
        String expectedDatabase = System.getProperty("scm.mall.settlement.ui-db", "");
        assertThat(expectedDatabase).matches("xsy_codex_set_[A-Za-z0-9_]+");
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo(expectedDatabase);
        Path accountFile = Path.of("../../.runtime/set-v130/accounts.json").toAbsolutePath().normalize();
        @SuppressWarnings("unchecked")
        Map<String, Object> account = objectMapper.readValue(Files.readString(accountFile), Map.class);
        Long customerId = ((Number) account.get("customerId")).longValue();
        CustomerEntity customer = customerService.require(customerId);
        CustomerUpdateForm form = new CustomerUpdateForm();
        form.setCustomerId(customerId);
        form.setVersion(customer.getVersion());
        form.setName(customer.getName());
        form.setCustomerTypeId(customer.getCustomerTypeId());
        form.setSettleMode(customer.getSettleMode());
        form.setSettlementCustomerId(customer.getSettlementCustomerId());
        form.setCreditLimit(customer.getCreditLimit().toPlainString());
        form.setCodAllowed(true);
        customerService.update(form);
        assertThat(customerService.require(customerId).getCodAllowed()).isTrue();
    }
}
