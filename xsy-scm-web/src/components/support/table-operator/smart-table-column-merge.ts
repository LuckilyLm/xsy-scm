/*
 *  表格列设置
 *
 */

import _ from 'lodash';

// 只迁移能精确识别的历史系统默认值；其余用户手工列宽原样保留。
// 表格设置以前会把所有默认宽度一并保存，因此不能将每个历史 width 都视为用户主动调整。
const legacyDefaults: Record<number, Record<string, readonly number[]>> = {
    50001: {
        primaryImageUrl: [64, 68], name: [200, 220], categoryPath: [200, 210],
        saleUnit: [70, 72], price: [180, 184], skuCount: [76, 80],
        status: [84, 96], masterStatus: [88, 96], tags: [150], action: [120, 144],
    },
    50003: {name: [180, 220], customerCode: [120, 160]},
};

/** 将页面默认列和已保存的个人列设置合并、排序。 */
export function mergeColumn(originalTableColumnArray, userTableColumnArray, tableId?: number | string) {
    let saveFlag = false;
    if (!userTableColumnArray) {
        return originalTableColumnArray;
    }

    //第一步：将用户的列数据转为Map，以后备使用
    let userTableColumnMap = new Map();
    for (const item of userTableColumnArray) {
        userTableColumnMap.set(item.columnKey, item);
    }

    //第二步：以前端的table columns列为基础，将用户后端的数据填充到前端表格列里
    let fontColumnSort = 1;
    let newColumns = [];
    for (const fontColumn of originalTableColumnArray) {
        // 页面可用原始 showFlag=false 设置默认隐藏项；用户保存的显隐偏好优先。
        fontColumn.columnKey = fontColumn.dataIndex;
        fontColumn.showFlag = fontColumn.showFlag ?? true;
        fontColumn.sort = fontColumnSort;

        // 如果用户存在此列，则覆盖 sort和width、showFlag字段
        let userColumn = userTableColumnMap.get(fontColumn.columnKey);
        if (userColumn) {
            fontColumn.sort = userColumn.sort;
            fontColumn.showFlag = userColumn.showFlag;
            if (fontColumn.dragAndDropFlag) {
                saveFlag = true;
                delete fontColumn.dragAndDropFlag;
            } else {
                const knownDefaults = tableId == null ? [] : (legacyDefaults[Number(tableId)]?.[fontColumn.columnKey] ?? []);
                if (userColumn.width && !knownDefaults.includes(Number(userColumn.width))) {
                    fontColumn.width = userColumn.width;
                }
            }
        }
        newColumns.push(fontColumn);
        fontColumnSort++;
    }

    //第三步：前端列进行排序
    newColumns = _.sortBy(newColumns, (e) => e.sort);
    return {
        newColumns,
        saveFlag,
    };
}
