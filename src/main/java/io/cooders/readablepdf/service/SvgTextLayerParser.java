package io.cooders.readablepdf.service;

import io.cooders.readablepdf.dto.RgbaColor;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

@Service
public class SvgTextLayerParser {

    private static final Pattern TRANSFORM_PATTERN = Pattern.compile("([a-zA-Z]+)\\(([^)]*)\\)");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?");

    public List<SvgTextItem> parse(String svgTextLayer) {
        if (svgTextLayer == null || svgTextLayer.isBlank()) {
            return List.of();
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            Document document = factory.newDocumentBuilder()
                    .parse(new InputSource(new StringReader(svgTextLayer)));
            List<SvgTextItem> items = new ArrayList<>();
            traverse(document.getDocumentElement(), SvgMatrix.identity(), SvgStyle.defaults(), items);
            return items;
        } catch (Exception exception) {
            return List.of();
        }
    }

    private void traverse(Node node, SvgMatrix parentMatrix, SvgStyle inheritedStyle, List<SvgTextItem> items) {
        if (node.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }

        Element element = (Element) node;

        if (isTag(element, "text")) {
            parseTextElement(element, parentMatrix, inheritedStyle, items);
            return;
        }

        SvgMatrix matrix = parentMatrix.multiply(parseTransform(attribute(element, "transform")));
        SvgStyle style = inheritedStyle.merge(element);

        NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index += 1) {
            traverse(children.item(index), matrix, style, items);
        }
    }

    private void parseTextElement(
            Element textElement,
            SvgMatrix parentMatrix,
            SvgStyle inheritedStyle,
            List<SvgTextItem> items
    ) {
        SvgMatrix matrix = parentMatrix.multiply(parseTransform(attribute(textElement, "transform")));
        SvgStyle style = inheritedStyle.merge(textElement);
        double x = firstNumber(attribute(textElement, "x")).orElse(0d);
        double y = firstNumber(attribute(textElement, "y")).orElse(0d);
        collectTextRuns(textElement, matrix, style, x, y, items);
    }

    private void collectTextRuns(
            Element element,
            SvgMatrix matrix,
            SvgStyle style,
            double inheritedX,
            double inheritedY,
            List<SvgTextItem> items
    ) {
        String directText = directText(element);
        if (!directText.isBlank()) {
            addTextItem(directText, inheritedX, inheritedY, matrix, style, items);
        }

        NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index += 1) {
            Node child = children.item(index);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element childElement = (Element) child;
            if (!isTag(childElement, "tspan") && !isTag(childElement, "text")) {
                continue;
            }

            SvgMatrix childMatrix = matrix.multiply(parseTransform(attribute(childElement, "transform")));
            SvgStyle childStyle = style.merge(childElement);
            double childX = firstNumber(attribute(childElement, "x")).orElse(inheritedX);
            double childY = firstNumber(attribute(childElement, "y")).orElse(inheritedY);
            childX += firstNumber(attribute(childElement, "dx")).orElse(0d);
            childY += firstNumber(attribute(childElement, "dy")).orElse(0d);
            collectTextRuns(childElement, childMatrix, childStyle, childX, childY, items);
        }
    }

    private void addTextItem(
            String rawText,
            double x,
            double y,
            SvgMatrix matrix,
            SvgStyle style,
            List<SvgTextItem> items
    ) {
        String text = rawText.replace("\n", "").replace("\r", "");
        if (text.isBlank()) {
            return;
        }

        SvgPoint point = matrix.apply(x, y);
        items.add(new SvgTextItem(
                text,
                point.x(),
                point.y(),
                style.fontSize(),
                style.letterSpacing(),
                style.fillColor(),
                style.opacity(),
                matrix.a(),
                matrix.b(),
                matrix.c(),
                matrix.d()
        ));
    }

    private SvgMatrix parseTransform(String transform) {
        if (transform == null || transform.isBlank()) {
            return SvgMatrix.identity();
        }

        SvgMatrix matrix = SvgMatrix.identity();
        Matcher matcher = TRANSFORM_PATTERN.matcher(transform);
        while (matcher.find()) {
            String command = matcher.group(1).toLowerCase(Locale.ROOT);
            List<Double> values = numbers(matcher.group(2));
            matrix = matrix.multiply(transformFor(command, values));
        }

        return matrix;
    }

    private SvgMatrix transformFor(String command, List<Double> values) {
        if ("matrix".equals(command) && values.size() >= 6) {
            return new SvgMatrix(values.get(0), values.get(1), values.get(2), values.get(3), values.get(4), values.get(5));
        }

        if ("translate".equals(command) && !values.isEmpty()) {
            return SvgMatrix.translate(values.get(0), values.size() > 1 ? values.get(1) : 0);
        }

        if ("scale".equals(command) && !values.isEmpty()) {
            return SvgMatrix.scale(values.get(0), values.size() > 1 ? values.get(1) : values.get(0));
        }

        if ("rotate".equals(command) && !values.isEmpty()) {
            double angle = Math.toRadians(values.get(0));
            if (values.size() >= 3) {
                double cx = values.get(1);
                double cy = values.get(2);
                return SvgMatrix.translate(cx, cy)
                        .multiply(SvgMatrix.rotate(angle))
                        .multiply(SvgMatrix.translate(-cx, -cy));
            }
            return SvgMatrix.rotate(angle);
        }

        return SvgMatrix.identity();
    }

    private static boolean isTag(Element element, String tagName) {
        return element.getTagName().equalsIgnoreCase(tagName)
                || element.getTagName().toLowerCase(Locale.ROOT).endsWith(":" + tagName);
    }

    private static String directText(Element element) {
        StringBuilder builder = new StringBuilder();
        NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index += 1) {
            Node child = children.item(index);
            if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                builder.append(child.getNodeValue());
            }
        }
        return builder.toString();
    }

    private static String attribute(Element element, String name) {
        return element.hasAttribute(name) ? element.getAttribute(name) : "";
    }

    private static Optional<Double> firstNumber(String value) {
        List<Double> numbers = numbers(value);
        return numbers.isEmpty() ? Optional.empty() : Optional.of(numbers.get(0));
    }

    private static List<Double> numbers(String value) {
        List<Double> numbers = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return numbers;
        }

        Matcher matcher = NUMBER_PATTERN.matcher(value);
        while (matcher.find()) {
            numbers.add(Double.parseDouble(matcher.group()));
        }
        return numbers;
    }

    public record SvgTextItem(
            String text,
            double x,
            double y,
            Double fontSize,
            Double letterSpacing,
            RgbaColor fillColor,
            double opacity,
            double matrixA,
            double matrixB,
            double matrixC,
            double matrixD
    ) {
        public double horizontalScale() {
            return Math.max(0.0001, Math.hypot(matrixA, matrixB));
        }

        public double verticalScale() {
            return Math.max(0.0001, Math.hypot(matrixC, matrixD));
        }

        public double normalizedA() {
            return matrixA / horizontalScale();
        }

        public double normalizedB() {
            return matrixB / horizontalScale();
        }

        public double normalizedC() {
            return matrixC / verticalScale();
        }

        public double normalizedD() {
            return matrixD / verticalScale();
        }
    }

    private record SvgPoint(double x, double y) {
    }

    private record SvgMatrix(double a, double b, double c, double d, double e, double f) {

        static SvgMatrix identity() {
            return new SvgMatrix(1, 0, 0, 1, 0, 0);
        }

        static SvgMatrix translate(double x, double y) {
            return new SvgMatrix(1, 0, 0, 1, x, y);
        }

        static SvgMatrix scale(double x, double y) {
            return new SvgMatrix(x, 0, 0, y, 0, 0);
        }

        static SvgMatrix rotate(double radians) {
            double cos = Math.cos(radians);
            double sin = Math.sin(radians);
            return new SvgMatrix(cos, sin, -sin, cos, 0, 0);
        }

        SvgMatrix multiply(SvgMatrix other) {
            return new SvgMatrix(
                    a * other.a + c * other.b,
                    b * other.a + d * other.b,
                    a * other.c + c * other.d,
                    b * other.c + d * other.d,
                    a * other.e + c * other.f + e,
                    b * other.e + d * other.f + f
            );
        }

        SvgPoint apply(double x, double y) {
            return new SvgPoint(a * x + c * y + e, b * x + d * y + f);
        }
    }

    private record SvgStyle(Double fontSize, Double letterSpacing, RgbaColor fillColor, double opacity) {

        static SvgStyle defaults() {
            return new SvgStyle(12d, null, RgbaColor.black(), 1);
        }

        SvgStyle merge(Element element) {
            Double nextFontSize = cssNumber(element, "font-size").orElse(fontSize);
            Double nextLetterSpacing = cssNumber(element, "letter-spacing").orElse(letterSpacing);
            RgbaColor nextFillColor = cssColor(element, "fill").orElse(fillColor);
            double nextOpacity = opacity
                    * cssNumber(element, "opacity").orElse(1d)
                    * cssNumber(element, "fill-opacity").orElse(1d);
            return new SvgStyle(nextFontSize, nextLetterSpacing, nextFillColor, Math.max(0, Math.min(1, nextOpacity)));
        }

        private static Optional<Double> cssNumber(Element element, String key) {
            String value = cssValue(element, key);
            return firstNumber(value);
        }

        private static Optional<RgbaColor> cssColor(Element element, String key) {
            String value = cssValue(element, key).trim();
            if (value.isBlank() || "none".equalsIgnoreCase(value) || "currentColor".equalsIgnoreCase(value)) {
                return Optional.empty();
            }

            if (value.startsWith("#")) {
                return parseHexColor(value);
            }

            if (value.toLowerCase(Locale.ROOT).startsWith("rgb")) {
                List<Double> values = numbers(value);
                if (values.size() >= 3) {
                    double alpha = values.size() >= 4 ? values.get(3) : 1;
                    return Optional.of(new RgbaColor(values.get(0) / 255d, values.get(1) / 255d, values.get(2) / 255d, alpha));
                }
            }

            return Optional.empty();
        }

        private static Optional<RgbaColor> parseHexColor(String value) {
            String hex = value.substring(1).trim();
            if (hex.length() == 3) {
                int r = Integer.parseInt(hex.substring(0, 1) + hex.substring(0, 1), 16);
                int g = Integer.parseInt(hex.substring(1, 2) + hex.substring(1, 2), 16);
                int b = Integer.parseInt(hex.substring(2, 3) + hex.substring(2, 3), 16);
                return Optional.of(new RgbaColor(r / 255d, g / 255d, b / 255d, 1));
            }

            if (hex.length() >= 6) {
                int r = Integer.parseInt(hex.substring(0, 2), 16);
                int g = Integer.parseInt(hex.substring(2, 4), 16);
                int b = Integer.parseInt(hex.substring(4, 6), 16);
                return Optional.of(new RgbaColor(r / 255d, g / 255d, b / 255d, 1));
            }

            return Optional.empty();
        }

        private static String cssValue(Element element, String key) {
            String fromStyle = styleProperty(element.getAttribute("style"), key);
            if (!fromStyle.isBlank()) {
                return fromStyle;
            }

            return element.hasAttribute(key) ? element.getAttribute(key) : "";
        }

        private static String styleProperty(String style, String key) {
            if (style == null || style.isBlank()) {
                return "";
            }

            String[] declarations = style.split(";");
            for (String declaration : declarations) {
                String[] pair = declaration.split(":", 2);
                if (pair.length == 2 && pair[0].trim().equalsIgnoreCase(key)) {
                    return pair[1].trim();
                }
            }
            return "";
        }
    }
}
