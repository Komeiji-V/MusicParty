import client from './client';

export const authApi = {
    // 管理员指令
    // 统一管理员指令接口
    adminCommand: (password, command) => client.post('/api/admin/command', { password, command }),
};