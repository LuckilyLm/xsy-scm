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

    /** 定时任务没有登录用户时使用系统操作人标记，避免通知记录被错误归属到普通员工。 */
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
     * 同一事件对同一接收人只发一次站内信；不同接收人各自接收。 去重记录与通知在同一事务内保存，失败时一起回滚。
     *
     * @param messageType
     *            消息类型，决定业务单据的跳转目标
     * @return true 表示本次发出；false 表示之前已经发送
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
