// 歌单导入工具：链接/ID 解析 + 分页拉全（歌单页与频道搜索弹窗共用）
import client from '../api/client';

// 链接/ID 解析（支持完整链接与裸 ID；QQ 字母 mid 不支持）
export function parsePlaylistId(text, platform) {
  const t = (text || '').trim();
  if (platform === 'netease') {
    const m = t.match(/(?:playlist\?id=|playlist\/)(\d+)/);
    return m ? m[1] : (/^\d+$/.test(t) ? t : null);
  }
  if (platform === 'qq') {
    const m = t.match(/playlist\/([A-Za-z0-9]+)/);
    if (m) return /^\d+$/.test(m[1]) ? m[1] : null;
    return /^\d+$/.test(t) ? t : null;
  }
  if (platform === 'kugou') {
    const m = t.match(/special\/single\/(\d+)/);
    return m ? m[1] : (/^\d+$/.test(t) ? t : null);
  }
  return null;
}

// 循环分页拉全歌单歌曲（网易云 1000/页，QQ 50/页，酷狗 100/页上限）
export async function fetchAllSongs(platform, id) {
  const songs = [];
  const pageSize = platform === 'netease' ? 1000 : platform === 'qq' ? 50 : 100;
  let offset = 0;
  while (true) {
    const batch = await client.get(`/api/playlist/songs/${platform}/${id}?offset=${offset}&limit=${pageSize}`);
    if (!Array.isArray(batch) || batch.length === 0) break;
    songs.push(...batch);
    if (batch.length < pageSize) break;
    offset += pageSize;
    if (songs.length >= 5000) break; // 安全上限
  }
  return songs;
}
