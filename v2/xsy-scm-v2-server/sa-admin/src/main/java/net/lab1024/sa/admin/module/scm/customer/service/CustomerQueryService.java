package net.lab1024.sa.admin.module.scm.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.customer.dao.CustomerDao;
import net.lab1024.sa.admin.module.scm.customer.dao.CustomerTypeDao;
import net.lab1024.sa.admin.module.scm.customer.domain.entity.CustomerEntity;
import net.lab1024.sa.admin.module.scm.customer.domain.entity.CustomerTypeEntity;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerQueryForm;
import net.lab1024.sa.admin.module.scm.customer.domain.vo.CustomerDetailVO;
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

    /** 排序白名单（修正 legacy D18：只做 SQL 注入检查、不做白名单）。 */
    private static final Set<String> SORTABLE = Set.of("customer_code", "name", "status", "updated_at");

    private final CustomerDao customers;
    private final CustomerSkuVisibilityService visibility;

    private final CustomerTypeDao customerTypes;

    /** 跨域只读：客户详情展示「绑定供应商」的名称。 */
    private final SupplierDao suppliers;

    /** SmartAdmin 原生员工读取，用于补全业务员姓名。 */
    private final EmployeeDao employees;

    public PageResult<CustomerVO> query(CustomerQueryForm form) {
        assertSortable(form);
        var page = SmartPageUtil.convert2PageQuery(form);
        if (page.orders().isEmpty()) {
            page.addOrder(OrderItem.desc("updated_at"), OrderItem.desc("id"));
        }
        List<CustomerEntity> rows = customers.queryPage(page, form);
        List<CustomerVO> list = new ArrayList<>(rows.size());
        rows.forEach(row -> list.add(toVO(row, context(rows))));
        return SmartPageUtil.convert2PageResult(page, list);
    }

    public CustomerDetailVO detail(Long customerId) {
        CustomerEntity entity = customers.selectById(customerId);
        if (entity == null) {
            throw new ScmBusinessException(CUSTOMER_NOT_FOUND);
        }
        CustomerDetailVO vo = new CustomerDetailVO();
        BeanUtils.copyProperties(entity, vo);
        vo.setCustomerId(entity.getId());
        vo.setVisibilities(visibility.list(customerId));

        List<CustomerEntity> rows = List.of(entity);
        EnrichmentContext context = context(rows);
        vo.setCustomerTypeName(context.typeNames().get(entity.getCustomerTypeId()));
        vo.setParentCustomerName(context.customerNames().get(entity.getParentCustomerId()));
        vo.setSellerName(context.employeeNames().get(entity.getSellerId()));
        vo.setSupplierName(context.supplierNames().get(entity.getSupplierId()));
        return vo;
    }

    /**
     * 客户下拉选项。
     *
     * <p>返回全部活动客户（按名称排序）。客户状态是四态业务状态而不是启用位，因此不像供应商那样
     * 只筛 {@code ENABLED}；调用方（例如「上级集团客户」选择器）按 {@code customerTypeCode} 过滤。
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

    /** 补全所需的四张名称表，一次构造、多次复用。 */
    private record EnrichmentContext(Map<Long, String> typeNames,
                                     Map<Long, String> customerNames,
                                     Map<Long, String> employeeNames,
                                     Map<Long, String> supplierNames) {
    }
}
