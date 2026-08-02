package tritium.music.core.lyric.provider;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.zip.InflaterInputStream;

final class QqQrcCodec {
    private static final byte[] KEY = "!@#)(*$%123ZXC!@!@#)(NHL".getBytes(StandardCharsets.US_ASCII);
    private static final Map<String, String> FIELDS = Map.of(
            "content", "orig",
            "contentts", "translation",
            "contentroma", "romanization"
    );

    private QqQrcCodec() {
    }

    static Parts decodeResponse(String response) throws Exception {
        Document document = parse(response.replace("<!--", "").replace("-->", ""));
        String original = "";
        String translation = "";
        String romanization = "";
        for (Map.Entry<String, String> field : FIELDS.entrySet()) {
            String decoded = decodeElement(document, field.getKey());
            if (field.getValue().equals("orig")) original = decoded;
            if (field.getValue().equals("translation")) translation = decoded;
            if (field.getValue().equals("romanization")) romanization = decoded;
        }
        return new Parts(original, translation, romanization);
    }

    private static String decodeElement(Document document, String name) throws Exception {
        NodeList elements = document.getElementsByTagName(name);
        if (elements.getLength() == 0) return "";
        String encrypted = elements.item(0).getTextContent().trim();
        if (encrypted.isBlank()) return "";
        String decoded = isHex(encrypted) ? decrypt(encrypted) : encrypted;
        if (!decoded.contains("<?xml") && !decoded.contains("<QrcInfos")) return decoded;
        Document nested = parse(decoded);
        NodeList lyrics = nested.getElementsByTagName("Lyric_1");
        if (lyrics.getLength() == 0) return decoded;
        Element lyric = (Element) lyrics.item(0);
        return lyric.hasAttribute("LyricContent") ? lyric.getAttribute("LyricContent") : lyric.getTextContent();
    }

    static String decrypt(String encrypted) throws Exception {
        byte[] bytes = HexFormat.of().parseHex(encrypted);
        if (bytes.length % 8 != 0) throw new IllegalArgumentException("QRC ciphertext length is not aligned");
        byte[] compressed = QqDesCompat.decrypt(bytes, KEY);
        try (InflaterInputStream inflater = new InflaterInputStream(new ByteArrayInputStream(compressed));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            try {
                inflater.transferTo(output);
            } catch (IOException e) {
                throw new IOException("QRC inflate failed, decrypted prefix="
                        + HexFormat.of().formatHex(compressed, 0, Math.min(8, compressed.length)), e);
            }
            return output.toString(StandardCharsets.UTF_8);
        }
    }

    private static boolean isHex(String value) {
        return !value.isBlank() && value.length() % 2 == 0 && value.matches("[0-9a-fA-F]+");
    }

    private static Document parse(String xml) throws Exception {
        xml = xml.replaceAll("<[A-Za-z_][A-Za-z0-9_.:-]*=\"[^\"]*\"\\s*/>", "");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    record Parts(String original, String translation, String romanization) {
    }
}
