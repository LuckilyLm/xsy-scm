package com.xianshuyuan.scm.purchase.dto;
import jakarta.validation.constraints.*; import java.util.*;
public record PurchaseDemandGenerateRequest(@NotEmpty List<Long> salesOrderIds) {}
