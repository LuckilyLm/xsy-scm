/* 页面水印 */

import dayjs from 'dayjs';

/** 水印 DOM id */
const WATER_MARK_DOM_ID = 'smart_admin_water_mark';
let smartAdminWaterMarkIntervalId: NodeJS.Timer | null = null;

function setWatermark(id, str) {
  // 删掉之前的水印
  if (document.getElementById(WATER_MARK_DOM_ID) !== null) {
    document.getElementById(WATER_MARK_DOM_ID).remove();
  }

  str = str + ' ' + dayjs().format('YYYY-MM-DD HH:mm');

  const can = document.createElement('canvas');
  can.width = 400;
  can.height = 200;

  const cans = can.getContext('2d');
  cans.rotate((-15 * Math.PI) / 150);
  cans.font = '16px Microsoft JhengHei';
  cans.fillStyle = 'rgba(190, 190, 190, 0.30)';
  cans.textAlign = 'left';
  cans.textBaseline = 'middle';
  cans.fillText(str, can.width / 8, can.height / 2);
  const div = document.createElement('div');
  div.id = WATER_MARK_DOM_ID;
  div.style.pointerEvents = 'none';
  div.style.top = '0px';
  div.style.left = '0px';
  div.style.position = 'absolute';
  div.style.zIndex = '99';
  div.style.width = '100%';
  div.style.height = '100%';
  div.style.background = 'url(' + can.toDataURL('image/png') + ') left top repeat';

  // 大屏等独立路由没有水印容器，静默返回
  const container = document.getElementById(id);
  if (container === null) {
    return;
  }
  container.appendChild(div);
}

const watermark = {
  show: function () {
    document.getElementById(WATER_MARK_DOM_ID).style.display = 'block';
  },
  hide: function () {
    document.getElementById(WATER_MARK_DOM_ID).style.display = 'hide';
  },
  // 只允许调用一次
  set: function (id, str) {
    if (document.getElementById(WATER_MARK_DOM_ID) !== null) {
      return;
    }

    setWatermark(id, str);

    // 每分钟重绘一次
    smartAdminWaterMarkIntervalId = setInterval(() => {
      setWatermark(id, str);
    }, 60000);

    window.onresize = () => {
      setWatermark(id, str);
    };
  },
  // 清空水印
  clear: function () {
    let watermarkDom = document.getElementById(WATER_MARK_DOM_ID);
    if (watermarkDom) {
      watermarkDom.remove();
    }

    window.removeEventListener('resize', setWatermark);
    if (smartAdminWaterMarkIntervalId) {
      clearInterval(smartAdminWaterMarkIntervalId);
      smartAdminWaterMarkIntervalId = null;
    }
  },
};
export default watermark;
