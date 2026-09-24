package net.lab1024.sa.admin.module.scm.customer.service;

import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeContext;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeService;
import net.lab1024.sa.admin.module.scm.customer.dao.CustomerDao;
import net.lab1024.sa.admin.module.scm.customer.dao.CustomerFrequentSkuDao;
import net.lab1024.sa.admin.module.scm.customer.dao.CustomerTypeDao;
import net.lab1024.sa.admin.module.scm.customer.domain.entity.CustomerEntity;
import net.lab1024.sa.admin.module.scm.customer.domain.entity.CustomerTypeEntity;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerQueryForm;
import net.lab1024.sa.admin.module.scm.customer.domain.vo.CustomerDetailVO;
import net.lab1024.sa.admin.module.scm.customer.domain.vo.CustomerFrequentSkuVO;
import net.lab1024.sa.admin.module.scm.customer.domain.vo.CustomerOptionVO;
import net.lab1024.sa.admin.module.scm.customer.domain.vo.CustomerVO;
import net.lab1024.sa.admin.module.scm.supplier.dao.SupplierDao;
import net.lab1024.sa.admin.module.scm.supplier.domain.entity.SupplierEntity;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.admin.module.system.employee.dao.EmployeeDao;
import net.lab1024.sa.admin.module.system.employee.domain.vo.EmployeeVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;
import static net.lab1024.sa.admin.module.scm.customer.constant.CustomerErrorCode.CUSTOMER_NOT_FOUND;

/**
 * 客户读路径。
 *
 * <p>列表补全（类型名 / 上级客户名 / 业务员名）一律走**批量查询**，绝不在循环里查库——
 * legacy 的 N+1 是最容易在客户量增长后暴露的性能问题（legacy 不变量 C17）。
 */
@Service
@RequiredArgsConstructor
public class CustomerQueryService {

    /**
     * 排序白名单（修正 legacy D18：只做 SQL 注入检查、不做白名单）。
     */
    private static final Set<String> SORTABLE = Set.of("customer_code", "name", "status", "updated_at");

    /**
     * 常购商品聚合窗口与行数上限（Wave 7 §11.3）：服务端裁剪，不接受越界的 days / limit。
     */
    private static final int FREQUENT_MIN_DAYS = 1;
    private static final int FREQUENT_MAX_DAYS = 365;
    private static final int FREQUENT_MIN_LIMIT = 1;
    private static final int FREQUENT_MAX_LIMIT = 100;
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final CustomerDao customers;
    private final CustomerSkuVisibilityService visibility;

    /**
     * 跨域只读：常购商品由订单事实（sales_order / sales_order_item）现算，不落副本。
     */
    private final CustomerFrequentSkuDao frequentSkus;

    private final CustomerTypeDao customerTypes;

    /**
     * 跨域只读：客户详情展示「绑定供应商」的名称。
     */
    private final SupplierDao suppliers;

    /**
     * SmartAdmin 原生员工读取，用于补全业务员姓名。
     */
    private final EmployeeDao employees;

    /**
     * SCM 数据范围解析入口：客户读路径唯一允许「能看哪些行」的判断来源。
     */
    private final ScmDataScopeService scopeService;

    public PageResult<CustomerVO> query(CustomerQueryForm form) {
        assertSortable(form);
        ScmDataScopeContext scope = scopeService.resolve();
        // 维度里一个授权 id 都没有 → 直接空分页，既不给数据库跑恒假谓词，也不会把空集合送进 IN ()。
        if (scope.getCustomerSellerScope().isEmpty()) {
            return ScmDataScopeService.emptyPage(form);
        }
        var page = SmartPageUtil.convert2PageQuery(form);
        if (page.orders().isEmpty()) {
            page.addOrder(OrderItem.desc("updated_at"), OrderItem.desc("id"));
        }
        List<CustomerEntity> rows = customers.queryPage(page, form, scope.getCustomerSellerScope());
        List<CustomerVO> list = new ArrayList<>(rows.size());
        rows.forEach(row -> list.add(toVO(row, context(rows))));
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /** 客户详情读（HTTP 入口）：按当前调用者的客户负责人范围判定，越权 30005。 */
    public CustomerDetailVO detail(Long customerId) {
        return detail(customerId, scopeService.resolve());
    }

    /**
     * 客户详情读 + 显式范围。
     *
     * <p>不存在与越权分得很清：不存在仍按 {@code CUSTOMER_NOT_FOUND}，存在但负责人不在范围内
     * 按 30005 拒绝。列表收窄不等于读不到，猜 id 直连详情必须是拒绝，否则整套行级范围只是隐藏。
     *
     * <p>上级集团客户名仍由 {@code context()} 单行取回，<b>不做也不校验集团展开</b>：
     * 集团统一结算不代表跨业务员互见（裁决第 6 条）。
     */
    public CustomerDetailVO detail(Long customerId, ScmDataScopeContext scope) {
        CustomerEntity entity = customers.selectById(customerId);
        if (entity == null) {
            throw new ScmBusinessException(CUSTOMER_NOT_FOUND);
        }
        if (!scope.getCustomerSellerScope().allows(entity.getSellerId())) {
            throw new ScmDataScopeException();
        }
        return detail(entity);
    }

    private CustomerDetailVO detail(CustomerEntity entity) {
        CustomerDetailVO vo = new CustomerDetailVO();
        BeanUtils.copyProperties(entity, vo);
        vo.setCustomerId(entity.getId());
        vo.setVisibilities(visibility.list(entity.getId()));

        List<CustomerEntity> rows = List.of(entity);
        EnrichmentContext context = context(rows);
        vo.setCustomerTypeName(context.typeNames().get(entity.getCustomerTypeId()));
        vo.setParentCustomerName(context.customerNames().get(entity.getParentCustomerId()));
        vo.setSellerName(context.employeeNames().get(entity.getSellerId()));
        vo.setSupplierName(context.supplierNames().get(entity.getSupplierId()));
        return vo;
    }

    /**
     * 客户「常购商品」（Wave 7 客户 360°，只读）：近 {@code days} 天已确认订单按 (SKU, 单位) 现算聚合，不落副本。
     *
     * <p>days / limit 一律服务端裁剪到安全区间；窗口按 <b>Asia/Shanghai 日界</b>对齐——「近 N 天含今天」
     * 下界取当天零点往前 {@code days-1} 天，避免按时分秒滚动窗口导致的边界抖动。客户不存在时与详情同样报 {@code CUSTOMER_NOT_FOUND}。
     *
     * <p>取数源是别人的成交价与用量，因此与详情同一套归属判定：读不到该客户就 30005。
     */
    public List<CustomerFrequentSkuVO> frequentSkus(Long customerId, int days, int limit) {
        CustomerEntity customer = customers.selectById(customerId);
        if (customer == null) {
            throw new ScmBusinessException(CUSTOMER_NOT_FOUND);
        }
        if (!scopeService.resolve().getCustomerSellerScope().allows(customer.getSellerId())) {
            throw new ScmDataScopeException();
        }
        int windowDays = Math.min(Math.max(days, FREQUENT_MIN_DAYS), FREQUENT_MAX_DAYS);
        int rowLimit = Math.min(Math.max(limit, FREQUENT_MIN_LIMIT), FREQUENT_MAX_LIMIT);
        ZonedDateTime startOfWindow = LocalDate.now(SHANGHAI)
                .minusDays(windowDays - 1L)
                .atStartOfDay(SHANGHAI);
        OffsetDateTime since = startOfWindow.toOffsetDateTime();
        return frequentSkus.frequentSkus(customerId, since, rowLimit);
    }

    /**
     * 客户下拉选项。
     *
     * <p>返回全部活动客户（按名称排序）。客户状态是四态业务状态而不是启用位，因此不像供应商那样
     * 只筛 {@code ENABLED}；调用方（例如「上级集团客户」选择器）按 {@code customerTypeCode} 过滤。
     *
     * <p><b>刻意不按数据范围收窄</b>：它是「选一个客户」的选择器入口（上级集团、订单录入等都用它），
     * 收窄会让主数据下拉在某些角色下整框落空。真正的读边界在列表与详情上，
     * 且「能否对该客户建单」在服务端另有归属判定。
     */
    public List<CustomerOptionVO> optionList() {
        List<CustomerEntity> rows = customers.selectList(new LambdaQueryWrapper<CustomerEntity>()
                .orderByAsc(CustomerEntity::getName, CustomerEntity::getId));

        // 一次批量取回类型编码，避免在循环里查库（C17）
        Map<Long, String> typeCodes = new HashMap<>();
        Set<Long> typeIds = collect(rows, CustomerEntity::getCustomerTypeId);
        if (!typeIds.isEmpty()) {
            customerTypes.selectList(new LambdaQueryWrapper<CustomerTypeEntity>()
                            .in(CustomerTypeEntity::getId, typeIds))
                    .forEach(t -> typeCodes.put(t.getId(), t.getTypeCode()));
        }

        List<CustomerOptionVO> list = new ArrayList<>(rows.size());
        for (CustomerEntity row : rows) {
            CustomerOptionVO vo = new CustomerOptionVO();
            vo.setCustomerId(row.getId());
            vo.setCustomerCode(row.getCustomerCode());
            vo.setName(row.getName());
            vo.setStatus(row.getStatus());
            vo.setCustomerTypeId(row.getCustomerTypeId());
            vo.setCustomerTypeCode(typeCodes.get(row.getCustomerTypeId()));
            list.add(vo);
        }
        return list;
    }

    private void assertSortable(CustomerQueryForm form) {
        if (form.getSortItemList() == null) {
            return;
        }
        boolean illegal = form.getSortItemList().stream()
                .anyMatch(item -> item.getColumn() == null || !SORTABLE.contains(item.getColumn().toLowerCase()));
        if (illegal) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
    }

    private CustomerVO toVO(CustomerEntity entity, EnrichmentContext context) {
        CustomerVO vo = new CustomerVO();
        BeanUtils.copyProperties(entity, vo);
        vo.setCustomerId(entity.getId());
        vo.setCustomerTypeName(context.typeNames().get(entity.getCustomerTypeId()));
        vo.setParentCustomerName(context.customerNames().get(entity.getParentCustomerId()));
        vo.setSellerName(context.employeeNames().get(entity.getSellerId()));
        return vo;
    }

    /**
     * 一次性把一批客户行需要的外部名称全部取回来。
     *
     * <p>四次批量查询封顶，与行数无关；空集合显式跳过，避免生成 `IN ()` 这种非法 SQL
     * （修正 legacy D15）。
     */
    private EnrichmentContext context(List<CustomerEntity> rows) {
        if (rows.isEmpty()) {
            return new EnrichmentContext(Map.of(), Map.of(), Map.of(), Map.of());
        }
        Set<Long> typeIds = collect(rows, CustomerEntity::getCustomerTypeId);
        Set<Long> parentIds = collect(rows, CustomerEntity::getParentCustomerId);
        Set<Long> sellerIds = collect(rows, CustomerEntity::getSellerId);
        Set<Long> supplierIds = collect(rows, CustomerEntity::getSupplierId);

        Map<Long, String> typeNames = new HashMap<>();
        if (!typeIds.isEmpty()) {
            customerTypes.selectList(new LambdaQueryWrapper<CustomerTypeEntity>()
                            .in(CustomerTypeEntity::getId, typeIds))
                    .forEach(t -> typeNames.put(t.getId(), t.getName()));
        }

        Map<Long, String> customerNames = new HashMap<>();
        if (!parentIds.isEmpty()) {
            customers.selectList(new LambdaQueryWrapper<CustomerEntity>()
                            .in(CustomerEntity::getId, parentIds))
                    .forEach(c -> customerNames.put(c.getId(), c.getName()));
        }

        Map<Long, String> employeeNames = new HashMap<>();
        if (!sellerIds.isEmpty()) {
            List<EmployeeVO> found = employees.getEmployeeByIds(sellerIds);
            if (found != null) {
                found.stream().filter(e -> e != null && e.getEmployeeId() != null)
                        .forEach(e -> employeeNames.put(e.getEmployeeId(), e.getActualName()));
            }
        }

        Map<Long, String> supplierNames = new HashMap<>();
        if (!supplierIds.isEmpty()) {
            suppliers.selectList(new LambdaQueryWrapper<SupplierEntity>()
                            .in(SupplierEntity::getId, supplierIds))
                    .forEach(s -> supplierNames.put(s.getId(), s.getName()));
        }

        return new EnrichmentContext(typeNames, customerNames, employeeNames, supplierNames);
    }

    private static Set<Long> collect(List<CustomerEntity> rows, Function<CustomerEntity, Long> getter) {
        Set<Long> ids = new LinkedHashSet<>();
        for (CustomerEntity row : rows) {
            Long id = getter.apply(row);
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    /**
     * 补全所需的四张名称表，一次构造、多次复用。
     */
    private record EnrichmentContext(Map<Long, String> typeNames,
                                     Map<Long, String> customerNames,
                                     Map<Long, String> employeeNames,
                                     Map<Long, String> supplierNames) {
    }
}
