package tritium.music.core.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Objects;

/**
 * @author IzumiiKonata
 * Date: 2025/11/7 22:13
 */
public record Artist(@SerializedName("id") long id, @SerializedName("name") String name,
                     @SerializedName("tns") List<String> translatedName,
                     @SerializedName("alias") List<String> aliasName) {

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Artist artist = (Artist) o;
        return id == artist.id;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
