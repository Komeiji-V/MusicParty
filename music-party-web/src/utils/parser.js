const TIME_EXP = /\[(\d{2,}):(\d{2})(?:\.(\d{2,3}))?\]/;

export const parseLyrics = (lrcString) => {
    if (!lrcString) return [];
    const lines = lrcString.split('\n');
    const result = [];

    for (let line of lines) {
        const match = TIME_EXP.exec(line);
        if (match) {
            const min = parseInt(match[1]);
            const sec = parseInt(match[2]);
            const ms = match[3] ? parseInt(match[3].padEnd(3, '0')) : 0;
            const time = min * 60 * 1000 + sec * 1000 + ms;
            const text = line.replace(TIME_EXP, '').trim();
            if (text) {
                result.push({ time, text });
            }
        }
    }
    return result;
};

/** 解析单行 LRC（取第一个时间戳），返回 { time, text } 或 null */
const parseLine = (line) => {
    const match = TIME_EXP.exec(line);
    if (!match) return null;
    const min = parseInt(match[1]);
    const sec = parseInt(match[2]);
    const ms = match[3] ? parseInt(match[3].padEnd(3, '0')) : 0;
    const text = line.replace(TIME_EXP, '').trim();
    if (!text) return null;
    return { time: min * 60 * 1000 + sec * 1000 + ms, text };
};

/** LRC 文本 → 时间戳映射（同时间多条取第一条） */
const toTimeMap = (raw) => {
    const map = new Map();
    if (!raw) return map;
    for (const line of raw.split('\n')) {
        const p = parseLine(line);
        if (p && !map.has(p.time)) map.set(p.time, p.text);
    }
    return map;
};

/** 找到最接近且不晚于 time 的翻译/罗马音行（且早于下一主行） */
const nearestBefore = (map, time, nextTime) => {
    let best = null;
    let bestDiff = Infinity;
    for (const [t, txt] of map) {
        if (nextTime != null && t >= nextTime) continue;
        const diff = time - t;
        if (diff >= 0 && diff < bestDiff) { best = txt; bestDiff = diff; }
    }
    return best;
};

/**
 * 结构化歌词解析：原文 + 翻译 + 罗马音，按时间对齐合并。
 * @returns [{ time, text, trans, roman }]
 */
export const parseLyricsFull = (lrc, tlyric, romalrc) => {
    const main = (lrc || '').split('\n').map(parseLine).filter(Boolean);
    if (main.length === 0) return [];

    const transMap = toTimeMap(tlyric);
    const romanMap = toTimeMap(romalrc);

    return main.map((line, i) => {
        const nextTime = i + 1 < main.length ? main[i + 1].time : null;
        return {
            time: line.time,
            text: line.text,
            trans: transMap.size ? nearestBefore(transMap, line.time, nextTime) : null,
            roman: romanMap.size ? nearestBefore(romanMap, line.time, nextTime) : null
        };
    });
};
