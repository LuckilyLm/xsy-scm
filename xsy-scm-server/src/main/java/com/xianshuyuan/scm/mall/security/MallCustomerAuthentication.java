package com.xianshuyuan.scm.mall.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

public class MallCustomerAuthentication extends AbstractAuthenticationToken {

    public static final String MALL_CUSTOMER_AUTHORITY = "ROLE_MALL_CUSTOMER";

    private final MallCustomerPrincipal customer;

    public MallCustomerAuthentication(MallCustomerPrincipal customer) {
        super(List.of(new SimpleGrantedAuthority(MALL_CUSTOMER_AUTHORITY)));
        this.customer = customer;
        setAuthenticated(true);
    }

    public MallCustomerPrincipal customer() {
        return customer;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return customer;
    }
}
