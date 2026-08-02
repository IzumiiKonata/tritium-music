package tritium.music.core.model;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import tritium.music.platform.TextureHandle;

import java.util.List;
import java.util.Objects;

/**
 * @author IzumiiKonata
 * Date: 2025/11/7 22:16
 */
@Data
public class Album {

    @SerializedName("id")
    private final long id;
    @SerializedName("name")
    private final String name;
    @SerializedName("picUrl")
    private final String picUrl;
    @SerializedName("tns")
    private final List<String> translatedName;
    private transient TextureHandle coverLocation;

    public final TextureHandle getCoverLocation() {
        if (coverLocation == null) {
            coverLocation = TextureHandle.of("textures/album/" + this.id + "/cover.png");
        }
        return coverLocation;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Album album = (Album) o;
        return id == album.id;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
