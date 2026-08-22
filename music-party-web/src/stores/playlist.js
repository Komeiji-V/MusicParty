// src/stores/playlist.js
import { defineStore } from 'pinia';
import { ref } from 'vue';
import client from '../api/client';

const buildMusicPayload = (music) => ({
    id: music.id,
    name: music.name || '',
    artists: Array.isArray(music.artists) ? music.artists : (music.artists ? [music.artists] : []),
    duration: music.duration || 0,
    coverUrl: music.coverUrl || '',
    platform: music.platform || '',
    album: music.album || ''
});

export const usePlaylistStore = defineStore('playlist', () => {
    const playlists = ref([]);
    const categories = ref([]);
    const currentPlaylist = ref(null);
    const items = ref([]);
    const loading = ref(false);

    const fetchPlaylists = async () => {
        loading.value = true;
        try {
            playlists.value = await client.get('/api/user/playlists');
        } catch (e) {
            console.error('Failed to fetch playlists', e);
            playlists.value = [];
            throw e;
        } finally {
            loading.value = false;
        }
    };

    const fetchCategories = async () => {
        try {
            categories.value = await client.get('/api/user/playlists/categories');
        } catch (e) {
            console.error('Failed to fetch categories', e);
        }
    };

    const createPlaylist = async (name, category, isPublic = false) => {
        const pl = await client.post('/api/user/playlists', {
            name, category: category || '', coverUrl: null, isPublic: !!isPublic
        });
        playlists.value.unshift(pl);
        if (category) fetchCategories();
        return pl;
    };

    const updatePlaylist = async (id, data) => {
        const pl = await client.put(`/api/user/playlists/${id}`, data);
        const idx = playlists.value.findIndex(p => p.id === id);
        if (idx >= 0) playlists.value[idx] = pl;
        if (currentPlaylist.value && currentPlaylist.value.id === id) {
            currentPlaylist.value = pl;
        }
        if (data.category !== undefined) fetchCategories();
        return pl;
    };

    const deletePlaylist = async (id) => {
        await client.delete(`/api/user/playlists/${id}`);
        playlists.value = playlists.value.filter(p => p.id !== id);
        if (currentPlaylist.value && currentPlaylist.value.id === id) {
            currentPlaylist.value = null;
            items.value = [];
        }
        fetchCategories();
    };

    const fetchItems = async (playlistId) => {
        loading.value = true;
        try {
            items.value = await client.get(`/api/user/playlists/${playlistId}/items`);
        } catch (e) {
            console.error('Failed to fetch playlist items', e);
            items.value = [];
            throw e;
        } finally {
            loading.value = false;
        }
    };

    const addItem = async (playlistId, music) => {
        return client.post(`/api/user/playlists/${playlistId}/items`, { music: buildMusicPayload(music) });
    };

    const importSongs = async (playlistId, songs) => {
        return client.post(`/api/user/playlists/${playlistId}/import`, { songs: songs.map(buildMusicPayload) });
    };

    const removeItem = async (playlistId, itemId) => {
        await client.delete(`/api/user/playlists/${playlistId}/items/${itemId}`);
    };

    // 自定义排序：歌单列表 / 歌单内歌曲（传有序 id 数组，后端重写 sort_order / position）
    const reorderPlaylists = async (orderedIds) => {
        await client.put('/api/user/playlists/order', orderedIds);
        await fetchPlaylists();
    };
    const exportPlaylist = async (id, format = 'json') => {
        const token = localStorage.getItem('mp_token');
        const res = await fetch(`/api/user/playlists/${id}/export?format=${format}`, {
            headers: token ? { Authorization: `Bearer ${token}` } : {}
        });
        if (!res.ok) {
            let msg = '导出失败';
            try {
                const data = await res.json();
                msg = data.message || msg;
            } catch (e) { /* ignore */ }
            throw new Error(msg);
        }
        const disposition = res.headers.get('Content-Disposition') || '';
        const match = disposition.match(/filename\*=UTF-8''([^;]+)/i) || disposition.match(/filename="?([^";]+)"?/i);
        let filename = `playlist.${format}`;
        if (match) filename = decodeURIComponent(match[1]);
        const blob = await res.blob();
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = filename;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.URL.revokeObjectURL(url);
    };

    return {
        playlists, categories, currentPlaylist, items, loading,
        fetchPlaylists, fetchCategories, createPlaylist, updatePlaylist, deletePlaylist,
        fetchItems, addItem, importSongs, removeItem, reorderPlaylists, exportPlaylist
    };
});
