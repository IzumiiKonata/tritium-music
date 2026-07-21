package tritium.music.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import lombok.Cleanup;
import lombok.Getter;
import lombok.SneakyThrows;
import tritium.music.client.screens.ncm.NCMScreen;
import tritium.music.core.audio.AudioPlayer;
import tritium.music.core.lyric.LyricLine;
import tritium.music.core.lyric.LyricParser;
import tritium.music.core.model.Music;
import tritium.music.core.model.PlayList;
import tritium.music.core.model.Quality;
import tritium.music.core.model.User;
import tritium.music.core.ncm.OptionsUtil;
import tritium.music.core.ncm.QRCodeGenerator;
import tritium.music.core.ncm.api.CloudMusicApi;
import tritium.music.core.util.AsyncUtil;
import tritium.music.core.util.GaussianKernel;
import tritium.music.core.util.HttpUtils;
import tritium.music.core.util.JsonUtils;
import tritium.music.core.util.Pair;
import tritium.music.core.util.StringUtil;
import tritium.music.core.util.Textures;
import tritium.music.core.util.Timer;
import tritium.music.core.util.WrappedInputStream;
import tritium.music.platform.Platform;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author IzumiiKonata
 * @since 6/16/2023 9:34 AM
 */
public class CloudMusic {

    @Getter
    private static final Map<String, String> headers = new HashMap<>();
    public static AudioPlayer player;
    public static List<Music> playList = new ArrayList<>();
    public static int curIdx = 0;
    public static Music currentlyPlaying;
    public static Thread playThread;

    public static User profile;
    public static List<PlayList> playLists;
    public static List<Long> likeList;

    public static PlayMode playMode = PlayMode.Sequential;

    public static Quality quality = Quality.STANDARD;

    public static final List<LyricLine> lyrics = new CopyOnWriteArrayList<>();
    public static LyricLine currentLyric = null;
    public static LyricLine currentLyricNoEarlyJump = null;
    public static boolean hasTransLyrics = false;
    public static boolean hasRomanization = false;
    public static boolean haveNoWords = false;

    private static final List<MusicListener> listeners = new CopyOnWriteArrayList<>();

    public static File cookieFile() {
        return new File(Platform.configDir(), "NCMCookie.txt");
    }

    public static void addListener(MusicListener listener) {
        listeners.add(listener);
    }

    public static void removeListener(MusicListener listener) {
        listeners.remove(listener);
    }

    public static void initLyrics(JsonObject rawLyricData, Music music, List<LyricLine> parsedLyrics) {
        resetLyricFlags();
        detectTranslations(rawLyricData);

        synchronized (lyrics) {
            updateLyricsList(parsedLyrics);
            currentLyric = lyrics.getFirst();
            haveNoWords = lyricsHaveNoWords();
            addLongBreaks();
        }

        for (MusicListener listener : listeners) {
            listener.onLyricsLoaded(music);
        }
    }

    private static void resetLyricFlags() {
        hasTransLyrics = false;
        hasRomanization = false;
    }

    private static void updateLyricsList(List<LyricLine> parsedLyrics) {
        lyrics.clear();
        lyrics.addAll(parsedLyrics);

        if (lyrics.isEmpty()) {
            lyrics.add(new LyricLine(0L, "暂无歌词"));
        }
    }

    private static void detectTranslations(JsonObject lyric) {
        if (hasLyricsType(lyric, "tlyric") || hasLyricsType(lyric, "ytlrc")) hasTransLyrics = true;
        if (hasLyricsType(lyric, "romalrc") || hasLyricsType(lyric, "yromalrc")) hasRomanization = true;
    }

    private static boolean hasLyricsType(JsonObject lyric, String type) {
        if (lyric.has(type) && lyric.get(type).isJsonObject()) {
            JsonObject lyricTypeObj = lyric.get(type).getAsJsonObject();
            return lyricTypeObj.has("lyric") && !lyricTypeObj.get("lyric").getAsString().isEmpty();
        }
        return false;
    }

    /**
     * 为歌词添加长间隔时的 "● ● ●"
     */
    private static void addLongBreaks() {
        final long longBreaksDuration = 3000L;

        if (haveNoWords) {
            addInitialBreakIfNeeded(longBreaksDuration);
            return;
        }

        addBreaksBetweenLyrics(longBreaksDuration);
    }

    /**
     * 歌词是否不为逐字歌词
     *
     * @return true 表示不为逐字歌词
     */
    private static boolean lyricsHaveNoWords() {
        return lyrics.stream().allMatch(l -> l.words.isEmpty());
    }

    private static void addInitialBreakIfNeeded(long duration) {
        long firstTimestamp = lyrics.getFirst().getTimestamp();
        if (firstTimestamp >= duration) {
            addBreakLine(0L, firstTimestamp);
        }
    }

    private static void addBreaksBetweenLyrics(long duration) {
        long lastTimestamp = 0L;
        List<LyricLine> breaksToAdd = new ArrayList<>();

        for (LyricLine line : lyrics) {
            long lineDuration = line.duration;
            long gap = line.getTimestamp() - lastTimestamp;

            if (gap >= duration) {
                breaksToAdd.add(createBreakLine(lastTimestamp, gap));
            }

            lastTimestamp = line.getTimestamp() + lineDuration;
        }

        addAndSortBreaks(breaksToAdd);
    }

    private static LyricLine createBreakLine(long timestamp, long duration) {
        LyricLine line = new LyricLine(timestamp, "● ● ●");
        line.isBreakLine = true;
        line.words.add(new LyricLine.Word("● ● ●", timestamp, duration));
        return line;
    }

    private static void addBreakLine(long timestamp, long duration) {
        lyrics.add(createBreakLine(timestamp, duration));
        lyrics.sort(Comparator.comparingLong(LyricLine::getTimestamp));
    }

    private static void addAndSortBreaks(List<LyricLine> breaks) {
        lyrics.addAll(breaks);
        lyrics.sort(Comparator.comparingLong(LyricLine::getTimestamp));
    }

    /**
     * 更新当前歌词行
     *
     * @param songProgress 歌曲进度 (ms)
     */
    public static void updateCurrentLyric(float songProgress) {
        LyricLine previousLyric = currentLyric;
        currentLyric = findCurrentLyric(songProgress);
        currentLyricNoEarlyJump = findCurrentLyric(songProgress, false);

        if (previousLyric != currentLyric) {
            resetLyricPositionUpdate();
            for (MusicListener listener : listeners) {
                listener.onCurrentLyricChanged();
            }
        }
    }

    static final float JUMP_TO_NEXT_MILLIS = 300.0f;

    static boolean canJumpToNextEarly(double songProgress, LyricLine lyric) {
        if (lyric == null || lyric.words.isEmpty())
            return false;

        return lyric.duration >= JUMP_TO_NEXT_MILLIS;
    }

    public static LyricLine findCurrentLyric(double songProgress) {
        return findCurrentLyric(songProgress, true);
    }

    public static LyricLine findCurrentLyric(double songProgress, boolean allowEarlyJump) {
        for (int i = 0; i < lyrics.size(); i++) {
            LyricLine lyric = lyrics.get(i);
            LyricLine prev = i > 0 ? lyrics.get(i - 1) : null;

            if (allowEarlyJump
                    && !haveNoWords
                    && !lyric.isBreakLine
                    && lyric.getTimestamp() > songProgress
                    && lyric.getTimestamp() - songProgress <= JUMP_TO_NEXT_MILLIS
                    && canJumpToNextEarly(songProgress, prev)) {
                return lyric;
            }

            if (lyric.getTimestamp() > songProgress) {
                return i > 0 ? lyrics.get(i - 1) : currentLyric;
            }

            if (i == lyrics.size() - 1) {
                return lyric;
            }
        }
        return currentLyric;
    }

    public static void resetLyricPositionUpdate() {
        lyrics.forEach(l -> {
            l.shouldUpdatePosition = false;
            l.delayTimer.reset();
        });
    }

    public static void resetLyricStatus() {
        lyrics.forEach(l -> {
            l.shouldUpdatePosition = false;
            l.delayTimer.reset();

            for (LyricLine.Word word : l.words) {
                Arrays.fill(word.emphasizes, 0);
            }

            l.markDirty();
        });
    }

    public static void setLyricsProgress(float progress) {
        if (lyrics.isEmpty()) return;

        try {
            resetLyricDisplayStates();
            updateCurrentLyric(progress);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void resetLyricDisplayStates() {
        resetAllLyricsState();
        resetWordStates();
    }

    private static void resetAllLyricsState() {
        for (LyricLine lyric : lyrics) {
            lyric.scrollWidth = 0;
            lyric.offsetX = 0;
            lyric.offsetY = Double.MIN_VALUE;
            lyric.targetOffsetX = 0;
        }
    }

    private static void resetWordStates() {
        for (LyricLine lyric : lyrics) {
            for (LyricLine.Word word : lyric.words) {
                word.alpha = 0.0f;
                word.progress = 0.0;
            }
        }
    }

    public static String getSecondaryLyrics(LyricLine lyricLine) {
        if (hasTransLyrics) {
            return getTranslationOrRomanizationText(lyricLine);
        }

        if (hasRomanization) {
            return getRomanizationTextIfEnabled(lyricLine);
        }

        return "";
    }

    private static String getTranslationOrRomanizationText(LyricLine lyricLine) {
        boolean showRoman = MusicState.get().isShowRoman();

        if (!showRoman) {
            return StringUtil.returnEmptyStringIfNull(lyricLine.getTranslationText());
        }

        if (hasRomanization) {
            return StringUtil.returnEmptyStringIfNull(lyricLine.getRomanizationText());
        }

        return StringUtil.returnEmptyStringIfNull(lyricLine.getTranslationText());
    }

    private static String getRomanizationTextIfEnabled(LyricLine lyricLine) {
        if (MusicState.get().isShowRoman()) {
            return StringUtil.returnEmptyStringIfNull(lyricLine.getRomanizationText());
        }
        return "";
    }

    public static boolean hasSecondaryLyrics() {
        boolean hasAvailableLyrics = hasTransLyrics || hasRomanization;
        boolean showTranslationEnabled = MusicState.get().isShowTranslation();
        return hasAvailableLyrics && showTranslationEnabled;
    }

    @SneakyThrows
    public static void initNCM() {
        String cookie = getCookieFromFileOrOptions();

        if (cookie.isEmpty()) {
            Platform.log("[NCM] Not logged in.");
        } else {
            loadNCM(cookie);
        }
    }

    @SneakyThrows
    private static String loadCookie() {
        File cookieFile = cookieFile();
        if (!cookieFile.exists()) {
            return "";
        }

        List<String> cookieLines = Files.readAllLines(cookieFile.toPath());
        return cookieLines.isEmpty() ? "" : cookieLines.getFirst();
    }

    private static String getCookieFromFileOrOptions() {
        String cookie = loadCookie();
        return cookie.isEmpty() ? OptionsUtil.getCookie() : cookie;
    }

    public static void loadNCM(String cookie) {
        OptionsUtil.setCookie(cookie);
        profile = getUserProfile();

        if (profile == null) {
            return;
        }

        Platform.log("[NCM] Logged in as " + profile.getName() + "(" + profile.getId() + ")");

        if (!OptionsUtil.getCookie().isEmpty()) {
            onStop();
        }

        CloudMusic.playLists = loadUserPlaylists();
        Platform.log("[NCM] Loaded " + playLists.size() + " playlists");

        likeList = likeList();
    }

    private static List<PlayList> loadUserPlaylists() {
        List<PlayList> userPlaylists = new ArrayList<>();
        int page = 0;

        while (true) {
            List<PlayList> pagePlaylists = fetchPlaylistsPage(page);

            if (pagePlaylists.isEmpty()) {
                break;
            }

            userPlaylists.addAll(pagePlaylists);
            page++;
        }

        return userPlaylists;
    }

    private static List<PlayList> fetchPlaylistsPage(int page) {
        try {
            return profile.playLists(page, 30);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    @SneakyThrows
    public static void onStop() {
        Files.write(cookieFile().toPath(), OptionsUtil.getCookie().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    public static void shutdownPlayback() {
        doBreak = true;
        playing.set(false);

        if (playThread != null) {
            playThread.interrupt();
        }

        if (player != null) {
            try {
                player.close();
            } catch (Exception ignored) {
            }
        }
    }

    @Getter
    public enum PlayMode {
        Random("F"),
        LoopInList("I"),
        LoopSingle("L"),
        Sequential("G");

        private final String icon;

        PlayMode(String icon) {
            this.icon = icon;
        }
    }

    public static volatile boolean dontAdd = false;

    public static void prev() {
        updatePlayCountIfNeeded();

        if (!canPlayPrevious()) {
            return;
        }

        if (player != null && !playList.isEmpty()) {
            prepareForTrackChange();
            curIdx--;
            stopCurrentPlayback();
        }
    }

    private static boolean canPlayPrevious() {
        if (curIdx - 1 >= 0) {
            return true;
        }

        if (playMode == PlayMode.LoopInList) {
            curIdx = playList.size();
            return true;
        } else if (playMode == PlayMode.LoopSingle) {
            curIdx++;
            return true;
        }

        return false;
    }

    public static void next() {
        if (!canPlayNext()) {
            return;
        }

        if (player != null && !playList.isEmpty()) {
            updatePlayCountIfNeeded();
            prepareForTrackChange();
            curIdx++;
            stopCurrentPlayback();
        }
    }

    private static boolean canPlayNext() {
        return curIdx + 1 <= playList.size() - 1 || playMode != PlayMode.Sequential;
    }

    /**
     * 给网易云发送当前歌曲的播放时长
     */
    private static void updatePlayCountIfNeeded() {
        if (playedFrom != null && player != null) {
            playList.get(curIdx).updPlayCount(playedFrom, player.getCurrentTimeSeconds());
        }
    }

    private static void prepareForTrackChange() {
        dontAdd = true;
    }

    private static void stopCurrentPlayback() {
        player.close();
        playing.set(false);
    }

    /**
     * 播放来源, 用于记录播放时长
     */
    public static PlayList playedFrom = null;

    /**
     * 播放给定的列表中的所有歌曲
     *
     * @param songs    歌曲列表
     * @param startIdx 第一首播放的索引
     */
    @SneakyThrows
    public static void play(List<Music> songs, int startIdx) {
        List<Music> safeSongList = new ArrayList<>(songs);

        stopExistingPlayThread();

        if (playMode == PlayMode.Random) {
            startIdx = handleRandomPlayMode(safeSongList, startIdx);
        }

        startIdx = normalizeStartIndex(startIdx);
        loadMusicCover(safeSongList.getFirst());

        playList = safeSongList;
        startNewPlayThread(safeSongList, startIdx);
    }

    private static void stopExistingPlayThread() {
        if (playThread != null) {
            doBreak = true;
            playing.set(false);
            playThread.interrupt();
        }
    }

    private static int handleRandomPlayMode(List<Music> songs, int startIdx) {
        if (startIdx == -1) {
            Collections.shuffle(songs);
        } else {
            Music selectedMusic = songs.get(startIdx);
            Collections.shuffle(songs);
            startIdx = songs.indexOf(selectedMusic);
        }
        return startIdx;
    }

    private static int normalizeStartIndex(int startIdx) {
        return startIdx == -1 ? 0 : startIdx;
    }

    private static void startNewPlayThread(List<Music> songs, int startIdx) {
        playThread = new PlayThread(songs, startIdx);
        doBreak = false;
        playing.set(false);
        playThread.start();
    }

    static volatile boolean doBreak = false;

    static AtomicBoolean playing = new AtomicBoolean(true);

    private static class PlayThread extends Thread {
        private final List<Music> songs;
        private final int startIdx;

        public PlayThread(List<Music> songs, int startIdx) {
            this.songs = songs;
            this.setName("Play Thread");
            this.setDaemon(true);
            this.startIdx = startIdx;
        }

        @Override
        public void run() {
            curIdx = startIdx;

            while (shouldContinuePlayback()) {
                if (playListChanged()) {
                    break;
                }

                Music currentSong = playList.get(curIdx);
                prepareForPlayback();

                if (!playSong(currentSong)) {
                    break;
                }

                preloadNextCover();
                waitForPlaybackCompletion();
                handlePlaybackCompletion();
                updateCurrentIndex();
            }
        }

        private boolean shouldContinuePlayback() {
            return curIdx < playList.size() && !doBreak && !this.isInterrupted();
        }

        private boolean playListChanged() {
            return playList != songs;
        }

        private void prepareForPlayback() {
            stopPreviousPlayer();
            loadMusicCover(playList.get(curIdx));
        }

        private boolean playSong(Music song) {
            loadLyric(song);
            currentlyPlaying = song;

            Pair<String, String> playUrl = song.getPlayUrl();

            if (playUrl == null) {
                handleUnplayableSong(song);
                return false;
            }

            return initializeAndPlaySong(song, playUrl);
        }

        private boolean initializeAndPlaySong(Music song, Pair<String, String> playUrl) {
            MusicState.get().setDownloading(false);
            File musicFile = getMusicFile(playUrl, song);

            try {
                player = initializePlayer(musicFile);
            } catch (Exception e) {
                handlePlayerInitializationError(e);
                return false;
            }

            notifySongStart(song);
            startPlayback(song, playUrl, musicFile);
            return true;
        }

        private void waitForPlaybackCompletion() {
            while (playing.get()) {
                if (this.isInterrupted() || doBreak) {
                    break;
                }

                CloudMusic.updateCurrentLyric(player.getCurrentTimeMillis());

                try {
                    Thread.sleep(10L);
                } catch (Exception e) {
                    // Ignore interruption exceptions during playback
                }
            }
        }

        private void handlePlaybackCompletion() {
            if (!dontAdd && playedFrom != null) {
                playList.get(curIdx).updPlayCount(playedFrom, player.getCurrentTimeSeconds());
            }

            player.close();
        }

        private void stopPreviousPlayer() {
            if (player != null && !player.isFinished()) {
                player.close();
                sleep(250);
            }
        }

        private void handleUnplayableSong(Music song) {
            Platform.sendChatMessage("§c无法播放: " + song.getName() + " - " + song.getArtistsName());
            Platform.log("§c无法播放: " + song.getName() + " - " + song.getArtistsName() + ", 可能因为该歌曲没有版权");
        }

        private void handlePlayerInitializationError(Exception e) {
            e.printStackTrace();
            Platform.log("§c[NCM] Failed to initiate audio player! Error: " + e.getMessage());
        }

        private void notifySongStart(Music song) {
            for (MusicListener listener : listeners) {
                listener.onSongStart(song);
            }
            Platform.log("[NCM] Now playing: " + song.getName() + ", id " + song.getId());
        }

        private void startPlayback(Music song, Pair<String, String> playUrl, File musicFile) {
            player.play();
            playing.set(true);

            player.setAfterPlayed(() -> {
                this.notifyWaitLock();
                playing.set(false);
            });
        }

        private void preloadNextCover() {
            if (curIdx + 1 < playList.size()) {
                loadMusicCover(playList.get(curIdx + 1));
            }
        }

        private void updateCurrentIndex() {
            updateCurIdx();
        }

        private File getMusicFile(Pair<String, String> playUrl, Music song) {

            String url = playUrl.getA();
            String type = playUrl.getB().toLowerCase();

            if (type.equals("flac") || type.equals("wav") || type.equals("mp3")) {
                return getCachedOrTempFile(url, type, song);
            }
            throw new IllegalArgumentException("Unsupported music format, url: " + url + ", type: " + type);
        }

        private File getCachedOrTempFile(String playUrl, String type, Music song) {
            File musicCacheDir = new File(Platform.configDir(), "MusicCache");

            if (!musicCacheDir.exists()) {
                musicCacheDir.mkdir();
            }

            String extension = "_" + quality.getQuality() + "." + type;

            File music = new File(musicCacheDir, song.getId() + extension);

            if (!music.exists()) {
                downloadMusic(playUrl, music);

                AsyncUtil.runAsync(() -> {
                    for (File file : musicCacheDir.listFiles()) {
                        if (file.getName().startsWith(String.valueOf(song.getId())) && !file.getName().startsWith(song.getId() + "_" + quality.getQuality())) {
                            file.delete();
                        }
                    }
                });
            }

            return music;
        }

        private AudioPlayer initializePlayer(File musicFile) {
            AudioPlayer player = CloudMusic.player;
            if (player == null) {
                player = new AudioPlayer(musicFile);
                player.setVolume(MusicState.get().getVolume());
                CloudMusic.player = player;
            } else {
                player.setAudio(musicFile);
            }
            return player;
        }

        private void notifyWaitLock() {
            playing.set(false);
        }

        private void loadMusicCover(Music song) {
            CloudMusic.loadMusicCover(song);
        }

        PlayMode lastMode = playMode;

        private void updateCurIdx() {

            if (lastMode != playMode) {

                if (playMode == PlayMode.Random) {
                    Collections.shuffle(songs);
                    playList = songs;
                }

                lastMode = playMode;
            }

            if (playMode == PlayMode.LoopSingle) {
                if (dontAdd) {
                    dontAdd = false;
                }

                if (curIdx < 0) {
                    curIdx = 0;
                }
            } else if (playMode == PlayMode.LoopInList || playMode == PlayMode.Random) {
                if (!dontAdd) {
                    curIdx++;
                } else {
                    dontAdd = false;
                }

                if (curIdx == playList.size()) {
                    curIdx = 0;
                }
            } else {
                if (!dontAdd) {
                    curIdx++;
                } else {
                    dontAdd = false;
                }
            }
        }

        private void sleep(int millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static void loadMusicCover(Music music) {
        loadMusicCover(music, false);
    }

    public static void loadMusicCover(Music music, boolean forceReload) {
        if (shouldLoadCover(music.getCoverLocation(), forceReload)) {
            loadMainCoverAsync(music, music.getCoverLocation(), music.getBlurredCoverLocation());
        }

        if (shouldLoadCover(music.getSmallCoverLocation(), forceReload)) {
            loadSmallCoverAsync(music, music.getSmallCoverLocation());
        }
    }

    private static final Set<tritium.music.platform.TextureHandle> LOADING_COVERS = ConcurrentHashMap.newKeySet();

    private static boolean shouldLoadCover(tritium.music.platform.TextureHandle coverLocation, boolean forceReload) {
        return !Platform.hasTexture(coverLocation) || forceReload;
    }

    private static void loadMainCoverAsync(Music music, tritium.music.platform.TextureHandle musicCover, tritium.music.platform.TextureHandle musicCoverBlur) {
        AsyncUtil.runAsync(() -> {
            try {
                @Cleanup
                InputStream coverStream = HttpUtils.downloadStream(music.getCoverUrl(320), 5);
                if (coverStream == null) {
                    return;
                }
                byte[] imageData = coverStream.readAllBytes();

                BufferedImage coverImage = Textures.decode(new ByteArrayInputStream(imageData));

                if (coverImage != null) {
                    loadCoverTextures(coverImage, musicCover, musicCoverBlur);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                LOADING_COVERS.remove(musicCover);
            }
        });
    }

    private static void loadCoverTextures(BufferedImage coverImage, tritium.music.platform.TextureHandle musicCover, tritium.music.platform.TextureHandle musicCoverBlur) {
        Textures.loadTexture(musicCover, coverImage);

        AsyncUtil.runAsync(() -> {
            BufferedImage inputImage = new BufferedImage(coverImage.getWidth(), coverImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
            inputImage.setRGB(0, 0, coverImage.getWidth(), coverImage.getHeight(),
                    coverImage.getRGB(0, 0, coverImage.getWidth(), coverImage.getHeight(), null, 0, coverImage.getWidth()),
                    0, coverImage.getWidth());

            BufferedImage blurredImage = gaussianBlur(inputImage, 31);
            Textures.loadTexture(musicCoverBlur, blurredImage);
        });
    }

    private static void loadSmallCoverAsync(Music music, tritium.music.platform.TextureHandle musicCoverSmall) {
        AsyncUtil.runAsync(() -> {
            try (InputStream smallCoverStream = HttpUtils.downloadStream(music.getCoverUrl(128), 5)) {
                if (smallCoverStream == null) {
                    return;
                }
                BufferedImage smallCoverImage = Textures.decode(smallCoverStream);
                Textures.loadTexture(musicCoverSmall, smallCoverImage);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                LOADING_COVERS.remove(musicCoverSmall);
            }
        });
    }

    private static final Kernel GAUSSIAN_KERNEL = new Kernel(41, 41, GaussianKernel.generate(41));

    public static BufferedImage gaussianBlur(BufferedImage imgIn, int blur) {
        Map<RenderingHints.Key, Object> map = new HashMap<>();
        map.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        map.put(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        map.put(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        RenderingHints hints = new RenderingHints(map);

        ConvolveOp op = new ConvolveOp(GAUSSIAN_KERNEL, ConvolveOp.EDGE_NO_OP, hints);

        BufferedImage filtered = op.filter(imgIn, null);

        BufferedImage output = new BufferedImage(filtered.getWidth(), filtered.getHeight(), filtered.getType());
        Graphics2D graphics = (Graphics2D) output.getGraphics();
        graphics.setRenderingHints(map);
        graphics.drawImage(filtered, -blur, -blur, filtered.getWidth() + blur * 2, filtered.getHeight() + blur * 2, null);
        graphics.dispose();

        return output;
    }

    @SneakyThrows
    private static void downloadMusic(String playUrl, File music) {

        MusicState state = MusicState.get();
        state.setDownloading(true);
        state.setDownloadProgress(0);
        state.setDownloadSpeed("0 b/s");

        try {
            InputStream stream = new WrappedInputStream(HttpUtils.get(playUrl, null), new WrappedInputStream.ProgressListener() {

                final Timer timer = new Timer();

                @Override
                public void onProgress(double progress) {
                    if (progress >= 1) {
                        state.setDownloading(false);
                    }

                    state.setDownloadProgress(progress);
                }

                final long kilo = 1024;
                final long mega = kilo * kilo;
                final long giga = mega * kilo;
                final long tera = giga * kilo;

                String getSize(long size) {
                    String s;
                    double kb = (double) size / kilo;
                    double mb = kb / kilo;
                    double gb = mb / kilo;
                    double tb = gb / kilo;
                    if (size < kilo) {
                        s = size + " Bytes";
                    } else if (size < mega) {
                        s = String.format("%.2f", kb) + " KB";
                    } else if (size < giga) {
                        s = String.format("%.2f", mb) + " MB";
                    } else if (size < tera) {
                        s = String.format("%.2f", gb) + " GB";
                    } else {
                        s = String.format("%.2f", tb) + " TB";
                    }
                    return s;
                }

                int lastBytesRead = 0;

                @Override
                public void bytesRead(int bytesRead) {

                    int checkDelay = 500;

                    if (timer.isDelayed(checkDelay)) {
                        timer.reset();

                        int diff = (bytesRead - lastBytesRead) * (1000 / checkDelay);

                        state.setDownloadSpeed(this.getSize(diff) + "/s");

                        lastBytesRead = bytesRead;
                    }

                }
            });

            OutputStream os = Files.newOutputStream(music.toPath(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

            writeTo(stream, os);

            os.close();

        } catch (Throwable t) {
            t.printStackTrace();

            state.setDownloading(false);

            music.delete();
        }
    }

    @SneakyThrows
    public static void writeTo(InputStream src, OutputStream dest) {
        byte[] buffer = new byte[1024];
        int len;
        while ((len = src.read(buffer)) != -1) {
            dest.write(buffer, 0, len);
        }
        dest.flush();
    }

    public static void loadLyric(Music music) {
        AsyncUtil.runAsync(() -> {

            String string = CloudMusicApi.lyricNew(music.getId()).toString();

            string = string.replaceAll("[ - ]", " ");

            JsonObject json = JsonUtils.toJsonObject(string);

            List<LyricLine> parsed = LyricParser.parse(json);

            InputStream stream = CloudMusic.class.getResourceAsStream("/assets/tritium-music/yrc/" + music.getId() + ".yrc");
            if (stream != null) {
                try {
                    String s = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                    List<LyricLine> newLines = new ArrayList<>();
                    LyricParser.parseYrc(s, newLines);

                    for (int i = 0; i < newLines.size(); i++) {
                        LyricLine newLine = newLines.get(i);
                        LyricLine oldLine = parsed.get(i);
                        oldLine.words.clear();
                        oldLine.words.addAll(newLine.words);
                        oldLine.timestamp = newLine.timestamp;
                        oldLine.lyric = newLine.lyric;
                        oldLine.duration = newLine.duration;
                    }

                    stream.close();
                } catch (IOException ignored) {
                }
            }

            initLyrics(json, music, parsed);

        });
    }

    public static String qrCodeLogin() {
        String key = CloudMusic.qrKey();

        QRCodeGenerator.generateAndLoadTexture("https://music.163.com/login?codekey=" + key);

        while (true) {

            if (Thread.currentThread().isInterrupted()) {
                return "";
            }

            JsonObject json = CloudMusicApi.loginQrCheck(key).toJsonObject();

            int code = json.get("code").getAsInt();
            if (code == 800) {
                key = CloudMusic.qrKey();

                NCMScreen.getInstance().loginRenderer.avatarLoaded = false;
                NCMScreen.getInstance().loginRenderer.scannedUserName = "";

                QRCodeGenerator.generateAndLoadTexture("https://music.163.com/login?codekey=" + key);
            }

            if (code == 802) {
                if (json.has("nickname")) {
                    NCMScreen.getInstance().loginRenderer.scannedUserName = json.get("nickname").getAsString();
                }

                if (json.has("avatarUrl")) {
                    String url = json.get("avatarUrl").getAsString();

                    if (!NCMScreen.getInstance().loginRenderer.avatarLoaded) {
                        NCMScreen.getInstance().loginRenderer.avatarLoaded = true;
                        AsyncUtil.runAsync(() -> {
                            try (InputStream is = HttpUtils.get(url, null)) {
                                BufferedImage img = ImageIO.read(is);

                                Textures.loadTextureAsync(NCMScreen.getInstance().loginRenderer.scannedAvatar, img);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
                    }
                }
            }

            if (code == 803) {

                String cookie = json.get("cookie").getAsString();

                String[] split = cookie.split(";");
                StringBuilder sb = new StringBuilder();
                for (String s : split) {
                    if (s.contains("MUSIC_U") || s.contains("__csrf")) {
                        sb.append(s).append("; ");
                    }
                }

                return sb.substring(0, sb.length() - 2);
            }

            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static User getUserProfile() {
        JsonObject jsonObject = CloudMusicApi.loginStatus().toJsonObject();

        JsonObject d = jsonObject.getAsJsonObject("data");

        if ((!d.has("account") || d.get("account") instanceof JsonNull) || (!d.has("profile") || d.get("profile") instanceof JsonNull)) {
            OptionsUtil.setCookie("");
            return null;
        }

        JsonObject profile = d.getAsJsonObject("profile");

        return JsonUtils.parse(profile, User.class);
    }

    public static List<Music> search(String keyWord) {
        List<Music> searchResults = new ArrayList<>();
        JsonObject searchResponse = CloudMusicApi.cloudSearch(keyWord, CloudMusicApi.SearchType.Single).toJsonObject();

        JsonArray songs = extractSongsFromResponse(searchResponse);

        if (songs != null) {
            for (JsonElement song : songs) {
                searchResults.add(JsonUtils.parse(song.getAsJsonObject(), Music.class));
            }
        }

        return searchResults;
    }

    private static JsonArray extractSongsFromResponse(JsonObject searchResponse) {
        try {
            JsonObject result = searchResponse.getAsJsonObject("result");
            return result != null ? result.getAsJsonArray("songs") : null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse search response", e);
        }
    }

    public static List<Long> likeList() {
        List<Long> list = new ArrayList<>();

        JsonObject json = CloudMusicApi.likeList(profile.getId()).toJsonObject();

        JsonArray ids = json.getAsJsonArray("ids");
        for (JsonElement id : ids) {
            list.add(id.getAsLong());
        }

        return list;
    }

    public static String qrKey() {
        JsonObject json = CloudMusicApi.loginQrKey().toJsonObject();
        return json.getAsJsonObject("data").get("unikey").getAsString();
    }
}
