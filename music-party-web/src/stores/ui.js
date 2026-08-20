// src/stores/ui.js
import { defineStore } from 'pinia';
import { ref, watch } from 'vue';
import { STORAGE_KEYS } from '../constants/keys';
import client from '../api/client';

export const useUiStore = defineStore('ui', () => {
    const isLiteMode = ref(false);
    const volume = ref(parseFloat(localStorage.getItem(STORAGE_KEYS.VOLUME) || '0.5'));
    const autoLiteMode = ref(localStorage.getItem('mp_auto_lite_mode') !== 'false'); // 默认 true
    const keepAliveEnabled = ref(localStorage.getItem(STORAGE_KEYS.KEEP_ALIVE) === 'true');

    const siteTitle = ref('MUSIC PARTY');
    const authorName = ref('ThorNex');
    const backWords = ref('THORNEX');
    const hasInfoPage = ref(false);
    const infoPageContent = ref('');
    const showInfo = ref(false);
    // 首页 ABOUT 介绍文字（可编辑；空 = 前端默认文案）
    const aboutText = ref('');

    const toggleLiteMode = () => {
        isLiteMode.value = !isLiteMode.value;
    };

    const setVolume = (val) => {
        volume.value = Math.max(0, Math.min(1, val));
    };

    const toggleKeepAlive = () => {
        keepAliveEnabled.value = !keepAliveEnabled.value;
        localStorage.setItem(STORAGE_KEYS.KEEP_ALIVE, keepAliveEnabled.value.toString());
    };

    const fetchConfig = async () => {
        try {
            const config = await client.get('/api/config');
            siteTitle.value = config.siteTitle || 'MUSIC PARTY';
            authorName.value = config.authorName || 'ThorNex';
            backWords.value = config.backWords || 'THORNEX';
            hasInfoPage.value = config.hasInfoPage || false;
            aboutText.value = config.aboutText || '';
        } catch (e) {
            console.error('Failed to fetch config', e);
        }
    };

    const fetchInfoPage = async () => {
        try {
            const data = await client.get('/api/config/info');
            infoPageContent.value = data.content || '';
        } catch (e) {
            console.error('Failed to fetch info page', e);
        }
    };

    // 监听音量变化并持久化
    watch(volume, (newVal) => {
        localStorage.setItem(STORAGE_KEYS.VOLUME, newVal.toString());
    });

    watch(autoLiteMode, (newVal) => {
        localStorage.setItem('mp_auto_lite_mode', newVal.toString());
    });

    return {
        isLiteMode,
        toggleLiteMode,
        volume,
        setVolume,
        autoLiteMode,
        keepAliveEnabled,
        toggleKeepAlive,
        siteTitle,
        authorName,
        backWords,
        hasInfoPage,
        infoPageContent,
        showInfo,
        aboutText,
        fetchConfig,
        fetchInfoPage
    };
});
