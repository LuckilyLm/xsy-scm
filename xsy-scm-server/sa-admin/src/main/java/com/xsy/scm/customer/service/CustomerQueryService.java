package com.xsy.scm.customer.service;

import com.xsy.scm.common.scope.ScmDataScopeException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.customer.dao.CustomerDao;
import com.xsy.scm.customer.dao.CustomerFrequentSkuDao;
import com.xsy.scm.customer.dao.CustomerTypeDao;
import com.xsy.scm.customer.domain.entity.CustomerEntity;
import com.xsy.scm.customer.domain.entity.CustomerTypeEntity;
import com.xsy.scm.customer.domain.form.CustomerQueryForm;
import com.xsy.scm.customer.domain.vo.CustomerDetailVO;
import com.xsy.scm.customer.domain.vo.CustomerFrequentSkuVO;
import com.xsy.scm.customer.domain.vo.CustomerOptionVO;
import com.xsy.scm.customer.domain.vo.CustomerVO;
import com.xsy.scm.supplier.dao.SupplierDao;
import com.xsy.scm.supplier.domain.entity.SupplierEntity;
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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;
import static com.xsy.scm.customer.constant.CustomerErrorCode.CUSTOMER_NOT_FOUND;

/** 客户列表的类型、上级客户和业务员名称统一批量查询，避免逐条访问数据库。 */
@Service
@RequiredArgsConstructor
public class CustomerQueryService {

    /**
     * 排序白名单：客户端不能把任意字段名带入 SQL。
     */
    private static final Set<String> SORTABLE = Set.of("customer_code", "name", "status", "updated_at");

    /**
     * 常购商品聚合窗口与行数上限：服务端裁剪，不接受越界的 days / limit。
     */
    private static final int FREQUENT_MIN_DAYS = 1;
    private static final int FREQUENT_MAX_DAYS = 365;
    private static final int FREQUENT_MIN_LIMIT = 1;
    private static final int FREQUENT_MAX_LIMIT = 100;
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final CustomerDao customerDao;
    private final CustomerSkuVisibilityService customerSkuVisibilityService;

    /**
     * 跨域只读：常购商品由订单事实（sales_order / sales_order_item）现算，不落副本。
     */
    private final CustomerFrequentSkuDao customerFrequentSkuDao;

    private final CustomerTypeDao customerTypeDao;

    /**
     * 跨域只读：客户详情展示「绑定供应商」的名称。
     */
    private final SupplierDao supplierDao;

    /**
     * SmartAdmin 原生员工读取，用于补全业务员姓名。
     */
    private final EmployeeDao employeeDao;

    /**
     * SCM 数据范围解析入口：客户读路径唯一允许「能看哪些行」的判断来源。
     */
    private final ScmDataScopeService dataScopeService;

    public PageResult<CustomerVO> query(CustomerQueryForm form) {
        assertSortable(form);
        ScmDataScopeContext scope = dataScopeService.resolve();
        // 维度里一个授权 id 都没有 → 直接空分页，既不给数据库跑恒假谓词，也不会把空集合送进 IN ()。
        if (scope.getCustomerSellerScope().isEmpty()) {
            return ScmDataScopeService.emptyPage(form);
        }
        var page = SmartPageUtil.convert2PageQuery(form);
        if (page.orders().isEmpty()) {
            page.addOrder(OrderItem.desc("updated_at"), OrderItem.desc("id"));
        }
        List<CustomerEntity> rows = customerDao.queryPage(page, form, scope.getCustomerSellerScope());
        List<CustomerVO> list = new ArrayList<>(rows.size());
        EnrichmentContext context = context(rows);
        rows.forEach(row -> list.add(toVO(row, context)));
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /** 客户详情读（HTTP 入口）：按当前调用者的客户负责人范围判定，越权 30005。 */
    public CustomerDetailVO detail(Long customerId) {
        return detail(customerId, dataScopeService.resolve());
    }

    /** 按客户 ID 查询详情时也必须检查负责人数据范围；客户不存在与当前用户无权查看分别处理，不能通过直接传 ID 绕过列表权限。 */
    public CustomerDetailVO detail(Long customerId, ScmDataScopeContext scope) {
        CustomerEntity entity = customerDao.selectById(customerId);
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
        vo.setVisibilities(customerSkuVisibilityService.list(entity.getId()));

        List<CustomerEntity> rows = List.of(entity);
        EnrichmentContext context = context(rows);
        vo.setCustomerTypeName(context.typeNames().get(entity.getCustomerTypeId()));
        vo.setParentCustomerName(context.customerNames().get(entity.getParentCustomerId()));
        CustomerEntity settlement = customerDao.selectById(entity.getSettlementCustomerId());
        vo.setSettlementCustomerName(settlement == null ? entity.getName() : settlement.getName());
        vo.setSellerName(context.employeeNames().get(entity.getSellerId()));
        vo.setSupplierName(context.supplierNames().get(entity.getSupplierId()));
        return vo;
    }

    /**
     * 客户「常购商品」（客户 360°，只读）：近 {@code days} 天已确认订单按 (SKU, 单位) 现算聚合，不落副本。
     *
     * <p>
     * days / limit 一律服务端裁剪到安全区间；窗口按 <b>Asia/Shanghai 日界</b>对齐——「近 N 天含今天」下界取当天零点往前 {@code days-1}
     * 天，避免按时分秒滚动窗口导致的边界抖动。客户不存在时与详情同样报 {@code CUSTOMER_NOT_FOUND}。
     *
     * <p>
     * 取数源是别人的成交价与用量，因此与详情同一套归属判定：读不到该客户就 30005。
     */
    public List<CustomerFrequentSkuVO> frequentSkus(Long customerId, int days, int limit) {
        CustomerEntity customer = customerDao.selectById(customerId);
        if (customer == null) {
            throw new ScmBusinessException(CUSTOMER_NOT_FOUND);
        }
        if (!dataScopeService.resolve().getCustomerSellerScope().allows(customer.getSellerId())) {
            throw new ScmDataScopeException();
        }
        int windowDays = Math.min(Math.max(days, FREQUENT_MIN_DAYS), FREQUENT_MAX_DAYS);
        int rowLimit = Math.min(Math.max(limit, FREQUENT_MIN_LIMIT), FREQUENT_MAX_LIMIT);
        ZonedDateTime startOfWindow = LocalDate.now(SHANGHAI).minusDays(windowDays - 1L).atStartOfDay(SHANGHAI);
        OffsetDateTime since = startOfWindow.toOffsetDateTime();
        return customerFrequentSkuDao.frequentSkus(customerId, since, rowLimit);
    }

    /** 客户选择列表遵守当前客户数据范围，可按客户类型过滤。客户状态不等同供应商启停状态，不能只按 ENABLED 筛选。 */
    public List<CustomerOptionVO> optionList() {
        var customerScope = dataScopeService.resolve().getCustomerSellerScope();
        if (customerScope.isEmpty()) {
            return List.of();
        }
        List<CustomerEntity> rows = customerDao.selectOptions(customerScope);

        // 一次批量取回类型编码，避免在循环里查库。
        Map<Long, String> typeCodes = new HashMap<>();
        Set<Long> typeIds = collect(rows, CustomerEntity::getCustomerTypeId);
        if (!typeIds.isEmpty()) {
            customerTypeDao
                    .selectList(new LambdaQueryWrapper<CustomerTypeEntity>().in(CustomerTypeEntity::getId, typeIds))
                    .forEach(customerType -> typeCodes.put(customerType.getId(), customerType.getTypeCode()));
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
     * <p>
     * 四次批量查询封顶，与行数无关；空集合显式跳过，避免生成 {@code IN ()} 这种非法 SQL。
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
            customerTypeDao
                    .selectList(new LambdaQueryWrapper<CustomerTypeEntity>().in(CustomerTypeEntity::getId, typeIds))
                    .forEach(customerType -> typeNames.put(customerType.getId(), customerType.getName()));
        }

        Map<Long, String> customerNames = new HashMap<>();
        if (!parentIds.isEmpty()) {
            customerDao.selectList(new LambdaQueryWrapper<CustomerEntity>().in(CustomerEntity::getId, parentIds))
                    .forEach(parentCustomer -> customerNames.put(parentCustomer.getId(), parentCustomer.getName()));
        }

        Map<Long, String> employeeNames = new HashMap<>();
        if (!sellerIds.isEmpty()) {
            List<EmployeeVO> found = employeeDao.getEmployeeByIds(sellerIds);
            if (found != null) {
                found.stream().filter(employee -> employee != null && employee.getEmployeeId() != null)
                        .forEach(employee -> employeeNames.put(employee.getEmployeeId(), employee.getActualName()));
            }
        }

        Map<Long, String> supplierNames = new HashMap<>();
        if (!supplierIds.isEmpty()) {
            supplierDao.selectList(new LambdaQueryWrapper<SupplierEntity>().in(SupplierEntity::getId, supplierIds))
                    .forEach(supplier -> supplierNames.put(supplier.getId(), supplier.getName()));
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
    private record EnrichmentContext(Map<Long, String> typeNames, Map<Long, String> customerNames,
            Map<Long, String> employeeNames, Map<Long, String> supplierNames) {
    }
}
