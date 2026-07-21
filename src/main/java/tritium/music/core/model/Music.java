package tritium.music.core.model;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import lombok.Data;
import tritium.music.core.CloudMusic;
import tritium.music.core.ncm.api.CloudMusicApi;
import tritium.music.core.util.Pair;
import tritium.music.platform.TextureHandle;

import java.util.List;
import java.util.Objects;

@Data
public class Music {

    static final int STEREO = 8192;
    static final int INSTRUMENTAL = 131072;
    static final int DOLBY_ATMOS = 262144;
    static final int DIRTY = 1048576;
    static final long HIRES = 17179869184L;

    @SerializedName("name")
    private final String name;

    @SerializedName("mainTitle")
    private final String mainTitle;

    @SerializedName("additionalTitle")
    private final String additionalTitle;

    @SerializedName("id")
    private final long id;

    @SerializedName("ar")
    private final List<Artist> artists;

    @SerializedName("alia")
    private final List<String> aliasName;

    @SerializedName("al")
    private final Album album;

    @SerializedName("dt")
    private final long duration;

    @SerializedName("mark")
    private final long featureFlag;

    @SerializedName("publishTime")
    private final long publishTime;

    @SerializedName("tns")
    private final List<String> translatedName;

    private transient String artistsName, translatedNames;
    private transient TextureHandle coverLocation;
    private transient TextureHandle blurredCoverLocation;
    private transient TextureHandle smallCoverLocation;

    public final TextureHandle getCoverLocation() {
        if (coverLocation == null) {
            coverLocation = TextureHandle.of("textures/music/" + this.id + "/cover.png");
        }
        return coverLocation;
    }

    public final TextureHandle getBlurredCoverLocation() {
        if (blurredCoverLocation == null) {
            blurredCoverLocation = TextureHandle.of("textures/music/" + this.id + "/cover_blurred.png");
        }
        return blurredCoverLocation;
    }

    public final TextureHandle getSmallCoverLocation() {
        if (smallCoverLocation == null) {
            smallCoverLocation = TextureHandle.of("textures/music/" + this.id + "/cover_small.png");
        }
        return smallCoverLocation;
    }

    public String getArtistsName() {
        if (this.artistsName == null) {
            this.artistsName = this.buildArtistsNames();

            if (this.artistsName.isEmpty()) {
                this.artistsName = "Unknown";
            }
        }

        return this.artistsName;
    }

    public String getTranslatedNames() {
        if (this.translatedNames == null) {
            this.translatedNames = this.buildTranslatedNames();
        }

        return this.translatedNames;
    }

    private String buildTranslatedNames() {

        if (this.translatedName == null || this.translatedName.isEmpty())
            return "";

        return String.join(", ", this.translatedName);
    }

    private String buildArtistsNames() {
        List<String> artistsList = this.artists.stream().map(Artist::getName).toList();

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < artistsList.size(); i++) {
            String artistName = artistsList.get(i);

            if (i != artistsList.size() - 1) {
                sb.append(artistName).append(", ");
            } else {
                sb.append(artistName);
            }
        }

        return sb.toString();
    }

    public String getCoverUrl(int size) {
        return this.album.getPicUrl() + "?param=" + size + "y" + size;
    }

    /**
     * 更新歌曲播放次数
     * 这个方法目前会触发网易云风控, 不要使用
     */
    @Deprecated
    public void updPlayCount(PlayList pl, float sec) {
    }

    /**
     * 获得歌曲 url
     *
     * @return 歌曲文件 url 与类型
     */
    public Pair<String, String> getPlayUrl() {
        JsonObject result = CloudMusicApi.songUrlV1(this.id, CloudMusic.quality.getQuality().toLowerCase()).toJsonObject();
        JsonObject music = result.get("data").getAsJsonArray().get(0).getAsJsonObject();
        if (music.get("code").getAsInt() != 200) {
            return null;
        }

        String url = music.get("url").getAsString();

        String type = music.get("type").getAsString();

        if (type.isEmpty())
            type = "mp3";

        return Pair.of(url, type);
    }

    public void setLike(boolean like) {
        CloudMusicApi.like(this.id, like);
    }

    public boolean isInstrumental() {
        return (this.featureFlag & INSTRUMENTAL) != 0;
    }

    public boolean isDolbyAtmos() {
        return (this.featureFlag & DOLBY_ATMOS) != 0;
    }

    public boolean isDirty() {
        return (this.featureFlag & DIRTY) != 0;
    }

    public boolean isHiRes() {
        return (this.featureFlag & HIRES) != 0;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Music music = (Music) o;
        return id == music.id;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
