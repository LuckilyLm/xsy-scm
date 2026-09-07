package com.xianshuyuan.scm.inventory.controller;

import com.xianshuyuan.scm.common.api.ApiResponse;
import com.xianshuyuan.scm.inventory.entity.*;
import com.xianshuyuan.scm.inventory.mapper.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class InventoryController {
 private final InventoryMapper inventories; private final InventoryMovementMapper movements;
 public InventoryController(InventoryMapper inventories,InventoryMovementMapper movements){this.inventories=inventories;this.movements=movements;}
 @GetMapping("/inventories") public ApiResponse<List<InventoryEntity>> inventories(@RequestParam(required=false) Long warehouseId,@RequestParam(required=false) Long skuId){return ApiResponse.success(inventories.selectActiveList(warehouseId,skuId));}
 @GetMapping("/inventory-movements") public ApiResponse<List<InventoryMovementEntity>> movements(@RequestParam(required=false) Long warehouseId,@RequestParam(required=false) Long skuId,@RequestParam(required=false) Long receiptId){return ApiResponse.success(movements.selectActiveList(warehouseId,skuId,receiptId));}
}
