package com.xsy.scm.notification.service;

import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.notification.dao.ScmNotificationEventDao;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.module.support.message.constant.MessageTypeEnum;
import net.lab1024.sa.base.module.support.message.domain.MessageSendForm;
import net.lab1024.sa.base.module.support.message.service.MessageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ScmNotificationService {

    /**
     * 无登录上下文（定时任务）写去重行时的操作者标记。
     *
     * <p>
     * 去重行的 {@code created_by} 只用于追溯来源，不参与任何权限判定，因此用常量标记比让
     * 整次扫描失败更合适；业务写路径仍必须经 {@link ScmOperator#current()} 拿到真实操作者。
     */
    public static final String SYSTEM_OPERATOR = "system";

    private final ScmNotificationEventDao eventDao;
    private final MessageService messageService;

    /**
     * 订单类事件的通知（保持既有调用点不变）。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean sendOnce(String eventKey, String eventType, Long receiverUserId, Long dataId, String title,
            String content) {
        return sendOnce(eventKey, eventType, MessageTypeEnum.ORDER.getValue(), receiverUserId, dataId, title, content);
    }

    /**
     * 按业务事件唯一键投递一条站内信；同一个键只会投递一次。
     *
     * <p>
     * <b>去重键必须按接收人区分</b>：一个业务事件可能有多个接收人（例如某仓的全部仓管），
     * 若 eventKey 只标识事件本身，唯一约束会把第二个及之后的接收人一起挡掉 —— 那会让
     * 「多人提醒」静默退化成「只提醒第一个人」。
     *
     * <p>
     * 去重行与站内信在同一事务内写入：回滚时不留通知，也不会出现「记了已发但没发」。
     *
     * @param messageType
     *            消息类型，决定前端「查看业务单据」的跳转目标
     * @return 本次是否真的投递；{@code false} 表示该键此前已投递过
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean sendOnce(String eventKey, String eventType, Integer messageType, Long receiverUserId, Long dataId,
            String title, String content) {
        if (receiverUserId == null || receiverUserId <= 0) {
            return false;
        }
        Integer userType = UserTypeEnum.ADMIN_EMPLOYEE.getValue();
        String operator = ScmOperator.currentOrNull();
        if (eventDao.insertIgnore(eventKey, eventType, userType, receiverUserId, dataId,
                operator == null ? SYSTEM_OPERATOR : operator) != 1) {
            return false;
        }
        MessageSendForm message = new MessageSendForm();
        message.setMessageType(messageType);
        message.setReceiverUserType(userType);
        message.setReceiverUserId(receiverUserId);
        message.setTitle(title);
        message.setContent(content);
        message.setDataId(dataId);
        messageService.sendMessage(message);
        return true;
    }
}
