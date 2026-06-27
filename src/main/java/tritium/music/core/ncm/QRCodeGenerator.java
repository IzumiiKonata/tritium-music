package tritium.music.core.ncm;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import lombok.SneakyThrows;
import tritium.music.core.util.Textures;
import tritium.music.platform.TextureHandle;

import java.awt.image.BufferedImage;
import java.util.Hashtable;

public class QRCodeGenerator {

    public static final TextureHandle qrCode = TextureHandle.of("textures/qrcode.png");

    @SneakyThrows
    public static void generateAndLoadTexture(String address) {
        BufferedImage img = QRCodeGenerator.generateQRCode(address, 128, 128);
        Textures.loadTexture(qrCode, img);
    }

    /**
     * 根据内容，生成指定宽高、指定格式的二维码图片
     *
     * @param text   内容
     * @param width  宽
     * @param height 高
     * @return 生成的二维码图片
     */
    public static BufferedImage generateQRCode(String text, int width, int height) throws Exception {
        Hashtable<EncodeHintType, Object> hints = new Hashtable<>();
        hints.put(EncodeHintType.CHARACTER_SET, "utf-8");
        BitMatrix bitMatrix = new MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, width, height, hints);

        return toBufferedImage(bitMatrix);
    }

    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;

    public static BufferedImage toBufferedImage(BitMatrix matrix) {
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, matrix.get(x, y) ? BLACK : WHITE);
            }
        }
        return image;
    }
}
