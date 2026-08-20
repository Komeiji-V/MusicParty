import dayjs from 'dayjs';

export const formatDuration = (ms) => {
    if (!ms || ms < 0) return "00:00";
    // 纯数值计算，避免 dayjs(ms) 的 mm 上限 59（≥60 分钟显示错误）及时区依赖
    const totalSeconds = Math.floor(ms / 1000);
    const m = Math.floor(totalSeconds / 60);
    const s = totalSeconds % 60;
    return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
};

export const formatTimeNow = () => {
    return dayjs().format('HH:mm:ss');
};