package tritium.music.core.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class StringUtil {

    public String returnEmptyStringIfNull(String input) {
        return input == null ? "" : input;
    }
}
