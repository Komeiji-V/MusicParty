import axios from 'axios';

const TOKEN_KEY = 'mp_token';

const client = axios.create({
    timeout: 10000
});

// 请求拦截器：附加 JWT 认证头
client.interceptors.request.use(config => {
    const token = localStorage.getItem(TOKEN_KEY);
    if (token) {
        config.headers = config.headers || {};
        config.headers['Authorization'] = `Bearer ${token}`;
    }
    return config;
}, error => Promise.reject(error));

// 响应拦截器：统一处理 401/403 和错误信息
client.interceptors.response.use(
    res => res.data,
    error => {
        const status = error.response?.status;
        const message = error.response?.data?.message || error.message || '请求失败';
        if (status === 401 && window.location.pathname !== '/login') {
            localStorage.removeItem(TOKEN_KEY);
            // 直接跳认证中心重新登录（带回跳地址），避免经过本站 /login 时被路由守卫互踢造成"点了没反应"
            fetch('/api/config').then(r => r.json()).then(cfg => {
                const authUrl = cfg.authCenterUrl || '';
                const redirect = encodeURIComponent(window.location.origin + window.location.pathname);
                if (authUrl) {
                    window.location.href = `${authUrl}/login?redirect=${redirect}`;
                } else {
                    window.location.href = '/login';
                }
            }).catch(() => {
                window.location.href = '/login';
            });
        }
        // 保留 response 引用，否则调用方 e.response?.data?.message 拿不到后端错误详情
        const wrapped = new Error(message);
        wrapped.response = error.response;
        return Promise.reject(wrapped);
    }
);

export default client;
