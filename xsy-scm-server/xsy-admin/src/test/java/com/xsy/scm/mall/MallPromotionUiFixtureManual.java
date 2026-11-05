package com.xsy.scm.mall;

import static org.assertj.core.api.Assertions.assertThat;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.mall.account.domain.form.MallAccountCreateForm;
import com.xsy.scm.mall.account.service.MallAccountService;
import com.xsy.scm.mall.address.domain.form.MallAddressSaveForm;
import com.xsy.scm.mall.address.service.MallAddressService;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
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

/** 只在显式指定隔离库时创建商城营销页面联调所需的客户、商品及收货地址。 */
class MallPromotionUiFixtureManual extends ScmW5PgITBase {

    @Autowired
    private MallAccountService accountService;

    @Autowired
    private MallAddressService addressService;

    @Test
    @EnabledIfSystemProperty(named = "scm.mall.promotion.ui-fixture", matches = "true")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void createDisposableCustomerAndProduct() throws IOException {
        String database = jdbc.queryForObject("SELECT current_database()", String.class);
        String expectedDatabase = System.getProperty("scm.mall.promotion.ui-db", "");
        assertThat(expectedDatabase).matches("xsy_(verify|codex_mkt)_[A-Za-z0-9_]+");
        assertThat(database).isEqualTo(expectedDatabase);

        Long customerId = newCustomer();
        Long skuId = newOnShelfSku("MKTUI");
        String phone = "139" + String.format("%08d", Integer.toUnsignedLong(UUID.randomUUID().hashCode())
                % 100_000_000);
        String temporaryPassword = "MktUi@" + UUID.randomUUID().toString().substring(0, 10);
        String newPassword = "MktUi@" + UUID.randomUUID().toString().substring(0, 10);
        MallAccountCreateForm account = new MallAccountCreateForm();
        account.setCustomerId(customerId);
        account.setLoginPhone(phone);
        account.setTemporaryPassword(temporaryPassword);
        Long accountId = accountService.create(account).getAccountId();

        RequestMallCustomer identity = new RequestMallCustomer();
        identity.setAccountId(accountId);
        identity.setCustomerId(customerId);
        MallAddressSaveForm address = new MallAddressSaveForm();
        address.setReceiverName("营销联调客户");
        address.setReceiverPhone(phone);
        address.setProvinceName("上海市");
        address.setCityName("上海市");
        address.setDistrictName("浦东新区");
        address.setDetailAddress("隔离库联调路 1 号");
        address.setIsDefault(true);
        Long addressId = addressService.create(identity, address).getFirst().getAddressId();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("database", database);
        result.put("customerId", customerId);
        result.put("skuId", skuId);
        result.put("addressId", addressId);
        result.put("phone", phone);
        result.put("temporaryPassword", temporaryPassword);
        result.put("newPassword", newPassword);
        Path output = Path.of("../../.runtime/mkt-validation/accounts.json").toAbsolutePath().normalize();
        Files.createDirectories(output.getParent());
        Files.writeString(output, objectMapper.writeValueAsString(result));
    }
}
