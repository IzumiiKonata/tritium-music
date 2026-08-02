package tritium.music.core.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.experimental.UtilityClass;

import java.io.Reader;

@UtilityClass
public class JsonUtils {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public JsonObject toJsonObject(String json) {
        return gson.fromJson(json, JsonObject.class);
    }

    public JsonObject toJsonObject(Reader reader) {
        return gson.fromJson(reader, JsonObject.class);
    }

    public JsonObject toJsonObject(JsonElement element) {
        return gson.fromJson(element, JsonObject.class);
    }

    public JsonArray toJsonArray(String json) {
        return gson.fromJson(json, JsonArray.class);
    }

    public JsonArray toJsonArray(Reader reader) {
        return gson.fromJson(reader, JsonArray.class);
    }

    public JsonArray toJsonArray(JsonElement element) {
        return gson.fromJson(element, JsonArray.class);
    }

    public <T> T parse(String json, Class<? extends T> typeClass) {
        return gson.fromJson(json, typeClass);
    }

    public <T> T parse(Reader reader, Class<? extends T> typeClass) {
        return gson.fromJson(reader, typeClass);
    }

    public <T> T parse(JsonElement element, Class<? extends T> typeClass) {
        return gson.fromJson(element, typeClass);
    }

    public String toJsonString(JsonElement element) {
        return gson.toJson(element);
    }

    public String toJsonString(Object src) {
        return gson.toJson(src);
    }
}
