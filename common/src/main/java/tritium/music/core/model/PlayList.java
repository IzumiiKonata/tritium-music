package tritium.music.core.model;

import com.google.gson.JsonArray;
import com.google.gson.annotations.SerializedName;
import lombok.Data;
import tritium.music.core.ncm.api.CloudMusicApi;
import tritium.music.core.util.AsyncUtil;
import tritium.music.core.util.JsonUtils;
import tritium.music.platform.TextureHandle;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 歌单对象
 */
@Data
public class PlayList {

    private static final int TRACK_BATCH_SIZE = 500;

    @SerializedName("id")
    private final long id;

    @SerializedName("name")
    private final String name;

    @SerializedName(value = "coverImgUrl", alternate = {"picUrl"})
    private final String coverUrl;

    @SerializedName("trackCount")
    private final int count;

    @SerializedName(value = "playCount", alternate = {"playcount"})
    private final long playCount;

    @SerializedName("creator")
    private final User creator;

    @SerializedName("description")
    private final String description;

    @SerializedName("subscribed")
    private final boolean subscribed;

    @SerializedName("createTime")
    private final long createTime;

    public transient List<Music> musics;
    private transient boolean searchMode = false;
    public transient boolean musicsQueried = false, musicsLoaded = false;
    public transient int musicsTotal;
    private transient List<MusicsLoadedCallback> pendingCallbacks;
    private transient TextureHandle coverLocation;

    public final TextureHandle getCoverLocation() {
        if (coverLocation == null) {
            coverLocation = TextureHandle.of("textures/playlist/" + this.id + "/cover.png");
        }
        return coverLocation;
    }

    public List<MusicsLoadedCallback> getPendingCallbacks() {
        if (this.pendingCallbacks == null) {
            this.pendingCallbacks = new CopyOnWriteArrayList<>();
        }
        return this.pendingCallbacks;
    }

    public int getLoadedCount() {
        return this.musics == null ? 0 : this.musics.size();
    }

    public List<Music> getMusics() {

        if (this.musics == null)
            this.musics = new CopyOnWriteArrayList<>();

        if (!musics.isEmpty() && (this.musicsQueried || searchMode)) {
            return this.musics;
        }

        if (!this.musicsQueried && !searchMode) {
            this.musicsQueried = true;

            AsyncUtil.runAsync(() -> {
                queryMusics();
                notifyMusicsLoaded();
            });
        }

        return this.musics;
    }

    public void loadMusicsWithCallback(MusicsLoadedCallback callback) {

        if (callback == null)
            return;

        if (this.musics == null)
            this.musics = new CopyOnWriteArrayList<>();

        if (searchMode || musicsLoaded || (!musics.isEmpty() && this.musicsQueried)) {
            callback.onMusicsLoaded(musics);
            return;
        }

        this.getPendingCallbacks().add(callback);

        if (this.musicsQueried)
            return;

        this.musicsQueried = true;

        AsyncUtil.runAsync(() -> {
            queryMusics();
            notifyMusicsLoaded();
        });
    }

    private void notifyMusicsLoaded() {
        List<MusicsLoadedCallback> callbacks = this.getPendingCallbacks();

        if (callbacks.isEmpty())
            return;

        List<MusicsLoadedCallback> pending = List.copyOf(callbacks);
        List<Music> loaded = this.musics;
        callbacks.clear();
        pending.forEach(callback -> callback.onMusicsLoaded(loaded));
    }

    private void queryMusics() {
        try {
            List<Long> trackIds = CloudMusicApi.playlistTrackIds(id);

            this.musicsTotal = trackIds.size();
            this.musics.clear();

            for (int offset = 0; offset < trackIds.size(); offset += TRACK_BATCH_SIZE) {
                List<Long> batch = trackIds.subList(offset, Math.min(trackIds.size(), offset + TRACK_BATCH_SIZE));
                JsonArray songs = CloudMusicApi.songDetail(batch).toJsonObject().getAsJsonArray("songs");
                songs.forEach(element -> this.musics.add(JsonUtils.parse(element.getAsJsonObject(), Music.class)));
            }

            musicsLoaded = true;
        } catch (Exception e) {
            this.musicsQueried = false;
            e.printStackTrace();
        }
    }

    public interface MusicsLoadedCallback {
        void onMusicsLoaded(List<Music> musics);
    }

    public void updPlayCount() {
        CloudMusicApi.playlistUpdatePlaycount(this.id);
    }

    public void addToList(long musicId) {
        CloudMusicApi.playlistTracks("add", this.id, String.valueOf(musicId)).requireSuccessful("Add track to playlist");
    }

    public void removeFromList(long musicId) {
        CloudMusicApi.playlistTracks("del", this.id, String.valueOf(musicId)).requireSuccessful("Remove track from playlist");
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        PlayList playList = (PlayList) o;
        return id == playList.id;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
