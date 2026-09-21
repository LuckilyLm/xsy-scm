package net.lab1024.sa.admin.module.scm.inventory.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventoryReservationStatusEnum;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryReservationDao;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryReservationQueryForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryReservationVO;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 库存预留查询侧（只读）。
 *
 * <p>状态中文描述在服务层按枚举填充，避免前端硬编码状态字典。
 */
@Service
@RequiredArgsConstructor
public class InventoryReservationQueryService {

    private final InventoryReservationDao reservationDao;

    /**
     * 分页查询。
     */
    public PageResult<InventoryReservationVO> queryPage(InventoryReservationQueryForm query) {
        var page = SmartPageUtil.convert2PageQuery(query);
        List<InventoryReservationVO> list = reservationDao.queryPage(page, query);
        list.forEach(InventoryReservationQueryService::fillStatusDesc);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    private static void fillStatusDesc(InventoryReservationVO vo) {
        for (ScmInventoryReservationStatusEnum item : ScmInventoryReservationStatusEnum.values()) {
            if (item.name().equals(vo.getStatus())) {
                vo.setStatusDesc(item.getDesc());
                return;
            }
        }
    }
}
