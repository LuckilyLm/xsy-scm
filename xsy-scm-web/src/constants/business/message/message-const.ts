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
