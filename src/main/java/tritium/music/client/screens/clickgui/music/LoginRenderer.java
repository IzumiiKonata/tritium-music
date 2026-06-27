package tritium.music.client.screens.clickgui.music;

import lombok.Getter;
import tritium.music.client.render.RenderContext;
import tritium.music.client.rendering.Image;
import tritium.music.client.rendering.RGBA;
import tritium.music.client.rendering.Rect;
import tritium.music.client.rendering.RenderSystem;
import tritium.music.client.rendering.SharedRenderingConstants;
import tritium.music.client.rendering.animation.Interpolations;
import tritium.music.client.rendering.font.FontManager;
import tritium.music.client.screens.ncm.NCMScreen;
import tritium.music.core.CloudMusic;
import tritium.music.core.ncm.OptionsUtil;
import tritium.music.core.ncm.QRCodeGenerator;
import tritium.music.platform.Platform;
import tritium.music.platform.TextureHandle;

import static tritium.music.client.screens.ncm.NCMScreen.ColorType.PRIMARY_TEXT;

public class LoginRenderer implements SharedRenderingConstants {

    @Getter
    public boolean closing = false;
    Thread loginThread;
    boolean success = false;
    float screeMaskAlpha = 0;
    double scale = 1;

    private static final String LOGIN_PROMPT = "使用网易云音乐 App 扫描二维码登录";

    public boolean avatarLoaded = false;
    public TextureHandle tempAvatar = TextureHandle.of("textures/tempavatar.png");
    public String tempUsername = "";

    public LoginRenderer() {
        loginThread = new Thread(() -> {
            String cookie = CloudMusic.qrCodeLogin();
            OptionsUtil.setCookie(cookie);
            success = true;
            this.closing = true;
        });

        loginThread.start();
    }

    public void render(double mouseX, double mouseY, double posX, double posY, double width, double height, float alpha) {
        screeMaskAlpha = Interpolations.interpolate(screeMaskAlpha * 255, this.isClosing() ? 0 : 120, 0.3f) * RGBA.DIVIDE_BY_255;

        RenderContext.graphics().pose().pushMatrix();
        RenderSystem.translateAndScale(posX + width / 2.0, posY + height / 2.0, scale);

        double pWidth = width / 2.0;
        double pHeight = height / 1.2;

        double qWidth = 96, qHeight = 96;

        String[] strings = FontManager.pf20.fitWidth(LOGIN_PROMPT, width - 24);

        double startY = posY + height / 6.0;

        int textColor = reAlpha(NCMScreen.getColor(PRIMARY_TEXT), alpha);

        for (String string : strings) {
            FontManager.pf20.drawCenteredString(string, posX + width / 2.0, startY, textColor);
            startY += FontManager.pf20.getHeight();
        }

        if (Platform.hasTexture(QRCodeGenerator.qrCode)) {
            Image.draw(QRCodeGenerator.qrCode, posX + width / 2.0 - qWidth / 2.0, posY + height / 6.0 * 4.0 - qHeight / 2.0, qWidth, qHeight, Image.Type.NoColor);
        } else {
            Rect.draw(posX + width / 2.0 - qWidth / 2.0, posY + height / 6.0 * 4.0 - qHeight / 2.0, qWidth, qHeight, hexColor(128, 128, 128, (int) (alpha * 255)));
        }

        if (avatarLoaded) {
            FontManager.pf25bold.drawCenteredString(tempUsername, posX + width / 2.0, posY + height / 4.0, textColor);

            if (Platform.hasTexture(tempAvatar)) {
                double size = 48;
                Image.draw(tempAvatar, posX + width / 2.0 - size / 2.0, posY + height / 3.2, size, size, Image.Type.NoColor);
            }
        }

        RenderContext.graphics().pose().popMatrix();
    }

    public boolean canClose() {
        return this.isClosing() && this.screeMaskAlpha <= 0.05 && success;
    }
}
