package com.xsy.scm.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.customer.domain.form.CustomerGroupAddForm;
import com.xsy.scm.customer.domain.form.CustomerGroupDeleteForm;
import com.xsy.scm.customer.domain.form.CustomerGroupUpdateForm;
import com.xsy.scm.customer.domain.vo.CustomerGroupVO;
import com.xsy.scm.customer.service.CustomerGroupService;
import com.xsy.scm.pricing.constant.ScmPriceSourceEnum;
import com.xsy.scm.pricing.domain.form.CustomerGroupPriceAddForm;
import com.xsy.scm.pricing.domain.form.CustomerGroupPriceQueryForm;
import com.xsy.scm.pricing.domain.form.PriceHistoryQueryForm;
import com.xsy.scm.pricing.domain.vo.PriceHistoryVO;
import com.xsy.scm.pricing.domain.vo.ResolvedPriceVO;
import com.xsy.scm.pricing.service.CustomerGroupPriceQueryService;
import com.xsy.scm.pricing.service.CustomerGroupPriceService;
import com.xsy.scm.pricing.service.PriceHistoryQueryService;
import com.xsy.scm.pricing.service.PriceResolver;
import com.xsy.scm.product.domain.form.ProductSkuForm;
import com.xsy.scm.product.domain.form.ProductSpuAddForm;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 客户分组价的落库与取价链路。
 *
 * <p>分组价按「分组 + 商品 + 销售单位」落一条，组内同单位的不同规格共用这条价格；
 * 停用分组、删除分组都必须让价格立即失效，但审计记录与历史价继续可查。
 */
class CustomerGroupPricePgIT extends ScmW5PgITBase {

    @Autowired
    private CustomerGroupService customerGroupService;

    @Autowired
    private CustomerGroupPriceService customerGroupPriceService;

    @Autowired
    private CustomerGroupPriceQueryService customerGroupPriceQueryService;

    @Autowired
    private PriceResolver priceResolver;

    @Autowired
    private PriceHistoryQueryService priceHistoryQueryService;

    @Test
    @DisplayName("#1 组内同单位规格共用一个分组价，组外客户仍走市场价")
    void groupPriceCoversEverySpecInSameUnit() {
        SkuPair skus = newSpuWithTwoSkus("CGP1");
        Long member = newCustomer();
        Long outsider = newCustomer();
        Group group = createGroup("CGP1", member);
        OffsetDateTime now = OffsetDateTime.now();

        Long groupPriceId = addPrice(group.id(), skus.first(), "3.3000",
            now.minusHours(1), now.plusHours(2));

        List<ResolvedPriceVO> memberPrices = priceResolver.resolve(member,
            List.of(skus.first(), skus.second()), now);
        assertThat(memberPrices).hasSize(2).allSatisfy(price -> {
            assertThat(price.getPriceSource()).isEqualTo(ScmPriceSourceEnum.CUSTOMER_GROUP);
            assertThat(price.getUnitPrice()).isEqualByComparingTo("3.3000");
            assertThat(price.getSourceRecordId()).isEqualTo(groupPriceId);
        });

        ResolvedPriceVO outsiderPrice = priceResolver.resolve(outsider, List.of(skus.first()), now).getFirst();
        assertThat(outsiderPrice.getPriceSource()).isEqualTo(ScmPriceSourceEnum.MARKET);
        assertThat(outsiderPrice.getUnitPrice()).isEqualByComparingTo("1.2000");

        // 列表按商品维度展示，详情回填任一可用规格供编辑；不按规格复制成两行
        var page = customerGroupPriceQueryService.query(query(group.id()));
        assertThat(page.getList()).hasSize(1);
        assertThat(page.getList().getFirst().getGroupName()).isEqualTo(group.name());
        assertThat(page.getList().getFirst().getSaleUnit()).isEqualTo("kg");
    }

    @Test
    @DisplayName("#2 同分组/商品/单位区间重叠被拒（40956），首尾相接允许")
    void overlappingPeriodIsRejected() {
        Long skuId = newOnShelfSku("CGP2");
        Long member = newCustomer();
        Group group = createGroup("CGP2", member);
        OffsetDateTime now = OffsetDateTime.now();
        addPrice(group.id(), skuId, "2.0000", now.minusHours(2), now.plusHours(2));

        expectCode(() -> addPrice(group.id(), skuId, "2.5000", now.plusHours(1), now.plusHours(3)), 40956);

        Long adjacentId = addPrice(group.id(), skuId, "2.8000", now.plusHours(2), now.plusHours(4));
        assertThat(adjacentId).isNotNull();
    }

    @Test
    @DisplayName("#3 停用分组后不再取价，也不能再新增分组价（40435）")
    void disabledGroupIsIgnored() {
        Long skuId = newOnShelfSku("CGP3");
        Long member = newCustomer();
        Group group = createGroup("CGP3", member);
        OffsetDateTime now = OffsetDateTime.now();
        addPrice(group.id(), skuId, "4.0000", now.minusHours(1), now.plusHours(2));

        CustomerGroupVO detail = customerGroupService.detail(group.id());
        CustomerGroupUpdateForm update = new CustomerGroupUpdateForm();
        update.setGroupId(group.id());
        update.setVersion(detail.getVersion());
        update.setGroupCode(detail.getGroupCode());
        update.setGroupName(detail.getGroupName());
        update.setStatus("DISABLED");
        update.setCustomerIds(List.of(member));
        customerGroupService.update(update);

        ResolvedPriceVO price = priceResolver.resolve(member, List.of(skuId), now).getFirst();
        assertThat(price.getPriceSource()).isEqualTo(ScmPriceSourceEnum.MARKET);

        expectCode(() -> addPrice(group.id(), skuId, "4.5000", now.plusHours(3), now.plusHours(4)), 40435);
    }

    @Test
    @DisplayName("#4 客户是原分组唯一成员时不能移入新分组（40957）")
    void lastMemberCannotBeMovedOut() {
        Long onlyMember = newCustomer();
        Group first = createGroup("CGP4A", onlyMember);

        CustomerGroupAddForm second = new CustomerGroupAddForm();
        second.setGroupCode(prefix + "CGP4B");
        second.setGroupName(prefix + "第二组");
        second.setStatus("ENABLED");
        second.setCustomerIds(List.of(onlyMember));

        expectCode(() -> customerGroupService.add(second), 40957);
        assertThat(jdbc.queryForObject(
            "SELECT customer_group_id FROM xsy_customer WHERE id = ? AND deleted = FALSE",
            Long.class, onlyMember)).isEqualTo(first.id());
    }

    @Test
    @DisplayName("#5 删除分组会移出成员并立即停止取价，历史仍可按分组查")
    void deletingGroupDetachesMembersAndKeepsHistory() {
        Long skuId = newOnShelfSku("CGP5");
        Long firstMember = newCustomer();
        Long secondMember = newCustomer();
        Group group = createGroup("CGP5", firstMember, secondMember);
        OffsetDateTime now = OffsetDateTime.now();
        addPrice(group.id(), skuId, "5.6000", now.minusHours(1), now.plusHours(2));

        CustomerGroupDeleteForm delete = new CustomerGroupDeleteForm();
        delete.setGroupId(group.id());
        delete.setVersion(customerGroupService.detail(group.id()).getVersion());
        customerGroupService.delete(delete);

        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM xsy_customer WHERE id IN (?, ?) AND customer_group_id IS NOT NULL",
            Integer.class, firstMember, secondMember)).isZero();
        assertThat(priceResolver.resolve(firstMember, List.of(skuId), now).getFirst().getPriceSource())
            .isEqualTo(ScmPriceSourceEnum.MARKET);

        PriceHistoryQueryForm history = new PriceHistoryQueryForm();
        history.setPageNum(1L);
        history.setPageSize(20L);
        history.setSource("CUSTOMER_GROUP");
        history.setGroupId(group.id());
        var page = priceHistoryQueryService.query(history);
        assertThat(page.getList()).isNotEmpty();
        PriceHistoryVO record = page.getList().getFirst();
        assertThat(record.getOperationType()).isEqualTo("CREATE");
        assertThat(record.getGroupName()).isEqualTo(group.name());
        assertThat(record.getCurrentUnitPrice()).isEqualByComparingTo("5.6000");
    }

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    private record Group(Long id, String name) {
    }

    private record SkuPair(Long first, Long second) {
    }

    private Group createGroup(String suffix, Long... customerIds) {
        CustomerGroupAddForm form = new CustomerGroupAddForm();
        form.setGroupCode(prefix + "G" + suffix);
        form.setGroupName(prefix + "分组" + suffix);
        form.setStatus("ENABLED");
        form.setCustomerIds(List.of(customerIds));
        Long groupId = customerGroupService.add(form);
        return new Group(groupId, form.getGroupName());
    }

    private Long addPrice(Long groupId, Long skuId, String unitPrice, OffsetDateTime from, OffsetDateTime to) {
        CustomerGroupPriceAddForm form = new CustomerGroupPriceAddForm();
        form.setGroupId(groupId);
        form.setSkuId(skuId);
        form.setUnitPrice(unitPrice);
        form.setEffectiveFrom(from);
        form.setEffectiveTo(to);
        return customerGroupPriceService.add(form);
    }

    private CustomerGroupPriceQueryForm query(Long groupId) {
        var form = new CustomerGroupPriceQueryForm();
        form.setPageNum(1L);
        form.setPageSize(20L);
        form.setGroupId(groupId);
        return form;
    }

    /**
     * 同一商品下两个规格、同一销售单位：分组价必须同时覆盖两者。
     */
    private SkuPair newSpuWithTwoSkus(String suffix) {
        ProductSpuAddForm form = new ProductSpuAddForm();
        form.setSpuCode(prefix + suffix);
        form.setName(prefix + suffix + "商品");
        form.setStatus("ON_SHELF");
        form.setCategoryId(jdbc.queryForObject(
            "SELECT id FROM xsy_product_category WHERE category_code = 'FRESH-FRUIT' AND deleted = FALSE", Long.class));

        ProductSkuForm first = sku(suffix + "-A", "规格A", true);
        ProductSkuForm second = sku(suffix + "-B", "规格B", false);
        form.setSkuList(new ArrayList<>(List.of(first, second)));

        Long spuId = productSpuService.add(form);
        List<Long> ids = jdbc.queryForList(
            "SELECT id FROM xsy_product_sku WHERE spu_id = ? AND deleted = FALSE ORDER BY id", Long.class, spuId);
        assertThat(ids).hasSize(2);
        return new SkuPair(ids.get(0), ids.get(1));
    }

    private ProductSkuForm sku(String suffix, String specName, boolean defaultFlag) {
        ProductSkuForm sku = new ProductSkuForm();
        sku.setSkuCode(prefix + suffix);
        sku.setSpecName(specName);
        sku.setSpecValues(new LinkedHashMap<>(Map.of("规格", suffix)));
        sku.setSaleUnit("kg");
        sku.setProductType("NON_STANDARD");
        sku.setMarketPrice(new BigDecimal("1.2000"));
        sku.setStatus("ON_SHELF");
        sku.setDefaultFlag(defaultFlag);
        return sku;
    }
}
