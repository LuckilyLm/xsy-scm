package net.lab1024.sa.admin.module.scm.warehouse;

import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.common.handler.ScmExceptionHandler;
import net.lab1024.sa.admin.module.scm.warehouse.constant.WarehouseErrorCode;
import net.lab1024.sa.admin.module.scm.warehouse.controller.WarehouseController;
import net.lab1024.sa.admin.module.scm.warehouse.domain.vo.WarehouseVO;
import net.lab1024.sa.admin.module.scm.warehouse.service.WarehouseQueryService;
import net.lab1024.sa.admin.module.scm.warehouse.service.WarehouseService;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.SystemEnvironment;
import net.lab1024.sa.base.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 仓库端点契约测试（W5 Target Design §7.1 的 5 个端点）。
 *
 * <p>锁定三件事：响应信封形状、业务码到 HTTP 200 + {@code ResponseDTO.code} 的映射、
 * MVC 层校验失败仍是 SmartAdmin 的 30001（SCM 不重复接管）。
 */
@WebMvcTest(WarehouseController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = {WarehouseController.class, ScmExceptionHandler.class, GlobalExceptionHandler.class})
class WarehouseWebTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private WarehouseService service;

    @MockitoBean
    private WarehouseQueryService queryService;

    @MockitoBean(name = "systemEnvironment")
    private SystemEnvironment environment;

    @Test
    @DisplayName("GET /list 返回数组信封（下拉选择器）")
    void returnsListEnvelope() throws Exception {
        WarehouseVO vo = new WarehouseVO();
        vo.setId(1L);
        vo.setWarehouseCode("WH001");
        vo.setName("默认仓库");
        vo.setStatus("ENABLED");
        when(queryService.list()).thenReturn(List.of(vo));

        mvc.perform(get("/scm/warehouse/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].warehouseCode").value("WH001"));
    }

    @Test
    @DisplayName("POST /query 返回分页信封")
    void returnsPageEnvelope() throws Exception {
        PageResult<WarehouseVO> page = new PageResult<>();
        page.setPageNum(1L);
        page.setPageSize(20L);
        page.setTotal(0L);
        page.setPages(0L);
        page.setList(List.of());
        page.setEmptyFlag(true);
        when(queryService.query(any())).thenReturn(page);

        mvc.perform(post("/scm/warehouse/query").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageNum\":1,\"pageSize\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list").isArray());
    }

    @Test
    @DisplayName("GET /detail/{id} 返回单个对象")
    void returnsDetail() throws Exception {
        WarehouseVO vo = new WarehouseVO();
        vo.setId(9L);
        vo.setWarehouseCode("WH009");
        when(queryService.detail(9L)).thenReturn(vo);

        mvc.perform(get("/scm/warehouse/detail/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(9));
    }

    @Test
    @DisplayName("POST /create 返回新仓库 id")
    void createsWarehouse() throws Exception {
        when(service.create(any())).thenReturn(42L);

        mvc.perform(post("/scm/warehouse/create").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warehouseCode\":\"WH002\",\"name\":\"二号仓\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(42));
    }

    @Test
    @DisplayName("POST /update 成功返回空信封")
    void updatesWarehouse() throws Exception {
        mvc.perform(post("/scm/warehouse/update").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":1,\"version\":0,\"warehouseCode\":\"WH001\",\"name\":\"默认仓库\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("仓库不存在 → 40485（HTTP 200 + 业务码）")
    void mapsNotFound() throws Exception {
        doThrow(new ScmBusinessException(WarehouseErrorCode.WAREHOUSE_NOT_FOUND)).when(service).update(any());

        mvc.perform(post("/scm/warehouse/update").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":1,\"version\":0,\"warehouseCode\":\"WH001\",\"name\":\"默认仓库\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40485));
    }

    @Test
    @DisplayName("编码重复 → 40996")
    void mapsDuplicateCode() throws Exception {
        doThrow(new ScmBusinessException(WarehouseErrorCode.WAREHOUSE_CODE_DUPLICATE)).when(service).create(any());

        mvc.perform(post("/scm/warehouse/create").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warehouseCode\":\"WH001\",\"name\":\"默认仓库\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40996));
    }

    @Test
    @DisplayName("乐观锁冲突 → 40921")
    void mapsVersionConflict() throws Exception {
        doThrow(new ScmBusinessException(VERSION_CONFLICT)).when(service).update(any());

        mvc.perform(post("/scm/warehouse/update").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":1,\"version\":7,\"warehouseCode\":\"WH001\",\"name\":\"默认仓库\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40921));
    }

    @Test
    @DisplayName("MVC 校验失败仍是 30001：编码空白 / 名称缺失")
    void validatesForm() throws Exception {
        mvc.perform(post("/scm/warehouse/create").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warehouseCode\":\"   \",\"name\":\"二号仓\"}"))
                .andExpect(jsonPath("$.code").value(30001));

        mvc.perform(post("/scm/warehouse/create").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warehouseCode\":\"WH002\"}"))
                .andExpect(jsonPath("$.code").value(30001));
    }

    @Test
    @DisplayName("查询条件状态取值域受限：非法 status → 30001")
    void validatesQueryStatus() throws Exception {
        mvc.perform(post("/scm/warehouse/query").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageNum\":1,\"pageSize\":20,\"status\":\"BROKEN\"}"))
                .andExpect(jsonPath("$.code").value(30001));
    }
}
