package com.xianshuyuan.scm.order.service;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xianshuyuan.scm.common.api.PageData;
import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.order.converter.SalesOrderConverter;
import com.xianshuyuan.scm.order.dto.SalesOrderPageQuery;
import com.xianshuyuan.scm.order.entity.*;
import com.xianshuyuan.scm.order.mapper.*;
import com.xianshuyuan.scm.order.vo.*;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;
@Service public class SalesOrderQueryService {
 private final SalesOrderMapper orders; private final SalesOrderItemMapper items; private final OrderOperationLogMapper logs;
 public SalesOrderQueryService(SalesOrderMapper o,SalesOrderItemMapper i,OrderOperationLogMapper l){orders=o;items=i;logs=l;}
 public PageData<SalesOrderResponse> page(SalesOrderPageQuery q){var result=orders.selectOrderPage(new Page<>(q.page(),q.pageSize()),q);var ids=result.getRecords().stream().map(SalesOrderEntity::getId).toList();Map<Long,List<SalesOrderItemEntity>> grouped=ids.isEmpty()?Map.of():items.selectActiveByOrderIds(ids).stream().collect(Collectors.groupingBy(SalesOrderItemEntity::getOrderId));return new PageData<>(result.getRecords().stream().map(o->SalesOrderConverter.toResponse(o,grouped.getOrDefault(o.getId(),List.of()))).toList(),q.page(),q.pageSize(),result.getTotal());}
 public SalesOrderResponse get(long id){var o=orders.selectActiveById(id);if(o==null)throw new BusinessException(OrderErrorCodes.ORDER_NOT_FOUND);return SalesOrderConverter.toResponse(o,items.selectActiveByOrderId(id));}
 public List<OrderOperationLogResponse> logs(long id){if(orders.selectActiveById(id)==null)throw new BusinessException(OrderErrorCodes.ORDER_NOT_FOUND);return logs.selectByOrderId(id).stream().map(x->new OrderOperationLogResponse(x.getId(),x.getOperationType(),x.getOperator(),x.getReason(),x.getBeforeData(),x.getAfterData(),x.getCreatedAt())).toList();}
}
