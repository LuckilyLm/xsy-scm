package net.lab1024.sa.admin.module.scm.delivery.service;

import org.springframework.stereotype.Component;

import java.util.List;

import net.lab1024.sa.admin.module.scm.order.domain.entity.SalesOrderEntity;

/**
 * Single extension point: add sorting readiness here when that domain is available.
 */
@Component
public class DeliveryEligibilityPolicy {
    public List<String> candidateStatuses() {
        return List.of("CONFIRMED");
    }

    public boolean eligible(SalesOrderEntity order) {
        return order != null && !Boolean.TRUE.equals(order.getDeleted()) && candidateStatuses().contains(order.getStatus());
    }
}
