package tritium.music.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import lombok.Cleanup;
import lombok.Getter;
import lombok.SneakyThrows;
import tritium.music.client.screens.ncm.NCMScreen;
import tritium.music.core.audio.*;
import tritium.music.core.lyric.LyricLine;
import tritium.music.core.lyric.LyricParser;
import tritium.music.core.lyric.provider.LyricProviderPreferences;
import tritium.music.core.lyric.provider.LyricsFetcher;
import tritium.music.core.lyric.provider.LyricsQuery;
import tritium.music.core.lyric.provider.LyricsResult;
import tritium.music.core.model.Music;
import tritium.music.core.model.PlayList;
import tritium.music.core.model.Quality;
import tritium.music.core.model.User;
import tritium.music.core.ncm.OptionsUtil;
import tritium.music.core.ncm.QRCodeGenerator;
import tritium.music.core.ncm.api.CloudMusicApi;
import tritium.music.core.util.*;
import tritium.music.platform.Platform;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author IzumiiKonata
 * @since 6/16/2023 9:34 AM
 */
public class CloudMusic {

    public static final List<LyricLine> lyrics = new CopyOnWriteArrayList<>();
    static final float JUMP_TO_NEXT_MILLIS = 300.0f;
    @Getter
    private static final Map<String, String> headers = new HashMap<>();
    private static final List<MusicListener> listeners = new CopyOnWriteArrayList<>();
    private static final Map<Long, AutoMixTrackAnalysis> AUTO_MIX_ANALYSIS_CACHE = Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, AutoMixTrackAnalysis> eldest) {
            return size() > 64;
        }
    });
    private static final Set<tritium.music.platform.TextureHandle> LOADING_COVERS = ConcurrentHashMap.newKeySet();
    private static final Kernel GAUSSIAN_KERNEL = new Kernel(41, 41, GaussianKernel.generate(41));
    public static AudioPlayer player;
    public static List<Music> playList = new ArrayList<>();
    public static volatile int curIdx = 0;
    public static Music currentlyPlaying;
    public static volatile Thread playThread;
    public static User profile;
    public static List<PlayList> playLists;
    public static List<Long> likeList;
    public static PlayMode playMode = PlayMode.Sequential;
    public static Quality quality = Quality.STANDARD;
    public static volatile boolean autoMixEnabled = false;
    public static LyricLine currentLyric = null;
    public static LyricLine currentLyricNoEarlyJump = null;
    public static boolean hasTransLyrics = false;
    public static boolean hasRomanization = false;
    public static boolean haveNoWords = false;
    public static volatile String currentLyricsSource = "";
    public static volatile long currentLyricsSongId = -1;
    public static volatile boolean dontAdd = false;
    /**
     * 播放来源, 用于记录播放时长
     */
    public static PlayList playedFrom = null;
    static volatile boolean doBreak = false;
    static AtomicBoolean playing = new AtomicBoolean(true);

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
        if (rawLyricData != null) detectTranslations(rawLyricData);
        if (rawLyricData == null) detectTranslations(parsedLyrics);

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
            lyrics.add(new LyricLine(0L, Platform.translate("tritium-music.ui.lyrics.unavailable")));
        }
    }

    private static void detectTranslations(JsonObject lyric) {
        if (hasLyricsType(lyric, "tlyric") || hasLyricsType(lyric, "ytlrc")) hasTransLyrics = true;
        if (hasLyricsType(lyric, "romalrc") || hasLyricsType(lyric, "yromalrc")) hasRomanization = true;
    }

    private static void detectTranslations(List<LyricLine> lyricLines) {
        hasTransLyrics = lyricLines.stream().anyMatch(line -> line.translationText != null && !line.translationText.isBlank());
        hasRomanization = lyricLines.stream().anyMatch(line -> line.romanizationText != null && !line.romanizationText.isBlank());
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

    static boolean canJumpToNextEarly(double songProgress, LyricLine lyric) {
        if (lyric == null || lyric.words.isEmpty()) return false;

        return lyric.duration >= JUMP_TO_NEXT_MILLIS;
    }

    public static LyricLine findCurrentLyric(double songProgress) {
        return findCurrentLyric(songProgress, true);
    }

    public static LyricLine findCurrentLyric(double songProgress, boolean allowEarlyJump) {
        for (int i = 0; i < lyrics.size(); i++) {
            LyricLine lyric = lyrics.get(i);
            LyricLine prev = i > 0 ? lyrics.get(i - 1) : null;

            if (allowEarlyJump && !haveNoWords && !lyric.isBreakLine && lyric.getTimestamp() > songProgress && lyric.getTimestamp() - songProgress <= JUMP_TO_NEXT_MILLIS && canJumpToNextEarly(songProgress, prev)) {
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

        Platform.log("[NCM] Logged in as " + profile.name() + "(" + profile.id() + ")");

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

    public static synchronized void refreshLibrary() {
        if (profile == null) return;
        List<PlayList> refreshedPlaylists = loadUserPlaylists();
        if (!refreshedPlaylists.isEmpty()) playLists = refreshedPlaylists;
        try {
            likeList = likeList();
        } catch (Exception e) {
            Platform.log("[NCM] Failed to refresh liked songs: " + e.getMessage());
        }
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

        Thread currentPlayThread = playThread;
        playThread = null;
        if (currentPlayThread != null) {
            currentPlayThread.interrupt();
        }

        if (player != null) {
            try {
                player.close();
            } catch (Exception ignored) {
            }
        }
    }

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

    public static synchronized void playNext(Music music) {
        if (music == null) return;
        if (currentlyPlaying == null || player == null || playList.isEmpty()) {
            play(List.of(music), 0);
            return;
        }
        int nextIndex = Math.min(curIdx + 1, playList.size());
        playList.add(nextIndex, music);
        loadMusicCover(music);
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
     * 播放给定的列表中的所有歌曲
     *
     * @param songs    歌曲列表
     * @param startIdx 第一首播放的索引
     */
    @SneakyThrows
    public static void play(List<Music> songs, int startIdx) {
        List<Music> safeSongList = new CopyOnWriteArrayList<>(songs);

        stopExistingPlayThread();

        if (playMode == PlayMode.Random) {
            startIdx = handleRandomPlayMode(safeSongList, startIdx);
        }

        startIdx = normalizeStartIndex(startIdx);
        loadMusicCover(safeSongList.get(startIdx));

        playList = safeSongList;
        startNewPlayThread(safeSongList, startIdx);
    }

    private static void stopExistingPlayThread() {
        Thread currentPlayThread = playThread;
        playThread = null;
        if (currentPlayThread != null) {
            doBreak = true;
            playing.set(false);
            currentPlayThread.interrupt();
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
        PlayThread newPlayThread = new PlayThread(songs, startIdx);
        doBreak = false;
        playing.set(false);
        playThread = newPlayThread;
        newPlayThread.start();
    }

    public static void loadMusicCover(Music music) {
        loadMusicCover(music, false);
    }

    public static void loadMusicCover(Music music, boolean forceReload) {
        tritium.music.platform.TextureHandle mainCover = music.getCoverLocation();
        tritium.music.platform.TextureHandle blurredCover = music.getBlurredCoverLocation();
        boolean mainCoverIncomplete = forceReload || !Platform.hasTexture(mainCover) || !Platform.hasTexture(blurredCover);
        if (mainCoverIncomplete && LOADING_COVERS.add(mainCover)) {
            loadMainCoverAsync(music, mainCover, blurredCover);
        }

        tritium.music.platform.TextureHandle smallCover = music.getSmallCoverLocation();
        if ((forceReload || !Platform.hasTexture(smallCover)) && LOADING_COVERS.add(smallCover)) {
            loadSmallCoverAsync(music, smallCover);
        }
    }

    private static void loadMainCoverAsync(Music music, tritium.music.platform.TextureHandle musicCover, tritium.music.platform.TextureHandle musicCoverBlur) {
        AsyncUtil.runAsync(() -> {
            try {
                @Cleanup InputStream coverStream = HttpUtils.downloadStream(music.getCoverUrl(320), 5);
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
            inputImage.setRGB(0, 0, coverImage.getWidth(), coverImage.getHeight(), coverImage.getRGB(0, 0, coverImage.getWidth(), coverImage.getHeight(), null, 0, coverImage.getWidth()), 0, coverImage.getWidth());

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

    public static void loadLyric(Music music) {
        AsyncUtil.runAsync(() -> {
            LyricsQuery query = lyricsQuery(music);
            String selectedProvider = LyricProviderPreferences.get().provider(music.getId());
            Optional<LyricsResult> fetched = LyricsFetcher.getDefault().fetch(query, selectedProvider);
            if (!selectedProvider.equals(LyricProviderPreferences.get().provider(music.getId()))) return;
            LyricsResult result = fetched.orElse(new LyricsResult("", "plain", "none"));
            applyLyrics(music, result);
        });
    }

    public static List<LyricsFetcher.AvailableLyrics> availableLyrics(Music music) {
        return LyricsFetcher.getDefault().available(lyricsQuery(music));
    }

    public static void selectLyricsProvider(Music music, String providerId) {
        LyricProviderPreferences.get().select(music.getId(), providerId);
        loadLyric(music);
    }

    public static String selectedLyricsProvider(Music music) {
        return LyricProviderPreferences.get().provider(music.getId());
    }

    private static void applyLyrics(Music music, LyricsResult result) {
        List<LyricLine> parsed = LyricParser.parse(result);
        JsonObject json = "netease".equals(result.format()) ? JsonUtils.toJsonObject(result.lyrics()) : null;

        InputStream stream = CloudMusic.class.getResourceAsStream("/assets/tritium-music/yrc/" + music.getId() + ".yrc");
        if (stream != null && json != null) {
            try {
                String s = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                List<LyricLine> newLines = new ArrayList<>();
                LyricParser.parseYrc(s, newLines);

                for (int i = 0; i < Math.min(newLines.size(), parsed.size()); i++) {
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

        if (music.equals(currentlyPlaying)) {
            currentLyricsSource = result.source();
            currentLyricsSongId = music.getId();
            initLyrics(json, music, parsed);
        }
    }

    private static LyricsQuery lyricsQuery(Music music) {
        String album = music.getAlbum() == null || music.getAlbum().getName() == null ? "" : music.getAlbum().getName();
        return new LyricsQuery(music.getId(), music.getArtistsName(), music.getName(), album);
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
                return OptionsUtil.getCookie();
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

        if ((!jsonObject.has("account") || jsonObject.get("account") instanceof JsonNull) || (!jsonObject.has("profile") || jsonObject.get("profile") instanceof JsonNull)) {
            OptionsUtil.clearAuthentication();
            return null;
        }

        JsonObject profile = jsonObject.getAsJsonObject("profile");

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

        JsonObject json = CloudMusicApi.likeList(profile.id()).toJsonObject();

        JsonArray ids = json.getAsJsonArray("ids");
        for (JsonElement id : ids) {
            list.add(id.getAsLong());
        }

        return list;
    }

    public static String qrKey() {
        JsonObject json = CloudMusicApi.loginQrKey().toJsonObject();
        return json.get("unikey").getAsString();
    }

    @Getter
    public enum PlayMode {
        Random("F"), LoopInList("I"), LoopSingle("L"), Sequential("G");

        private final String icon;

        PlayMode(String icon) {
            this.icon = icon;
        }
    }

    private static class PlayThread extends Thread {
        private static final long INTRO_ANALYSIS_MILLIS = 24_000;
        private static final long TAIL_ANALYSIS_MILLIS = 28_000;
        private static final long PREPARE_TIMEOUT_MILLIS = 20_000;
        private final List<Music> songs;
        private final int startIdx;
        private PlayMode lastMode = playMode;
        private Preparation preparation;
        private double loudnessTarget = Double.NaN;

        public PlayThread(List<Music> songs, int startIdx) {
            this.songs = songs;
            this.setName("Play Thread");
            this.setDaemon(true);
            this.startIdx = startIdx;
        }

        @Override
        public void run() {
            curIdx = startIdx;
            TrackSession session = null;
            try {
                while (shouldContinuePlayback()) {
                    if (playList != songs) {
                        break;
                    }
                    if (session == null) {
                        session = startTrack(playList.get(curIdx));
                        if (session == null) {
                            break;
                        }
                    }
                    preparation = prepareNextTrack(session.song());
                    PlaybackResult result = waitForPlayback(session);
                    closePreparationExcept(result.next());
                    if (!isCurrentPlayback() || doBreak || isInterrupted()) {
                        session.tailAnalysis().close();
                        session.player().close();
                        break;
                    }
                    if (!dontAdd && playedFrom != null) {
                        session.song().updPlayCount(playedFrom, session.player().getCurrentTimeSeconds());
                    }
                    if (!result.indexAdvanced() && session.player().isFailed()) {
                        session.tailAnalysis().close();
                        session.player().close();
                        Platform.log("[NCM] Playback stopped after the audio stream could not recover.");
                        break;
                    }
                    session.tailAnalysis().close();
                    session.player().close();
                    if (!result.indexAdvanced()) {
                        updateCurIdx();
                    }
                    session = result.next();
                }
            } finally {
                if (preparation != null) {
                    preparation.close();
                }
                if (isCurrentPlayback()) {
                    playing.set(false);
                }
            }
        }

        private TrackSession startTrack(Music song) {
            AudioPlayer previous = player;
            if (previous != null && !previous.isFinished()) {
                previous.close();
            }
            loadMusicCover(song);
            Pair<String, String> playUrl = song.getPlayUrl();
            if (!isCurrentPlayback()) {
                return null;
            }
            if (playUrl == null) {
                handleUnplayableSong(song);
                return null;
            }
            try {
                AudioPlayer nextPlayer = createPlayer(playUrl, song);
                TrackSession session = new TrackSession(song, nextPlayer, new AtomicBoolean(), new TailAnalysis(song, nextPlayer, null));
                configureCompletion(session);
                promote(session);
                nextPlayer.play();
                return session;
            } catch (Exception e) {
                e.printStackTrace();
                Platform.log("§c[NCM] Failed to initiate audio player! Error: " + e.getMessage());
                return null;
            }
        }

        private void promote(TrackSession session) {
            MusicState.get().setDownloading(false);
            currentlyPlaying = session.song();
            player = session.player();
            player.activateSpectrum();
            loadMusicCover(session.song());
            loadLyric(session.song());
            for (MusicListener listener : listeners) {
                listener.onSongStart(session.song());
            }
            Platform.log("[NCM] Now playing: " + session.song().getName() + ", id " + session.song().getId());
            playing.set(true);
        }

        private PlaybackResult waitForPlayback(TrackSession session) {
            TransitionPlan plan = null;
            double tempoRate = 1;
            long seekRevision = session.player().getSeekRevision();
            while (playing.get() && !session.ended().get() && isCurrentPlayback() && !doBreak && !isInterrupted()) {
                updateCurrentLyric(session.player().getCurrentTimeMillis());
                long currentSeekRevision = session.player().getSeekRevision();
                if (currentSeekRevision != seekRevision) {
                    seekRevision = currentSeekRevision;
                    plan = null;
                    tempoRate = 1;
                }
                if (autoMixEnabled && preparation != null && (session.tailAnalysis().referenceProfile != null || preparation.result() != null)) {
                    session.tailAnalysis().start();
                }
                if (autoMixEnabled && preparation == null) {
                    preparation = prepareNextTrack(session.song());
                }
                if (!autoMixEnabled && preparation != null && preparation.autoMixAnalysis) {
                    preparation.close();
                    preparation = prepareNextTrack(session.song());
                    session.player().setPlaybackRate(1);
                    session.player().setPitchShiftSemitones(0);
                    tempoRate = 1;
                }
                PreparedTrack prepared = preparation == null ? null : preparation.result();
                if (prepared != null && !validPreparedTrack(prepared)) {
                    preparation.close();
                    preparation = null;
                    plan = null;
                    session.player().setPlaybackRate(1);
                    session.player().setPitchShiftSemitones(0);
                    tempoRate = 1;
                    continue;
                }
                if (autoMixEnabled && preparation != null && preparation.autoMixAnalysis && prepared != null && validPreparedTrack(prepared)) {
                    long position = (long) session.player().getCurrentTimeMillis();
                    if (plan == null) {
                        AutoMixTrackAnalysis tail = session.tailAnalysis().result();
                        plan = tail == null ? /*createFallbackTransitionPlan(session, prepared, position)*/null : createTransitionPlan(session, tail, prepared);
                    } else if (!plan.analyzed()) {
                        AutoMixTrackAnalysis tail = session.tailAnalysis().result();
                        if (tail != null) {
                            TransitionPlan enhanced = createTransitionPlan(session, tail, prepared);
                            if (position + 500 < plan.startMillis() && position + 500 < enhanced.startMillis()) {
                                plan = enhanced;
                            }
                        }
                    }
                    if (plan != null && !session.player().isPausing()) {
                        tempoRate = applyTempoSync(session.player(), plan, position, tempoRate);
                    }
                    if (plan != null && !session.player().isPausing() && position >= plan.startMillis()) {
                        PlaybackResult transition = performTransition(session, prepared, fitTransitionPlan(plan, position));
                        if (transition != null) {
                            return transition;
                        }
                        seekRevision = session.player().getSeekRevision();
                        plan = null;
                        tempoRate = 1;
                    }
                }
                sleep(10);
            }
            PreparedTrack prepared = preparation == null ? null : preparation.result();
            if (prepared != null && validPreparedTrack(prepared) && isCurrentPlayback() && !doBreak && !isInterrupted()) {
                return startPreparedTrack(prepared);
            }
            return new PlaybackResult(null, false);
        }

        private Preparation prepareNextTrack(Music currentSong) {
            if (playMode == PlayMode.LoopSingle || playMode != lastMode) {
                return null;
            }
            int nextIndex = nextIndex();
            if (nextIndex < 0 || nextIndex >= playList.size()) {
                return null;
            }
            Music nextSong = playList.get(nextIndex);
            boolean analyze = autoMixEnabled && currentSong.getDuration() >= 25_000 && nextSong.getDuration() >= 20_000;
            if (autoMixEnabled && !analyze) {
                return null;
            }
            loadMusicCover(nextSong);
            LyricsFetcher.getDefault().prefetch(lyricsQuery(nextSong));
            return new Preparation(nextSong, nextIndex, analyze);
        }

        private boolean validPreparedTrack(PreparedTrack prepared) {
            int expectedIndex = dontAdd ? curIdx : nextIndex();
            return playMode == lastMode && prepared.index() == expectedIndex && prepared.index() >= 0 && prepared.index() < playList.size() && playList.get(prepared.index()).equals(prepared.song());
        }

        private PlaybackResult startPreparedTrack(PreparedTrack prepared) {
            AudioPlayer incoming = prepared.player();
            incoming.setMixGain(1);
            incoming.setTransitionEq(1, 1, 1, 0);
            incoming.setPlaybackRate(1);
            incoming.setPitchShiftSemitones(0);
            incoming.setNormalizationGain(normalizationGain(prepared.analysis().profile()));
            TrackSession next = new TrackSession(prepared.song(), incoming, new AtomicBoolean(), new TailAnalysis(prepared.song(), incoming, prepared.analysis().profile()));
            configureCompletion(next);
            updateCurIdx();
            promote(next);
            incoming.play();
            return new PlaybackResult(next, true);
        }

        private TransitionPlan createTransitionPlan(TrackSession current, AutoMixTrackAnalysis tail, PreparedTrack next) {
            AutoMixProfile currentProfile = tail.profile();
            AutoMixProfile nextProfile = next.analysis().profile();
            long lastSound = Math.min(current.song().getDuration(), tail.lastSoundMillis());
            double rate = beatMatchRate(currentProfile, nextProfile);
            AutoMixTransitionSearch.Selection selection = tail.endingType() == AutoMixTrackAnalysis.EndingType.NATURAL_FADE ? null : AutoMixTransitionSearch.find(tail, next.analysis());
            boolean beatBlend = selection != null;
            long incomingCue = selection == null ? next.cueMillis() : selection.incomingMillis();
            long preferredDuration;
            long start;
            TransitionStyle style;
            double eqStrength;
            if (selection != null) {
                start = selection.outgoingMillis();
                preferredDuration = selection.trackOverlapMillis();
                style = TransitionStyle.MUSICAL_BLEND;
                eqStrength = Math.min(0.95, 0.7 + selection.vocalCollision() * 0.25);
            } else if (tail.endingType() == AutoMixTrackAnalysis.EndingType.NATURAL_FADE) {
                preferredDuration = Math.max(3_500, Math.min(9_000, lastSound - tail.fadeOutStartMillis()));
                start = Math.max(tail.fadeOutStartMillis(), lastSound - preferredDuration);
                style = TransitionStyle.NATURAL_FADE;
                eqStrength = 0.35;
            } else if (tail.endingType() == AutoMixTrackAnalysis.EndingType.TRAILING_SILENCE) {
                preferredDuration = 4_500;
                start = Math.max(tail.lastStrongMillis(), lastSound - preferredDuration);
                style = TransitionStyle.SILENCE_SKIP;
                eqStrength = 0.48;
            } else {
                preferredDuration = 4_000;
                start = Math.max(0, lastSound - preferredDuration);
                style = TransitionStyle.CROSSFADE;
                eqStrength = 0.55;
            }
            if (!beatBlend && style != TransitionStyle.NATURAL_FADE && currentProfile.hasReliableBeat()) {
                start = currentProfile.alignToBeat(start);
            }
            start = Math.min(start, Math.max(0, lastSound - 900));
            long trackOverlap = Math.max(900, lastSound - start);
            if (!beatBlend || !AutoMixTempoPolicy.shouldSync(tail, next.analysis(), rate)) {
                rate = 1;
            }
            AutoMixHarmonicMatch harmonicMatch = beatBlend ? AutoMixHarmonicMatch.between(tail, next.analysis(), start, incomingCue, trackOverlap) : new AutoMixHarmonicMatch(0, 0, 0);
            if (Math.abs(next.player().getCurrentTimeMillis() - incomingCue) > 250) {
                next.player().setPlaybackTime((float) incomingCue);
            }
            int pitchShift = harmonicMatch.pitchShiftSemitones();
            long duration = Math.max(900, Math.round(trackOverlap / Math.max(0.01, rate)));
            boolean transformsAudio = rate != 1 || pitchShift != 0;
            long settleDuration = !transformsAudio ? 0 : beatDuration(currentProfile, nextProfile, 2, 900, 1_400);
            double transformMagnitude = Math.max(Math.abs(rate - 1) / AutoMixTempoPolicy.MAX_TEMPO_MATCH_CHANGE, Math.abs(pitchShift) / 3.0);
            int rampBeats = transformMagnitude >= 0.65 ? 5 : transformMagnitude >= 0.3 ? 4 : 3;
            long rampDuration = !transformsAudio ? 0 : beatDuration(currentProfile, nextProfile, rampBeats, 1_400, 3_200);
            long tempoRampEnd = Math.max(0, start - settleDuration);
            long tempoRampStart = Math.max(0, tempoRampEnd - rampDuration);
            if (transformsAudio) {
                tempoRampStart = Math.min(tempoRampEnd, currentProfile.downbeatAtOrBefore(tempoRampStart));
                long planningPosition = (long) current.player().getCurrentTimeMillis();
                if (planningPosition > tempoRampStart + currentProfile.beatIntervalMillis()) {
                    rate = 1;
                    pitchShift = 0;
                    tempoRampStart = start;
                    tempoRampEnd = start;
                }
            }
            Platform.log(String.format(Locale.ROOT, "[NCM] AutoMix: %.1f -> %.1f BPM, rate %.3fx, pitch %+.1f st, %s, %d ms, cue %d -> %d, transform ramp %d-%d ms, confidence %.2f/%.2f, harmony %.2f (+%.2f), structure %s", bpm(currentProfile), bpm(nextProfile), rate, (double) pitchShift, style, duration, start, incomingCue, tempoRampStart, tempoRampEnd, currentProfile.beatConfidence(), nextProfile.beatConfidence(), harmonicMatch.similarity(), harmonicMatch.improvement(), transitionSummary(selection)));
            return new TransitionPlan(start, Math.max(900, duration), style, rate, pitchShift, tempoRampStart, tempoRampEnd, eqStrength, incomingCue, true, lastSound);
        }

        private TransitionPlan createFallbackTransitionPlan(TrackSession current, PreparedTrack next, long positionMillis) {
            AutoMixTransitionTiming.Window window = AutoMixTransitionTiming.fallback(current.song().getDuration(), positionMillis);
            Platform.log(String.format(Locale.ROOT, "[NCM] AutoMix: guaranteed crossfade at %d ms for %d ms while analysis completes", window.startMillis(), window.durationMillis()));
            return new TransitionPlan(window.startMillis(), window.durationMillis(), TransitionStyle.CROSSFADE, 1, 0, window.startMillis(), window.startMillis(), 0, next.cueMillis(), false, current.song().getDuration());
        }

        private TransitionPlan fitTransitionPlan(TransitionPlan plan, long positionMillis) {
            AutoMixTransitionTiming.Window fitted = AutoMixTransitionTiming.fit(new AutoMixTransitionTiming.Window(plan.startMillis(), plan.durationMillis()), positionMillis, plan.endMillis());
            if (fitted.startMillis() == plan.startMillis() && fitted.durationMillis() == plan.durationMillis()) {
                return plan;
            }
            return new TransitionPlan(fitted.startMillis(), fitted.durationMillis(), plan.style(), plan.playbackRate(), plan.pitchShiftSemitones(), fitted.startMillis(), fitted.startMillis(), plan.eqStrength(), plan.incomingCueMillis(), plan.analyzed(), plan.endMillis());
        }

        private double bpm(AutoMixProfile profile) {
            return profile.hasReliableBeat() ? 60_000 / profile.beatIntervalMillis() : 0;
        }

        private long beatDuration(AutoMixProfile current, AutoMixProfile next, int beats, long minimum, long maximum) {
            double interval = current.hasReliableBeat() ? current.beatIntervalMillis() : next.hasReliableBeat() ? next.beatIntervalMillis() : minimum / (double) beats;
            return Math.max(minimum, Math.min(maximum, Math.round(interval * beats)));
        }

        private double beatMatchRate(AutoMixProfile current, AutoMixProfile next) {
            return current.beatMatchRateTo(next, AutoMixTempoPolicy.MAX_TEMPO_MATCH_CHANGE);
        }

        private String transitionSummary(AutoMixTransitionSearch.Selection selection) {
            if (selection == null) {
                return "fallback";
            }
            return String.format(Locale.ROOT, "%.2f [structure %.2f, boundary %.2f, vocals %.2f, harmony %.2f, energy %.2f, %d beats]", selection.score(), selection.structureScore(), selection.boundaryScore(), selection.vocalCollision(), selection.harmonicScore(), selection.energyScore(), selection.beats());
        }

        private double applyTempoSync(AudioPlayer outgoing, TransitionPlan plan, long positionMillis, double currentRate) {
            if (plan.playbackRate() == 1 && plan.pitchShiftSemitones() == 0 || positionMillis < plan.tempoRampStartMillis()) {
                if (currentRate != 1) {
                    outgoing.setPlaybackRate(1);
                }
                outgoing.setPitchShiftSemitones(0);
                return 1;
            }
            double progress = plan.tempoRampEndMillis() <= plan.tempoRampStartMillis() ? 1 : (positionMillis - plan.tempoRampStartMillis()) / (double) (plan.tempoRampEndMillis() - plan.tempoRampStartMillis());
            double desired = 1 + (plan.playbackRate() - 1) * smootherStep(progress);
            double desiredPitch = plan.pitchShiftSemitones() * smootherStep(progress);
            double maximumStep = 0.0006;
            double applied = currentRate + Math.max(-maximumStep, Math.min(maximumStep, desired - currentRate));
            outgoing.setPlaybackRate(applied);
            outgoing.setPitchShiftSemitones(desiredPitch);
            return applied;
        }

        private PlaybackResult performTransition(TrackSession current, PreparedTrack prepared, TransitionPlan plan) {
            AudioPlayer outgoing = current.player();
            AudioPlayer incoming = prepared.player();
            AutoMixProfile currentProfile = current.tailAnalysis().result() == null ? outgoing.getAutoMixProfile() : current.tailAnalysis().result().profile();
            if (Double.isNaN(loudnessTarget) && currentProfile.loudnessDb() > -35) {
                loudnessTarget = currentProfile.loudnessDb();
            }
            incoming.setNormalizationGain(normalizationGain(prepared.analysis().profile()));
            if (Math.abs(incoming.getCurrentTimeMillis() - plan.incomingCueMillis()) > 250) {
                incoming.setPlaybackTime((float) plan.incomingCueMillis());
            }
            incoming.setMixGain(0);
            float incomingLow = (float) (1 - 0.82 * plan.eqStrength());
            float incomingMid = (float) (1 - 0.32 * plan.eqStrength());
            float incomingHigh = (float) (1 - 0.18 * plan.eqStrength());
            incoming.setTransitionEq(incomingLow, incomingMid, incomingHigh, 0);
            TrackSession next = new TrackSession(prepared.song(), incoming, new AtomicBoolean(), new TailAnalysis(prepared.song(), incoming, prepared.analysis().profile()));
            configureCompletion(next);
            incoming.play();

            long elapsedNanos = 0;
            long previousNanos = System.nanoTime();
            boolean promoted = false;
            long outgoingSeekRevision = outgoing.getSeekRevision();
            long incomingSeekRevision = incoming.getSeekRevision();
            while (elapsedNanos < plan.durationMillis() * 1_000_000L) {
                if (!playing.get() || doBreak || isInterrupted() || !isCurrentPlayback()) {
                    incoming.close();
                    outgoing.close();
                    if (dontAdd) {
                        updateCurIdx();
                    }
                    return new PlaybackResult(null, true);
                }
                if (outgoing.getSeekRevision() != outgoingSeekRevision && !promoted) {
                    incoming.pause();
                    incoming.setPlaybackTime((float) prepared.cueMillis());
                    incoming.setMixGain(0);
                    outgoing.resetTransitionState();
                    if (outgoing.isPausing()) {
                        outgoing.unpause();
                    }
                    return null;
                }
                if (incoming.getSeekRevision() != incomingSeekRevision && promoted) {
                    outgoing.close();
                    incoming.resetTransitionState();
                    return new PlaybackResult(next, true);
                }
                long now = System.nanoTime();
                if (incoming.isPausing() && !next.ended().get()) {
                    outgoing.pause();
                    previousNanos = now;
                    sleep(10);
                    continue;
                }
                if (outgoing.isPausing() && !current.ended().get()) {
                    outgoing.unpause();
                }
                float masterVolume = MusicState.get().getVolume();
                if (Math.abs(incoming.getVolume() - masterVolume) > 0.0001f) {
                    incoming.setVolume(masterVolume);
                }
                if (Math.abs(outgoing.getVolume() - masterVolume) > 0.0001f) {
                    outgoing.setVolume(masterVolume);
                }
                elapsedNanos += Math.max(0, now - previousNanos);
                previousNanos = now;
                double progress = Math.min(1, elapsedNanos / (plan.durationMillis() * 1_000_000.0));
                applyTransitionGain(outgoing, incoming, progress, plan.style());
                applyTransitionEq(outgoing, incoming, progress, plan.style(), plan.eqStrength());
                if (!promoted && (progress >= 0.5 || current.ended().get())) {
                    updateCurIdx();
                    promote(next);
                    promoted = true;
                }
                updateCurrentLyric(promoted ? incoming.getCurrentTimeMillis() : outgoing.getCurrentTimeMillis());
                if (current.ended().get()) {
                    elapsedNanos = plan.durationMillis() * 1_000_000L;
                }
                if (next.ended().get()) {
                    outgoing.close();
                    return new PlaybackResult(null, true);
                }
                sleep(10);
            }
            incoming.setMixGain(1);
            incoming.setTransitionEq(1, 1, 1, 0);
            incoming.setPlaybackRate(1);
            outgoing.setPlaybackRate(1);
            outgoing.setPitchShiftSemitones(0);
            outgoing.close();
            if (!promoted) {
                updateCurIdx();
                promote(next);
            }
            return new PlaybackResult(next, true);
        }

        private void applyTransitionGain(AudioPlayer outgoing, AudioPlayer incoming, double progress, TransitionStyle style) {
            double outgoingProgress = style == TransitionStyle.NATURAL_FADE ? smoothStep(0.55, 1, progress) : style == TransitionStyle.MUSICAL_BLEND ? smoothStep(0.42, 1, progress) : progress;
            double incomingProgress = style == TransitionStyle.MUSICAL_BLEND ? smoothStep(0, 0.58, progress) : smoothStep(0, 0.82, progress);
            outgoing.setMixGain((float) Math.cos(outgoingProgress * Math.PI * 0.5));
            incoming.setMixGain((float) Math.sin(incomingProgress * Math.PI * 0.5));
        }

        private void applyTransitionEq(AudioPlayer outgoing, AudioPlayer incoming, double progress, TransitionStyle style, double plannedStrength) {
            double strength = plannedStrength * (style == TransitionStyle.NATURAL_FADE ? 0.72 : 1);
            double bassCut = smoothStep(0, 0.48, progress) * strength;
            double bodyCut = smoothStep(0.12, 0.82, progress) * strength;
            double airCut = smoothStep(0.30, 0.92, progress) * strength;
            float lowPass = style == TransitionStyle.MUSICAL_BLEND ? (float) (18_000 * Math.pow(1_800.0 / 18_000, smoothStep(0.34, 1, progress) * strength)) : 0;
            outgoing.setTransitionEq((float) (1 - bassCut * 0.9), (float) (1 - bodyCut * 0.28), (float) (1 - airCut * 0.14), lowPass);

            double incomingBass = smoothStep(0.28, 0.76, progress);
            double incomingBody = smoothStep(0, 0.52, progress);
            incoming.setTransitionEq((float) (1 - (1 - incomingBass) * 0.86 * strength), (float) (1 - (1 - incomingBody) * 0.24 * strength), (float) (1 - (1 - incomingBody) * 0.12 * strength), 0);
        }

        private double smoothStep(double start, double end, double value) {
            double normalized = Math.max(0, Math.min(1, (value - start) / (end - start)));
            return normalized * normalized * (3 - 2 * normalized);
        }

        private double smootherStep(double value) {
            double normalized = Math.max(0, Math.min(1, value));
            return normalized * normalized * normalized * (normalized * (normalized * 6 - 15) + 10);
        }

        private float normalizationGain(AutoMixProfile profile) {
            if (Double.isNaN(loudnessTarget) || profile.loudnessDb() <= -35) {
                return 1;
            }
            double gain = Math.pow(10, (loudnessTarget - profile.loudnessDb()) / 20);
            return (float) Math.max(0.72, Math.min(1.38, gain));
        }

        private void configureCompletion(TrackSession session) {
            session.player().setAfterPlayed(() -> session.ended().set(true));
        }

        private AudioPlayer createPlayer(Pair<String, String> playUrl, Music song) {
            String type = playUrl.b().toLowerCase();
            if (!type.equals("flac") && !type.equals("wav") && !type.equals("mp3")) {
                throw new IllegalArgumentException("Unsupported music format, url: " + playUrl.a() + ", type: " + type);
            }
            AudioPlayer result = new AudioPlayer(playUrl.a(), type, song.getDuration());
            result.setVolume(MusicState.get().getVolume());
            return result;
        }

        private int nextIndex() {
            if (playMode == PlayMode.LoopSingle) {
                return -1;
            }
            int next = curIdx + 1;
            if (next < playList.size()) {
                return next;
            }
            return playMode == PlayMode.LoopInList || playMode == PlayMode.Random ? 0 : -1;
        }

        private void closePreparationExcept(TrackSession retained) {
            if (preparation == null) {
                return;
            }
            preparation.closeExcept(retained == null ? null : retained.player());
            preparation = null;
        }

        private boolean shouldContinuePlayback() {
            return isCurrentPlayback() && curIdx >= 0 && curIdx < playList.size() && !doBreak && !isInterrupted();
        }

        private boolean isCurrentPlayback() {
            return playThread == this;
        }

        private void handleUnplayableSong(Music song) {
            Platform.sendChatMessage("§c" + Platform.translate("tritium-music.ui.playback.unplayable", song.getName(), song.getArtistsName()));
            Platform.log(Platform.translate("tritium-music.ui.playback.unplayable_copyright", song.getName(), song.getArtistsName()));
        }

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
                interrupt();
            }
        }

        private enum TransitionStyle {
            CROSSFADE, NATURAL_FADE, SILENCE_SKIP, MUSICAL_BLEND
        }

        private record TrackSession(Music song, AudioPlayer player, AtomicBoolean ended, TailAnalysis tailAnalysis) {
        }

        private record PreparedTrack(Music song, int index, AudioPlayer player, AutoMixTrackAnalysis analysis,
                                     long cueMillis) {
        }

        private record PlaybackResult(TrackSession next, boolean indexAdvanced) {
        }

        private record TransitionPlan(long startMillis, long durationMillis, TransitionStyle style, double playbackRate,
                                      double pitchShiftSemitones, long tempoRampStartMillis, long tempoRampEndMillis,
                                      double eqStrength, long incomingCueMillis, boolean analyzed, long endMillis) {
        }

        private final class Preparation {
            private final Music song;
            private final int index;
            private final boolean autoMixAnalysis;
            private final Thread thread;
            private volatile PreparedTrack result;
            private volatile boolean closed;

            private Preparation(Music song, int index, boolean autoMixAnalysis) {
                this.song = song;
                this.index = index;
                this.autoMixAnalysis = autoMixAnalysis;
                this.thread = new Thread(this::run, "AutoMix Preloader");
                this.thread.setDaemon(true);
                this.thread.setPriority(Thread.MIN_PRIORITY);
                this.thread.start();
            }

            private void run() {
                AudioPlayer nextPlayer = null;
                try {
                    Pair<String, String> playUrl = song.getPlayUrl();
                    if (playUrl == null || closed || PlayThread.this.isInterrupted()) {
                        return;
                    }
                    nextPlayer = createPlayer(playUrl, song);
                    AutoMixTrackAnalysis analysis = AutoMixTrackAnalysis.fallback(song.getDuration());
                    if (autoMixAnalysis) {
                        AutoMixTrackAnalysis cached = AUTO_MIX_ANALYSIS_CACHE.get(song.getId());
                        if (cached != null) {
                            analysis = cached;
                        } else {
                            try {
                                analysis = nextPlayer.analyzeForAutoMix(0, INTRO_ANALYSIS_MILLIS);
                                AUTO_MIX_ANALYSIS_CACHE.put(song.getId(), analysis);
                            } catch (Exception ignored) {
                            }
                        }
                    }
                    if (closed || Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    long cueMillis = autoMixAnalysis ? AutoMixTransitionSearch.incomingCue(analysis) : 0;
                    nextPlayer.setPlaybackTime((float) cueMillis);
                    nextPlayer.setMixGain(0);
                    nextPlayer.prepare();
                    if (!nextPlayer.awaitPrepared(PREPARE_TIMEOUT_MILLIS) || closed) {
                        return;
                    }
                    result = new PreparedTrack(song, index, nextPlayer, analysis, cueMillis);
                    nextPlayer = null;
                } catch (Exception e) {
                    Platform.log("[NCM] AutoMix preloading fell back to normal playback: " + e.getMessage());
                } finally {
                    if (nextPlayer != null) {
                        nextPlayer.close();
                    }
                }
            }

            private PreparedTrack result() {
                return result;
            }

            private void close() {
                closeExcept(null);
            }

            private void closeExcept(AudioPlayer retained) {
                closed = true;
                thread.interrupt();
                PreparedTrack prepared = result;
                if (prepared != null && prepared.player() != retained) {
                    prepared.player().close();
                }
            }

        }

        private final class TailAnalysis {
            private final Music song;
            private final AudioPlayer player;
            private final AutoMixProfile referenceProfile;
            private volatile Thread thread;
            private volatile AutoMixTrackAnalysis result;

            private TailAnalysis(Music song, AudioPlayer player, AutoMixProfile referenceProfile) {
                this.song = song;
                this.player = player;
                this.referenceProfile = referenceProfile;
            }

            private synchronized void start() {
                if (thread != null) {
                    return;
                }
                thread = new Thread(this::run, "AutoMix Tail Analyzer");
                thread.setDaemon(true);
                thread.setPriority(Thread.MIN_PRIORITY);
                thread.start();
            }

            private void run() {
                long start = Math.max(0, song.getDuration() - TAIL_ANALYSIS_MILLIS);
                try {
                    result = referenceProfile == null ? player.analyzeForAutoMix(start, song.getDuration() - start) : player.analyzeForAutoMix(start, song.getDuration() - start, referenceProfile);
                } catch (Exception e) {
                    result = AutoMixTrackAnalysis.fallback(song.getDuration());
                }
            }

            private AutoMixTrackAnalysis result() {
                return result;
            }

            private void close() {
                Thread current = thread;
                if (current != null) {
                    current.interrupt();
                }
            }
        }
    }
}
