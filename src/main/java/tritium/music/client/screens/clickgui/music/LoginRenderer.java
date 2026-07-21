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
import tritium.music.fabric.ui.Identifiers;
import tritium.music.platform.Platform;
import tritium.music.platform.TextureHandle;

import static tritium.music.client.screens.ncm.NCMScreen.ColorType.PRIMARY_TEXT;

public class LoginRenderer implements SharedRenderingConstants {

    @Getter
    public volatile boolean closing = false;
    Thread loginThread;
    volatile boolean success = false;
    volatile boolean failed = false;
    float screeMaskAlpha = 0;
    float qrAlpha = 0;
    float avatarAlpha = 0f;
    double scale = .975;

    private static final String LOGIN_PROMPT = "使用网易云音乐 App 扫描二维码登录";

    public boolean avatarLoaded = false;
    public TextureHandle scannedAvatar = TextureHandle.of("textures/tempavatar.png");
    public String scannedUserName = "";

    public LoginRenderer() {
        loginThread = new Thread(() -> {
            try {
                String cookie = CloudMusic.qrCodeLogin();
                if (cookie == null || cookie.isBlank()) {
                    failed = true;
                    return;
                }
                OptionsUtil.setCookie(cookie);
                success = true;
                this.closing = true;
            } catch (Throwable ignored) {
                failed = true;
            }
        }, "Tritium Music QR Login");

        loginThread.setDaemon(true);
        loginThread.start();
    }

    public void render(double mouseX, double mouseY, double posX, double posY, double width, double height, float alpha) {
        screeMaskAlpha = Interpolations.interpolate(screeMaskAlpha, this.isClosing() ? 0 : 1, 0.35f);
        scale = Interpolations.interpolate(scale, this.isClosing() ? .985 : 1, .38f);
        float visibleAlpha = alpha * screeMaskAlpha;

        Rect.draw(posX, posY, width, height, RGBA.black(visibleAlpha * .48f));

        RenderContext.graphics().pose().pushMatrix();
        RenderSystem.translateAndScale(posX + width / 2.0, posY + height / 2.0, scale);

        double cardWidth = Math.min(340, width - 56);
        double cardHeight = Math.min(304, height - 48);
        double cardX = posX + (width - cardWidth) * .5;
        double cardY = posY + (height - cardHeight) * .5;
        double radius = 6;

//        roundedRect(cardX - 5, cardY - 5, cardWidth + 10, cardHeight + 10, radius + 3,
//                0, 0, 0, visibleAlpha * .14f);
        roundedRect(cardX, cardY, cardWidth, cardHeight, radius,
                22, 23, 27, Math.round(visibleAlpha * 252));

        double centerX = cardX + cardWidth * .5;
        double titleY = cardY + 27;
        FontManager.pf20bold.drawCenteredString("扫码登录", centerX, titleY,
                reAlpha(NCMScreen.getColor(PRIMARY_TEXT), visibleAlpha));

        double promptY = titleY + FontManager.pf20bold.getHeight() + 9;
        String[] promptLines = FontManager.pf14.fitWidth(LOGIN_PROMPT, cardWidth - 40);
        for (String line : promptLines) {
            FontManager.pf14.drawCenteredString(line, centerX, promptY,
                    reAlpha(NCMScreen.getColor(NCMScreen.ColorType.SECONDARY_TEXT), visibleAlpha * .82f));
            promptY += FontManager.pf14.getHeight() + 2;
        }

        renderQrArea(width, height, centerX, cardY + height * .2, visibleAlpha);

        RenderContext.graphics().pose().popMatrix();
    }

    private void renderQrArea(double width, double height, double centerX, double qrFrameY, float alpha) {
        double qrFrameSize = Math.min(width, height) * .3;
        double qrFrameX = centerX - qrFrameSize * .5;
        double qrSize = qrFrameSize * .88;
        double qrX = qrFrameX + (qrFrameSize - qrSize) * .5;
        double qrY = qrFrameY + (qrFrameSize - qrSize) * .5;

        roundedRect(qrFrameX, qrFrameY, qrFrameSize, qrFrameSize, 4,
                247, 247, 248, Math.round(alpha * 255));

        boolean qrReady = Platform.hasTexture(QRCodeGenerator.qrCode);
        qrAlpha = Interpolations.interpolate(qrAlpha, qrReady ? 1 : 0, .3f);
        if (qrReady) {
            Image.draw(QRCodeGenerator.qrCode, qrX, qrY, qrSize, qrSize, Image.Type.NoColor, alpha * qrAlpha);
        } else {
            renderLoading(qrFrameX + qrFrameSize * .5, qrFrameY + qrFrameSize * .5, alpha);
        }

        String status;
        if (success) {
            status = "登录成功";
        } else if (failed) {
            status = "登录服务暂时不可用";
        } else if (qrReady) {

            if (avatarLoaded) {
                status = "请在设备上点击登录";
            } else {
                status = "等待扫码确认";
            }

        } else {
            status = "正在生成二维码";
        }

        double statusWidth = FontManager.pf12.getStringWidthD(status);
        double statusX = centerX - (statusWidth) * .5;
        double statusY = qrFrameY + qrFrameSize + 18;
        FontManager.pf12.drawString(status, statusX, statusY,
                reAlpha(NCMScreen.getColor(NCMScreen.ColorType.SECONDARY_TEXT), alpha * .82f));

        if (avatarLoaded) {
            if (Platform.hasTexture(scannedAvatar)) {

                this.avatarAlpha = Interpolations.interpolate(avatarAlpha, 1f, .2f);

                double avatarSize = qrSize * .5;
                double avatarX = qrX + (qrSize - avatarSize) * .5;
                double avatarY = qrY + (qrSize - avatarSize) * .5;
                Rect.draw(qrX, qrY, qrSize, qrSize, RGBA.color(0, 0, 0, 100), Rect.RectType.EXPAND);
                RenderSystem.bindTexture(Identifiers.of(scannedAvatar));
                roundedRectTextured(avatarX, avatarY, avatarSize, avatarSize, 4, alpha * avatarAlpha);
                FontManager.pf12.drawCenteredString(scannedUserName, centerX,
                        avatarY + avatarSize + 3, reAlpha(NCMScreen.getColor(PRIMARY_TEXT), alpha * avatarAlpha * .86f));
            }
        } else {
            this.avatarAlpha = Interpolations.interpolate(avatarAlpha, 0f, .2f);
        }
    }

    private void renderLoading(double centerX, double centerY, float alpha) {
        double phase = (System.currentTimeMillis() % 1200L) / 1200.0;
        for (int i = 0; i < 3; i++) {
            double wave = .35 + .65 * (.5 + .5 * Math.sin((phase - i * .16) * Math.PI * 2));
            double size = 5 + wave * 2;
            roundedRect(centerX + (i - 1) * 15 - size * .5, centerY - size * .5,
                    size, size, size * .5, 130, 27, 40, Math.round(alpha * wave * 255));
        }
    }

    public boolean canClose() {
        return this.isClosing() && this.screeMaskAlpha <= 0.05 && success;
    }
}
