package tritium.music.core.ncm;

import lombok.experimental.UtilityClass;

@UtilityClass
public class OptionsUtil {

    private String COOKIE = "";

    public void setCookie(String cookie) {
        COOKIE = cookie;
    }

    public static String getCookie() {
        return COOKIE;
    }

    public RequestUtil.RequestOptions createOptions() {
        return createOptions("");
    }

    public RequestUtil.RequestOptions createOptions(String crypto) {
        return RequestUtil.RequestOptions.builder()
                .crypto(crypto)
                .cookie(COOKIE)
                .ua("")
                .proxy("")
                .encryptedResponse(null)
                .build();
    }
}
