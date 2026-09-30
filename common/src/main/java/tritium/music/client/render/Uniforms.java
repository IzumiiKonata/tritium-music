package tritium.music.client.render;

import com.mojang.blaze3d.shaders.Uniform;
import net.minecraft.client.renderer.ShaderInstance;
import org.jspecify.annotations.Nullable;

public final class Uniforms {

    private Uniforms() {
    }

    public static @Nullable Uniform get(ShaderInstance shader, String name) {
        return shader == null ? null : shader.getUniform(name);
    }

    public static void set(ShaderInstance shader, String name, float value) {
        Uniform uniform = get(shader, name);
        if (uniform != null) {
            uniform.set(value);
        }
    }

    public static void set(ShaderInstance shader, String name, float x, float y) {
        Uniform uniform = get(shader, name);
        if (uniform != null) {
            uniform.set(x, y);
        }
    }

    public static void set(ShaderInstance shader, String name, float x, float y, float z, float w) {
        Uniform uniform = get(shader, name);
        if (uniform != null) {
            uniform.set(x, y, z, w);
        }
    }
}
