package tritium.music.core.model;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import tritium.music.core.ncm.api.CloudMusicApi;
import tritium.music.core.util.JsonUtils;
import tritium.music.platform.TextureHandle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 用户对象
 */
public record User(@SerializedName("userId") long id, @SerializedName("nickname") String name,
                   @SerializedName("signature") String signature, @SerializedName("vipType") int vip,
                   @SerializedName("avatarUrl") String avatarUrl) {

    public final TextureHandle getAvatarLocation() {
        return TextureHandle.of("textures/user/" + this.id + "/avatar.png");
    }

    /**
     * 用户歌单
     *
     * @return 歌单列表
     */
    public List<PlayList> playLists(int page, int limit) {
        if (limit == 0) {
            limit = 30;
        }

        JsonObject data = CloudMusicApi.userPlaylist(this.id, limit, limit * page).toJsonObject();

        List<PlayList> playLists = new ArrayList<>();
        data.get("playlist").getAsJsonArray().forEach(playList -> {
            PlayList parse = JsonUtils.parse(playList.getAsJsonObject(), PlayList.class);
            playLists.add(parse);
        });

        return playLists;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return id == user.id;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
