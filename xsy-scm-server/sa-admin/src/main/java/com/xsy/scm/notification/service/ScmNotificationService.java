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
    private final ScmNotificationEventDao eventDao;
    private final MessageService messageService;

    @Transactional(rollbackFor = Exception.class)
    public boolean sendOnce(String eventKey, String eventType, Long receiverUserId, Long dataId, String title,
            String content) {
        if (receiverUserId == null || receiverUserId <= 0) {
            return false;
        }
        Integer userType = UserTypeEnum.ADMIN_EMPLOYEE.getValue();
        if (eventDao.insertIgnore(eventKey, eventType, userType, receiverUserId, dataId, ScmOperator.current()) != 1) {
            return false;
        }
        MessageSendForm message = new MessageSendForm();
        message.setMessageType(MessageTypeEnum.ORDER.getValue());
        message.setReceiverUserType(userType);
        message.setReceiverUserId(receiverUserId);
        message.setTitle(title);
        message.setContent(content);
        message.setDataId(dataId);
        messageService.sendMessage(message);
        return true;
    }
}
