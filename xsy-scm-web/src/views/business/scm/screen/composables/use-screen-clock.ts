import {computed, onBeforeUnmount, onMounted, ref} from 'vue';

const WEEKDAYS = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'];

function pad(value: number): string {
    return String(value).padStart(2, '0');
}

/**
 * 大屏时钟（日期 + 星期 + 时分秒）。
 *
 * <p>秒级刷新只影响这一个 1 秒定时器，不触发任何数据请求 —— 时间在走不代表数据要重拉，
 * 否则大屏每分钟都在打接口，而绝大多数时刻数据根本没变。
 */
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

/** 把时间戳格式化成「HH:mm」，用于「数据更新时间」。 */
export function formatClock(value: Date | null): string {
    if (!value) {
        return '--:--';
    }
    return `${pad(value.getHours())}:${pad(value.getMinutes())}`;
}
