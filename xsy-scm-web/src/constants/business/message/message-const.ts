/*
 * @Description: file content
 * @LastEditors:
 * @LastEditTime: 2022-07-24 21:43:43
 */
export const MESSAGE_TYPE_ENUM = {
    MAIL: {
        value: 1,
        desc: '站内信'
    },
    ORDER: {
        value: 2,
        desc: '订单'
    },
    // 后端 net.lab1024.sa.base.module.support.message.constant.MessageTypeEnum 的数值镜像
    INVENTORY_LOSS_GAIN: {
        value: 3,
        desc: '库存报损报溢'
    },
    // 与 message-business-link.ts 的跳转分支同值；两侧一致性由消息跳转单测核对
    INVENTORY_WARNING: {
        value: 4,
        desc: '库存预警'
    },
};


export const MESSAGE_RECEIVE_TYPE_ENUM = {
    EMPLOYEE: {
        value: 1,
        desc: '员工'
    },
};

export default {
    MESSAGE_TYPE_ENUM,
    MESSAGE_RECEIVE_TYPE_ENUM
};
