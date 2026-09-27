package com.xsy.scm.delivery.constant;

/** Order-level fulfillment states matching the delivery_route_order database check. */
public enum ScmDeliveryFulfillmentStatusEnum {
    PENDING,
    IN_TRANSIT,
    SIGNED,
    EXCEPTION
}
