/**
 * 称号标签文字颜色自适应：按背景色亮度自动选深色/白色文字。
 * 亮底（黄、浅绿等）用深色字，暗底（紫、蓝、深红等）用白字，无需描边即可保证可读。
 */
export function titleTextColor(hex) {
  const c = (hex || '#ff5722').replace('#', '')
  if (!/^[0-9a-fA-F]{6}$/.test(c)) return '#ffffff'
  const r = parseInt(c.slice(0, 2), 16)
  const g = parseInt(c.slice(2, 4), 16)
  const b = parseInt(c.slice(4, 6), 16)
  const luma = 0.299 * r + 0.587 * g + 0.114 * b
  return luma > 150 ? '#111827' : '#ffffff'
}
