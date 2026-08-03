package tritium.music.core.lyric;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class TtmlLyricParser {
    private TtmlLyricParser() {
    }

    static List<LyricLine> parse(String input) {
        List<LyricLine> lines = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(input)));
            NodeList paragraphs = document.getElementsByTagNameNS("*", "p");
            for (int i = 0; i < paragraphs.getLength(); i++) {
                Element paragraph = (Element) paragraphs.item(i);
                LyricLine line = parseParagraph(paragraph);
                if (line != null) lines.add(line);
            }
        } catch (Exception ignored) {
            return List.of();
        }
        lines.sort(Comparator.comparingLong(LyricLine::getTimestamp));
        return lines;
    }

    private static LyricLine parseParagraph(Element paragraph) {
        long begin = time(attribute(paragraph, "begin"));
        long end = time(attribute(paragraph, "end"));
        List<LyricLine.Word> words = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        boolean seenWord = false;
        NodeList children = paragraph.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;
            if (!(node instanceof Element element)) continue;
            if (!localName(element).equals("span")) continue;
            String role = attribute(element, "role");
            if (role.equals("x-bg") || role.equals("x-translation") || hasTimedSpanChild(element)) continue;
            String value = directText(element);
            String spanBegin = attribute(element, "begin");
            String spanEnd = attribute(element, "end");
            if (value.isEmpty() || spanBegin.isEmpty() || spanEnd.isEmpty()) continue;
            long wordBegin = time(spanBegin);
            long wordEnd = time(spanEnd);
            String spaced = seenWord && hasLeadingWhitespace(node) ? " " + value : value;
            words.add(new LyricLine.Word(spaced, wordBegin, Math.max(0, wordEnd - wordBegin)));
            text.append(spaced);
            seenWord = true;
        }
        String lyric = text.isEmpty() ? normalizedText(paragraph) : text.toString();
        if (lyric.isBlank()) return null;
        LyricLine line = new LyricLine(begin, lyric);
        line.duration = Math.max(0, end - begin);
        line.words.addAll(words);
        String translation = translation(paragraph);
        if (!translation.isBlank()) line.translationText = translation;
        return line;
    }

    private static boolean hasLeadingWhitespace(Node node) {
        Node prev = node.getPreviousSibling();
        if (prev != null && prev.getNodeType() == Node.TEXT_NODE) {
            String text = prev.getNodeValue();
            return text != null && !text.isEmpty() && text.trim().isEmpty();
        }
        return false;
    }

    private static String translation(Element paragraph) {
        for (Element span : descendants(paragraph, "span")) {
            if (attribute(span, "role").equals("x-translation")) {
                return normalizedText(span);
            }
        }
        return "";
    }

    private static boolean hasTimedSpanChild(Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element childElement && localName(childElement).equals("span")
                    && !attribute(childElement, "begin").isEmpty()) return true;
        }
        return false;
    }

    private static List<Element> descendants(Element parent, String name) {
        NodeList nodes = parent.getElementsByTagNameNS("*", name);
        List<Element> elements = new ArrayList<>(nodes.getLength());
        for (int i = 0; i < nodes.getLength(); i++) elements.add((Element) nodes.item(i));
        return elements;
    }

    private static String directText(Element element) {
        StringBuilder result = new StringBuilder();
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                result.append(child.getNodeValue());
            }
        }
        return result.toString();
    }

    private static String normalizedText(Element element) {
        return element.getTextContent().replaceAll("\\s+", " ").trim();
    }

    private static String localName(Element element) {
        return element.getLocalName() == null ? element.getTagName() : element.getLocalName();
    }

    private static String attribute(Element element, String name) {
        if (element.hasAttribute(name)) return element.getAttribute(name);
        for (int i = 0; i < element.getAttributes().getLength(); i++) {
            Node attribute = element.getAttributes().item(i);
            if (name.equals(attribute.getLocalName())) return attribute.getNodeValue();
        }
        return "";
    }

    private static long time(String value) {
        if (value == null || value.isBlank()) return 0;
        try {
            if (value.endsWith("ms")) return Math.round(Double.parseDouble(value.substring(0, value.length() - 2)));
            if (value.endsWith("s"))
                return Math.round(Double.parseDouble(value.substring(0, value.length() - 1)) * 1000);
            String[] parts = value.split(":");
            double seconds;
            if (parts.length == 3)
                seconds = Double.parseDouble(parts[0]) * 3600 + Double.parseDouble(parts[1]) * 60 + Double.parseDouble(parts[2]);
            else if (parts.length == 2) seconds = Double.parseDouble(parts[0]) * 60 + Double.parseDouble(parts[1]);
            else seconds = Double.parseDouble(parts[0]);
            return Math.round(seconds * 1000);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
