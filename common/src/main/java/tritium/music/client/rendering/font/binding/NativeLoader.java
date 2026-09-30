package tritium.music.client.rendering.font.binding;

import org.lwjgl.system.APIUtil;
import org.lwjgl.system.Library;
import org.lwjgl.system.SharedLibrary;

import java.util.HashMap;
import java.util.Map;

final class NativeLoader {

    private final SharedLibrary library;
    private final Throwable failure;
    private final Map<String, Long> functions = new HashMap<>();

    NativeLoader(String module, String bundledName, String... names) {
        SharedLibrary loaded = null;
        Throwable error = null;

        try {
            loaded = Library.loadNative(NativeLoader.class, module, bundledName, true);
        } catch (Throwable throwable) {
            error = throwable;
        }

        if (loaded != null) {
            try {
                for (String name : names) {
                    functions.put(name, APIUtil.apiGetFunctionAddress(loaded, name));
                }
            } catch (Throwable throwable) {
                functions.clear();
                error = throwable;
            }
        }

        library = loaded;
        failure = error;
    }

    boolean available() {
        return library != null && failure == null;
    }

    long function(String name) {
        Long address = functions.get(name);
        if (address == null) {
            throw new IllegalStateException("Unresolved native function: " + name);
        }
        return address;
    }

    String status() {
        if (failure != null) {
            return failure.getClass().getSimpleName() + ": " + failure.getMessage();
        }
        return library == null ? "library not loaded" : library.getPath();
    }
}
