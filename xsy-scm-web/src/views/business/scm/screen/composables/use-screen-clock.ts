import {computed, onBeforeUnmount, onMounted, ref} from 'vue';

const WEEKDAYS = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'];

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

/** 大屏时钟：每秒刷新时间，不触发数据请求。 */
export function useScreenClock() {
  const now = ref(new Date());
  let timer: number | undefined;

  const dateText = computed(() => {
    const d = now.value;
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  });

  const weekdayText = computed(() => WEEKDAYS[now.value.getDay()]);

  const timeText = computed(() => {
    const d = now.value;
    return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
  });

  onMounted(() => {
    timer = window.setInterval(() => {
      now.value = new Date();
    }, 1000);
  });

  onBeforeUnmount(() => {
    if (timer !== undefined) {
      window.clearInterval(timer);
      timer = undefined;
    }
  });

  return {now, dateText, weekdayText, timeText};
}
