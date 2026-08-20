package org.thornex.musicparty.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thornex.musicparty.config.AppProperties;
import org.thornex.musicparty.dto.Music;
import org.thornex.musicparty.dto.MusicQueueItem;
import org.thornex.musicparty.dto.UserSummary;
import org.thornex.musicparty.enums.PlayMode;
import org.thornex.musicparty.enums.Priority;
import org.thornex.musicparty.enums.QueueItemStatus;
import org.thornex.musicparty.enums.TopResult;
import org.thornex.musicparty.repository.MusicQueueItemRepository;
import org.thornex.musicparty.repository.PlayHistoryRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class MusicQueueManager {

    private static final Long DEFAULT_CHANNEL_ID = 1L;

    private final AppProperties appProperties;
    private final MusicQueueItemRepository musicQueueItemRepository;
    private final PlayHistoryRepository playHistoryRepository;
    private final ObjectMapper objectMapper;

    private final Map<Long, Deque<MusicQueueItem>> queues = new ConcurrentHashMap<>();
    private final Map<Long, List<Music>> playHistories = new ConcurrentHashMap<>();
    private final Map<Long, AtomicReference<String>> lastPlayedUserTokens = new ConcurrentHashMap<>();
    private final Map<Long, Long> nextPositions = new ConcurrentHashMap<>();

    private Deque<MusicQueueItem> getQueue(Long channelId) {
        Long cid = channelId != null ? channelId : DEFAULT_CHANNEL_ID;
        return queues.computeIfAbsent(cid, k -> new ConcurrentLinkedDeque<>());
    }

    private List<Music> getHistory(Long channelId) {
        Long cid = channelId != null ? channelId : DEFAULT_CHANNEL_ID;
        return playHistories.computeIfAbsent(cid, k -> Collections.synchronizedList(new LinkedList<>()));
    }

    private AtomicReference<String> getLastPlayedToken(Long channelId) {
        Long cid = channelId != null ? channelId : DEFAULT_CHANNEL_ID;
        return lastPlayedUserTokens.computeIfAbsent(cid, k -> new AtomicReference<>(""));
    }

    @PostConstruct
    public void restoreFromDatabase() {
        try {
            List<org.thornex.musicparty.entity.MusicQueueItem> queueRows = musicQueueItemRepository.findAll();
            List<org.thornex.musicparty.entity.PlayHistory> historyRows = playHistoryRepository.findAll();

            Map<Long, List<org.thornex.musicparty.entity.MusicQueueItem>> queueByChannel = queueRows.stream()
                    .collect(Collectors.groupingBy(org.thornex.musicparty.entity.MusicQueueItem::getChannelId));
            Map<Long, List<org.thornex.musicparty.entity.PlayHistory>> historyByChannel = historyRows.stream()
                    .collect(Collectors.groupingBy(org.thornex.musicparty.entity.PlayHistory::getChannelId));

            Set<Long> channels = new HashSet<>(queueByChannel.keySet());
            channels.addAll(historyByChannel.keySet());
            int historySize = appProperties.getMusicApi().getQueue().getHistorySize();

            for (Long channelId : channels) {
                List<MusicQueueItem> items = new ArrayList<>();
                long maxPos = -1;
                List<org.thornex.musicparty.entity.MusicQueueItem> channelQueue =
                        queueByChannel.getOrDefault(channelId, Collections.emptyList());
                channelQueue.sort(Comparator.comparing(
                        e -> e.getPosition() == null ? Integer.MAX_VALUE : e.getPosition()));
                for (org.thornex.musicparty.entity.MusicQueueItem row : channelQueue) {
                    MusicQueueItem item = toDto(row);
                    if (item != null) {
                        items.add(item);
                        maxPos = Math.max(maxPos, row.getPosition() == null ? -1 : row.getPosition());
                    }
                }

                List<Music> history = new ArrayList<>();
                List<org.thornex.musicparty.entity.PlayHistory> channelHistory =
                        historyByChannel.getOrDefault(channelId, Collections.emptyList());
                channelHistory.sort(Comparator.comparing(org.thornex.musicparty.entity.PlayHistory::getPlayedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())));
                int from = Math.max(0, channelHistory.size() - historySize);
                for (int i = channelHistory.size() - 1; i >= from; i--) {
                    try {
                        history.add(objectMapper.readValue(channelHistory.get(i).getSongData(), Music.class));
                    } catch (Exception e) {
                        log.error("Failed to deserialize play history for channel {}", channelId, e);
                    }
                }

                restore(items, history, channelId);
                nextPositions.put(channelId, maxPos);
                log.info("Restored {} queue items and {} play history items for channel {}",
                        items.size(), history.size(), channelId);
            }
        } catch (Exception e) {
            log.error("Failed to restore queue and history from database", e);
        }
    }

    private MusicQueueItem toDto(org.thornex.musicparty.entity.MusicQueueItem entity) {
        try {
            Music music = objectMapper.readValue(entity.getSongData(), Music.class);
            UserSummary user = new UserSummary(
                    entity.getUserToken() != null ? entity.getUserToken() : "SYSTEM",
                    "",
                    entity.getEnqueuerName() != null ? entity.getEnqueuerName() : "SYSTEM",
                    Boolean.TRUE.equals(entity.getEnqueuerGuest()));
            return new MusicQueueItem(
                    String.valueOf(entity.getId()),
                    music,
                    user,
                    parseStatus(entity.getStatus()),
                    parsePriority(entity.getPriority()));
        } catch (Exception e) {
            log.error("Failed to deserialize queue item {}", entity.getId(), e);
            return null;
        }
    }

    private org.thornex.musicparty.entity.MusicQueueItem toEntity(MusicQueueItem item, Long channelId) {
        return org.thornex.musicparty.entity.MusicQueueItem.builder()
                .channelId(channelId)
                .userToken(item.enqueuedBy() != null ? item.enqueuedBy().token() : null)
                .enqueuerName(item.enqueuedBy() != null ? item.enqueuedBy().name() : null)
                .enqueuerGuest(item.enqueuedBy() != null && item.enqueuedBy().isGuest())
                .songData(writeJson(item.music()))
                .musicId(item.music() != null ? item.music().id() : null)
                .platform(item.music() != null ? item.music().platform() : null)
                .priority(item.priority() != null ? item.priority().name() : Priority.REGULAR.name())
                .status(item.status() != null ? item.status().name() : QueueItemStatus.PENDING.name())
                .build();
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("Failed to serialize object", e);
            return "{}";
        }
    }

    private QueueItemStatus parseStatus(String value) {
        try {
            return value != null ? QueueItemStatus.valueOf(value) : QueueItemStatus.PENDING;
        } catch (IllegalArgumentException e) {
            return QueueItemStatus.PENDING;
        }
    }

    private Priority parsePriority(String value) {
        try {
            return value != null ? Priority.valueOf(value) : Priority.REGULAR;
        } catch (IllegalArgumentException e) {
            return Priority.REGULAR;
        }
    }

    private long allocatePosition(Long channelId) {
        return nextPositions.compute(channelId, (k, v) -> v == null ? 0L : v + 1);
    }

    private void deleteQueueItem(MusicQueueItem item, Long channelId) {
        try {
            musicQueueItemRepository.deleteById(Long.valueOf(item.queueId()));
        } catch (Exception e) {
            log.error("Failed to delete queue item {} from database (channel {})", item.queueId(), channelId, e);
        }
    }

    private void syncQueueToDb(Long channelId) {
        Long cid = channelId != null ? channelId : DEFAULT_CHANNEL_ID;
        Deque<MusicQueueItem> queue = getQueue(cid);
        int index = 0;
        for (MusicQueueItem item : queue) {
            try {
                org.thornex.musicparty.entity.MusicQueueItem entity = toEntity(item, cid);
                entity.setId(Long.valueOf(item.queueId()));
                entity.setPosition(index);
                musicQueueItemRepository.save(entity);
            } catch (Exception e) {
                log.error("Failed to sync queue item {} to database (channel {})", item.queueId(), cid, e);
            }
            index++;
        }
    }

    public synchronized MusicQueueItem add(Music music, UserSummary enqueuedBy, QueueItemStatus initialStatus, Long channelId) {
        Long cid = channelId != null ? channelId : DEFAULT_CHANNEL_ID;
        Deque<MusicQueueItem> queue = getQueue(cid);
        if (queue.size() >= appProperties.getMusicApi().getQueue().getMaxSize()) {
            return null;
        }
        if (isMusicInQueue(queue, music.id())) {
            return null;
        }
        org.thornex.musicparty.entity.MusicQueueItem entity =
                org.thornex.musicparty.entity.MusicQueueItem.builder()
                        .channelId(cid)
                        .userToken(enqueuedBy != null ? enqueuedBy.token() : null)
                        .enqueuerName(enqueuedBy != null ? enqueuedBy.name() : null)
                        .enqueuerGuest(enqueuedBy != null && enqueuedBy.isGuest())
                        .songData(writeJson(music))
                        .musicId(music.id())
                        .platform(music.platform())
                        .priority(Priority.REGULAR.name())
                        .status(initialStatus != null ? initialStatus.name() : QueueItemStatus.PENDING.name())
                        .position((int) allocatePosition(cid))
                        .build();
        entity = musicQueueItemRepository.save(entity);
        MusicQueueItem newItem = new MusicQueueItem(
                String.valueOf(entity.getId()),
                music,
                enqueuedBy,
                initialStatus,
                Priority.REGULAR
        );
        queue.addLast(newItem);
        return newItem;
    }

    public MusicQueueItem add(Music music, UserSummary enqueuedBy, QueueItemStatus initialStatus) {
        return add(music, enqueuedBy, initialStatus, DEFAULT_CHANNEL_ID);
    }

    public synchronized TopResult top(String queueId, PlayMode playMode, Long channelId) {
        Long cid = channelId != null ? channelId : DEFAULT_CHANNEL_ID;
        Deque<MusicQueueItem> queue = getQueue(cid);
        Optional<MusicQueueItem> itemOpt = findByQueueId(queue, queueId);
        if (itemOpt.isEmpty()) {
            return TopResult.NONE;
        }
        MusicQueueItem item = itemOpt.get();
        if (item.priority() == Priority.GLOBAL_TOP) {
            return TopResult.NONE;
        }
        if (item.priority() == Priority.USER_TOP) {
            if (queue.remove(item)) {
                queue.addFirst(item.withPriority(Priority.GLOBAL_TOP));
                syncQueueToDb(cid);
                nextPositions.put(cid, (long) queue.size());
                return TopResult.GLOBAL;
            }
            return TopResult.NONE;
        }
        if (playMode == PlayMode.SHUFFLE) {
            List<MusicQueueItem> snapshot = new ArrayList<>(queue);
            int index = snapshot.indexOf(item);
            if (index != -1) {
                snapshot.set(index, item.withPriority(Priority.USER_TOP));
                queue.clear();
                queue.addAll(snapshot);
                syncQueueToDb(cid);
                nextPositions.put(cid, (long) queue.size());
                return TopResult.PERSONAL;
            }
        } else {
            if (queue.remove(item)) {
                queue.addFirst(item.withPriority(Priority.GLOBAL_TOP));
                syncQueueToDb(cid);
                nextPositions.put(cid, (long) queue.size());
                return TopResult.GLOBAL;
            }
        }
        return TopResult.NONE;
    }

    public TopResult top(String queueId, PlayMode playMode) {
        return top(queueId, playMode, DEFAULT_CHANNEL_ID);
    }

    public synchronized int removeByUser(String userToken, Long channelId) {
        Deque<MusicQueueItem> queue = getQueue(channelId);
        List<MusicQueueItem> toRemove = queue.stream()
                .filter(item -> item.enqueuedBy().token().equals(userToken))
                .toList();
        toRemove.forEach(queue::remove);
        toRemove.forEach(item -> deleteQueueItem(item, channelId));
        return toRemove.size();
    }

    public int removeByUser(String userToken) {
        return removeByUser(userToken, DEFAULT_CHANNEL_ID);
    }

    public synchronized Optional<MusicQueueItem> getItem(String queueId, Long channelId) {
        return findByQueueId(getQueue(channelId), queueId);
    }

    public Optional<MusicQueueItem> getItem(String queueId) {
        return getItem(queueId, DEFAULT_CHANNEL_ID);
    }

    public synchronized Optional<MusicQueueItem> remove(String queueId, Long channelId) {
        Deque<MusicQueueItem> queue = getQueue(channelId);
        Optional<MusicQueueItem> itemOpt = findByQueueId(queue, queueId);
        itemOpt.ifPresent(item -> {
            queue.remove(item);
            deleteQueueItem(item, channelId);
        });
        return itemOpt;
    }

    public Optional<MusicQueueItem> remove(String queueId) {
        return remove(queueId, DEFAULT_CHANNEL_ID);
    }

    public synchronized MusicQueueItem pollNext(PlayMode playMode, boolean isFairShuffle, boolean allowOfflineShuffle,
                                                 Map<String, QueueItemStatus> statusMap, Set<String> onlineUserTokens,
                                                 Long channelId) {
        Deque<MusicQueueItem> queue = getQueue(channelId);
        List<Music> history = getHistory(channelId);

        if (queue.isEmpty()) {
            return pollFromHistory(history);
        }

        List<MusicQueueItem> candidates = new ArrayList<>(queue);

        Optional<MusicQueueItem> topItem = candidates.stream()
                .filter(item -> item.priority() == Priority.GLOBAL_TOP && isReadyOrFailed(statusMap, item))
                .findFirst();

        if (topItem.isPresent()) {
            queue.remove(topItem.get());
            deleteQueueItem(topItem.get(), channelId);
            return topItem.get();
        }

        List<MusicQueueItem> availableItems = candidates.stream()
                .filter(item -> item.priority() != Priority.GLOBAL_TOP && isReadyOrFailed(statusMap, item))
                .toList();

        if (availableItems.isEmpty()) {
            return null;
        }

        MusicQueueItem chosenItem;
        if (playMode == PlayMode.SHUFFLE) {
            if (isFairShuffle) {
                chosenItem = pollNextFairShuffle(availableItems, onlineUserTokens, allowOfflineShuffle, channelId);
            } else {
                chosenItem = pollNextTotalShuffle(availableItems, onlineUserTokens, allowOfflineShuffle);
            }
        } else {
            chosenItem = availableItems.get(0);
        }

        if (chosenItem == null) {
            return null;
        }

        queue.remove(chosenItem);
        deleteQueueItem(chosenItem, channelId);
        getLastPlayedToken(channelId).set(chosenItem.enqueuedBy().token());
        return chosenItem;
    }

    public MusicQueueItem pollNext(PlayMode playMode, boolean isFairShuffle, boolean allowOfflineShuffle,
                                    Map<String, QueueItemStatus> statusMap, Set<String> onlineUserTokens) {
        return pollNext(playMode, isFairShuffle, allowOfflineShuffle, statusMap, onlineUserTokens, DEFAULT_CHANNEL_ID);
    }

    private MusicQueueItem pollNextTotalShuffle(List<MusicQueueItem> availableItems, Set<String> onlineUserTokens, boolean allowOffline) {
        List<MusicQueueItem> pool;
        if (allowOffline) {
            pool = availableItems;
        } else {
            pool = availableItems.stream()
                    .filter(item -> onlineUserTokens.contains(item.enqueuedBy().token()))
                    .toList();
        }
        if (pool.isEmpty()) {
            return null;
        }
        Optional<MusicQueueItem> userTop = pool.stream()
                .filter(i -> i.priority() == Priority.USER_TOP)
                .findAny();
        if (userTop.isPresent()) {
            return userTop.get();
        }
        return pool.get(new Random().nextInt(pool.size()));
    }

    private MusicQueueItem pollNextFairShuffle(List<MusicQueueItem> availableItems, Set<String> onlineUserTokens,
                                                boolean allowOffline, Long channelId) {
        Map<String, List<MusicQueueItem>> userSongsMap = new HashMap<>();
        for (MusicQueueItem item : availableItems) {
            userSongsMap.computeIfAbsent(item.enqueuedBy().token(), k -> new ArrayList<>()).add(item);
        }
        List<String> allUserTokens = new ArrayList<>(userSongsMap.keySet());
        List<String> targetUserTokens;
        if (allowOffline) {
            targetUserTokens = allUserTokens;
        } else {
            targetUserTokens = allUserTokens.stream()
                    .filter(onlineUserTokens::contains)
                    .collect(Collectors.toList());
        }
        if (targetUserTokens.isEmpty()) {
            return null;
        }
        Collections.sort(targetUserTokens);
        String lastToken = getLastPlayedToken(channelId).get();
        int nextIndex = 0;
        if (targetUserTokens.contains(lastToken)) {
            int currentIndex = targetUserTokens.indexOf(lastToken);
            nextIndex = (currentIndex + 1) % targetUserTokens.size();
        } else {
            nextIndex = 0;
            for (int i = 0; i < targetUserTokens.size(); i++) {
                if (targetUserTokens.get(i).compareTo(lastToken) > 0) {
                    nextIndex = i;
                    break;
                }
            }
        }
        String selectedUserToken = targetUserTokens.get(nextIndex);
        List<MusicQueueItem> userSongs = userSongsMap.get(selectedUserToken);
        Optional<MusicQueueItem> userTop = userSongs.stream()
                .filter(i -> i.priority() == Priority.USER_TOP)
                .findFirst();
        if (userTop.isPresent()) {
            return userTop.get();
        }
        Collections.shuffle(userSongs);
        return userSongs.get(0);
    }

    private Optional<MusicQueueItem> findByQueueId(Deque<MusicQueueItem> queue, String queueId) {
        return queue.stream()
                .filter(item -> item.queueId().equals(queueId))
                .findFirst();
    }

    public void addToHistory(Music music, Long channelId) {
        if (music == null) return;
        List<Music> history = getHistory(channelId);
        synchronized (history) {
            history.removeIf(m -> m.id().equals(music.id()) && m.platform().equals(music.platform()));
            history.add(0, music);
            if (history.size() > appProperties.getMusicApi().getQueue().getHistorySize()) {
                history.removeLast();
            }
        }
        try {
            playHistoryRepository.save(org.thornex.musicparty.entity.PlayHistory.builder()
                    .channelId(channelId != null ? channelId : DEFAULT_CHANNEL_ID)
                    .songData(writeJson(music))
                    .build());
        } catch (Exception e) {
            log.error("Failed to persist play history for channel {}", channelId, e);
        }
    }

    public void addToHistory(Music music) {
        addToHistory(music, DEFAULT_CHANNEL_ID);
    }

    public synchronized void clearAll(Long channelId) {
        Long cid = channelId != null ? channelId : DEFAULT_CHANNEL_ID;
        getQueue(cid).clear();
        List<Music> history = getHistory(cid);
        history.clear();
        getLastPlayedToken(cid).set("");
        try {
            musicQueueItemRepository.deleteByChannelId(cid);
            playHistoryRepository.deleteByChannelId(cid);
        } catch (Exception e) {
            log.error("Failed to clear queue and history for channel {} from database", cid, e);
        }
        nextPositions.put(cid, -1L);
    }

    public void clearAll() {
        clearAll(DEFAULT_CHANNEL_ID);
    }

    public synchronized void clearPendingQueue(Long channelId) {
        Long cid = channelId != null ? channelId : DEFAULT_CHANNEL_ID;
        getQueue(cid).clear();
        try {
            musicQueueItemRepository.deleteByChannelId(cid);
        } catch (Exception e) {
            log.error("Failed to clear queue for channel {} from database", cid, e);
        }
        nextPositions.put(cid, -1L);
    }

    public void clearPendingQueue() {
        clearPendingQueue(DEFAULT_CHANNEL_ID);
    }

    public List<MusicQueueItem> getQueueSnapshot(Long channelId) {
        return new ArrayList<>(getQueue(channelId));
    }

    public List<MusicQueueItem> getQueueSnapshot() {
        return getQueueSnapshot(DEFAULT_CHANNEL_ID);
    }

    public List<Music> getHistorySnapshot(Long channelId) {
        List<Music> history = getHistory(channelId);
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }

    public List<Music> getHistorySnapshot() {
        return getHistorySnapshot(DEFAULT_CHANNEL_ID);
    }

    public synchronized void restore(List<MusicQueueItem> loadedQueue, List<Music> loadedHistory, Long channelId) {
        Deque<MusicQueueItem> queue = getQueue(channelId);
        queue.clear();
        List<Music> history = getHistory(channelId);
        history.clear();
        getLastPlayedToken(channelId).set("");
        if (loadedQueue != null) {
            queue.addAll(loadedQueue);
        }
        if (loadedHistory != null) {
            history.addAll(loadedHistory);
        }
    }

    public void restore(List<MusicQueueItem> loadedQueue, List<Music> loadedHistory) {
        restore(loadedQueue, loadedHistory, DEFAULT_CHANNEL_ID);
    }

    private boolean isMusicInQueue(Deque<MusicQueueItem> queue, String musicId) {
        return queue.stream().anyMatch(item -> item.music().id().equals(musicId));
    }

    private boolean isReadyOrFailed(Map<String, QueueItemStatus> statusMap, MusicQueueItem item) {
        QueueItemStatus status = statusMap.getOrDefault(item.music().id(), QueueItemStatus.PENDING);
        return status == QueueItemStatus.READY || status == QueueItemStatus.FAILED;
    }

    private MusicQueueItem pollFromHistory(List<Music> history) {
        synchronized (history) {
            if (history.isEmpty()) {
                return null;
            }
            Music randomSong = history.get(new Random().nextInt(history.size()));
            UserSummary systemUser = new UserSummary("SYSTEM", "SYSTEM", "AutoDJ", false);
            return new MusicQueueItem(
                    UUID.randomUUID().toString(),
                    randomSong,
                    systemUser,
                    QueueItemStatus.PENDING
            );
        }
    }
}
