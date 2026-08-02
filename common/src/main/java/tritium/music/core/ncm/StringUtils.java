package tritium.music.core.ncm;

import lombok.experimental.UtilityClass;

@UtilityClass
public class StringUtils {

    public boolean isBlank(CharSequence cs) {
        int strLen;
        if (cs != null && (strLen = cs.length()) != 0) {
            for (int i = 0; i < strLen; ++i) {
                if (!Character.isWhitespace(cs.charAt(i))) {
                    return false;
                }
            }

            return true;
        } else {
            return true;
        }
    }

    public boolean isNotBlank(CharSequence cs) {
        return !isBlank(cs);
    }
}
