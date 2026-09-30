package tritium.music.core.audio;

import java.util.Locale;

final class OnnxRuntimeFailure {

    private static final String[] LIBRARY_NAMES = {"onnxruntime", "libonnxruntime"};

    private static final String[] INITIALIZATION_FAILURES = {
            "initialization routine failed",
            "初始化例程失败"
    };

    private OnnxRuntimeFailure() {
    }

    static boolean matches(Throwable throwable) {
        Throwable cause = root(throwable);
        if (!(cause instanceof UnsatisfiedLinkError)) {
            return false;
        }
        String message = cause.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return containsAny(normalized, LIBRARY_NAMES) && containsAny(normalized, INITIALIZATION_FAILURES);
    }

    static String detail(Throwable throwable) {
        Throwable cause = root(throwable);
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return cause.toString();
        }
        int separator = message.indexOf(": ");
        if (separator <= 0) {
            return message;
        }
        String file = message.substring(0, separator);
        int slash = Math.max(file.lastIndexOf('\\'), file.lastIndexOf('/'));
        return (slash < 0 ? file : file.substring(slash + 1)) + message.substring(separator);
    }

    private static boolean containsAny(String text, String[] markers) {
        for (String marker : markers) {
            if (text.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static Throwable root(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}
