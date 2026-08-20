import client from './client';

/**
 * 管理员后台专用接口封装
 * 注意：新后端基于 JWT 角色鉴权（SUPER_ADMIN），不再使用独立的管理员密码
 * 频道设置/数据清理等操作已统一走 AdminPage.vue（/admin），此处仅保留仍被引用的接口
 */
export const adminApi = {
    // 验证当前登录用户是否具备超级管理员权限 (非 SUPER_ADMIN 将收到 403)
    verify: () => client.get('/api/admin/users')
};
