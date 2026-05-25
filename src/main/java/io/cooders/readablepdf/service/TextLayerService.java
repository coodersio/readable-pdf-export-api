package io.cooders.readablepdf.service;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.canvas.PdfCanvasConstants;
import com.itextpdf.kernel.pdf.extgstate.PdfExtGState;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import io.cooders.readablepdf.dto.PdfRect;
import io.cooders.readablepdf.dto.ReadableTextNode;
import io.cooders.readablepdf.dto.ReadablePdfPage;
import io.cooders.readablepdf.dto.RgbaColor;
import io.cooders.readablepdf.service.SvgTextLayerParser.SvgTextItem;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class TextLayerService {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?");

    private final SvgTextLayerParser svgTextLayerParser;
    private final FontResolverService fontResolverService;

    public TextLayerService(
            SvgTextLayerParser svgTextLayerParser,
            FontResolverService fontResolverService
    ) {
        this.svgTextLayerParser = svgTextLayerParser;
        this.fontResolverService = fontResolverService;
    }

    public void addReadableTextLayer(
            PdfDocument pdfDocument,
            PdfPage pdfPage,
            ReadablePdfPage pageMetadata
    ) throws IOException {
        PdfCanvas canvas = new PdfCanvas(
                pdfPage.newContentStreamAfter(),
                pdfPage.getResources(),
                pdfDocument
        );
        Rectangle pageSize = pdfPage.getPageSize();
        Scale scale = Scale.from(pageSize, pageMetadata);
        SvgTextMatcher matcher = new SvgTextMatcher(svgTextLayerParser.parse(pageMetadata.svgTextLayer()));

        for (ReadableTextNode textNode : pageMetadata.safeTextNodes()) {
            if (shouldSkip(textNode)) {
                continue;
            }

            List<SvgTextItem> matchedSvgItems = matcher.take(textNode.characters());
            if (textNode.isRedrawable()) {
                drawVisibleTextNode(pdfDocument, canvas, pageSize, scale, pageMetadata, textNode, matchedSvgItems);
            } else {
                drawInvisibleFallback(pdfDocument, canvas, pageSize, scale, pageMetadata, textNode, matchedSvgItems);
            }
        }
    }

    private void drawVisibleTextNode(
            PdfDocument pdfDocument,
            PdfCanvas canvas,
            Rectangle pageSize,
            Scale scale,
            ReadablePdfPage pageMetadata,
            ReadableTextNode textNode,
            List<SvgTextItem> svgItems
    ) throws IOException {
        if (svgItems.isEmpty()) {
            drawBoxEstimatedText(pdfDocument, canvas, pageSize, scale, pageMetadata, textNode, false);
            return;
        }

        for (SvgTextItem svgItem : svgItems) {
            drawText(
                    pdfDocument,
                    canvas,
                    pageSize,
                    scale,
                    sanitize(svgItem.text()),
                    svgItem.x(),
                    svgItem.y(),
                    firstNonNull(svgItem.fontSize(), fontSize(textNode)) * svgItem.verticalScale(),
                    firstNonNull(svgItem.letterSpacing(), letterSpacing(textNode, firstNonNull(svgItem.fontSize(), fontSize(textNode)))) * svgItem.horizontalScale(),
                    colorFor(textNode, svgItem),
                    opacityFor(textNode, svgItem),
                    false,
                    textNode,
                    svgItem
            );
        }
    }

    private void drawInvisibleFallback(
            PdfDocument pdfDocument,
            PdfCanvas canvas,
            Rectangle pageSize,
            Scale scale,
            ReadablePdfPage pageMetadata,
            ReadableTextNode textNode,
            List<SvgTextItem> svgItems
    ) throws IOException {
        if (!svgItems.isEmpty()) {
            for (SvgTextItem svgItem : svgItems) {
                drawText(
                        pdfDocument,
                        canvas,
                        pageSize,
                        scale,
                        sanitize(svgItem.text()),
                        svgItem.x(),
                        svgItem.y(),
                        firstNonNull(svgItem.fontSize(), fontSize(textNode)) * svgItem.verticalScale(),
                        firstNonNull(svgItem.letterSpacing(), letterSpacing(textNode, firstNonNull(svgItem.fontSize(), fontSize(textNode)))) * svgItem.horizontalScale(),
                        textNode.safeFillColor(),
                        textNode.safeOpacity(),
                        true,
                        textNode,
                        svgItem
                );
            }
            return;
        }

        drawBoxEstimatedText(pdfDocument, canvas, pageSize, scale, pageMetadata, textNode, true);
    }

    private void drawBoxEstimatedText(
            PdfDocument pdfDocument,
            PdfCanvas canvas,
            Rectangle pageSize,
            Scale scale,
            ReadablePdfPage pageMetadata,
            ReadableTextNode textNode,
            boolean invisible
    ) throws IOException {
        PdfRect box = textNode.absoluteBoundingBox();
        double fontSize = fontSize(textNode);
        double lineHeight = lineHeight(textNode, fontSize);
        SvgTextItem textTransform = textNodeTransformItem(textNode);
        double x = 0;
        double y = fontSize;

        if (box != null) {
            double frameX = pageMetadata != null && pageMetadata.absoluteBoundingBox() != null
                    ? pageMetadata.absoluteBoundingBox().x()
                    : 0;
            double frameY = pageMetadata != null && pageMetadata.absoluteBoundingBox() != null
                    ? pageMetadata.absoluteBoundingBox().y()
                    : 0;
            x = box.x() - frameX;
            y = box.y() - frameY + fontSize * 0.82 * textTransform.verticalScale();
        }

        String[] lines = textNode.characters().split("\\R", -1);
        for (int index = 0; index < lines.length; index += 1) {
            String line = sanitize(lines[index]);
            if (line.isBlank()) {
                continue;
            }

            drawText(
                    pdfDocument,
                    canvas,
                    pageSize,
                    scale,
                    line,
                    x,
                    y + index * lineHeight * textTransform.verticalScale(),
                    fontSize * textTransform.verticalScale(),
                    letterSpacing(textNode, fontSize) * textTransform.horizontalScale(),
                    textNode.safeFillColor(),
                    textNode.safeOpacity(),
                    invisible,
                    textNode,
                    textTransform
            );
        }
    }

    private void drawText(
            PdfDocument pdfDocument,
            PdfCanvas canvas,
            Rectangle pageSize,
            Scale scale,
            String text,
            double figmaX,
            double figmaBaselineY,
            double fontSizePx,
            double letterSpacingPx,
            RgbaColor fillColor,
            double opacity,
            boolean invisible,
            ReadableTextNode textNode,
            SvgTextItem svgItem
    ) throws IOException {
        if (text.isBlank()) {
            return;
        }

        PdfFont font = fontResolverService.resolve(
                pdfDocument,
                text,
                textNode == null ? null : textNode.fontName(),
                textNode == null ? null : textNode.fontFamily(),
                textNode == null ? null : textNode.fontStyle()
        );
        float pdfX = (float) (figmaX * scale.x());
        float pdfY = (float) (pageSize.getHeight() - figmaBaselineY * scale.y());
        float pdfFontSize = (float) Math.max(1, fontSizePx * scale.y());
        float characterSpacing = (float) (letterSpacingPx * scale.x());
        TextMatrix textMatrix = TextMatrix.from(svgItem);

        canvas.saveState();
        if (!invisible) {
            canvas.setFillColorRgb(fillColor.red(), fillColor.green(), fillColor.blue());
            if (opacity < 0.999) {
                canvas.setExtGState(new PdfExtGState().setFillOpacity((float) Math.max(0, Math.min(1, opacity))));
            }
        }
        canvas.beginText();
        canvas.setFontAndSize(font, pdfFontSize);
        canvas.setCharacterSpacing(text.length() > 1 ? characterSpacing : 0);
        canvas.setHorizontalScaling((float) textMatrix.horizontalScaling());
        canvas.setTextRenderingMode(invisible
                ? PdfCanvasConstants.TextRenderingMode.INVISIBLE
                : PdfCanvasConstants.TextRenderingMode.FILL);
        canvas.setTextMatrix(
                (float) textMatrix.a(),
                (float) textMatrix.b(),
                (float) textMatrix.c(),
                (float) textMatrix.d(),
                pdfX,
                pdfY
        );
        canvas.showText(text);
        canvas.endText();
        canvas.restoreState();
    }

    private boolean shouldSkip(ReadableTextNode textNode) {
        return textNode == null
                || textNode.characters() == null
                || textNode.characters().isBlank()
                || "hidden-or-empty".equals(textNode.fallbackReason())
                || "decorative-placeholder".equals(textNode.fallbackReason());
    }

    private RgbaColor colorFor(ReadableTextNode textNode, SvgTextItem svgItem) {
        if (textNode.fillColor() != null) {
            return textNode.safeFillColor();
        }

        return svgItem.fillColor() == null ? RgbaColor.black() : svgItem.fillColor();
    }

    private double opacityFor(ReadableTextNode textNode, SvgTextItem svgItem) {
        if (textNode.fillColor() != null || textNode.opacity() != null) {
            return textNode.safeOpacity();
        }

        return svgItem.opacity();
    }

    private SvgTextItem textNodeTransformItem(ReadableTextNode textNode) {
        return new SvgTextItem(
                "",
                0,
                0,
                null,
                null,
                RgbaColor.black(),
                1,
                transformValue(textNode, 0, 0, 1),
                transformValue(textNode, 1, 0, 0),
                transformValue(textNode, 0, 1, 0),
                transformValue(textNode, 1, 1, 1)
        );
    }

    private double transformValue(ReadableTextNode textNode, int row, int column, double fallback) {
        if (textNode.absoluteTransform() == null
                || textNode.absoluteTransform().size() <= row
                || textNode.absoluteTransform().get(row) == null
                || textNode.absoluteTransform().get(row).size() <= column
                || textNode.absoluteTransform().get(row).get(column) == null) {
            return fallback;
        }

        return textNode.absoluteTransform().get(row).get(column);
    }

    private double fontSize(ReadableTextNode textNode) {
        Object value = textNode.fontSize();
        if (value instanceof Number number) {
            return Math.max(1, number.doubleValue());
        }

        if (value instanceof String stringValue) {
            return firstNumber(stringValue, 12);
        }

        return 12;
    }

    private double lineHeight(ReadableTextNode textNode, double fontSize) {
        if (textNode.lineHeight() == null || textNode.lineHeight().isBlank() || "mixed".equals(textNode.lineHeight())) {
            return fontSize * 1.2;
        }

        return Math.max(fontSize, firstNumber(textNode.lineHeight(), fontSize * 1.2));
    }

    private double letterSpacing(ReadableTextNode textNode, double fontSize) {
        if (textNode.letterSpacing() == null
                || textNode.letterSpacing().isBlank()
                || "mixed".equalsIgnoreCase(textNode.letterSpacing())
                || "AUTO".equalsIgnoreCase(textNode.letterSpacing())) {
            return 0;
        }

        String letterSpacing = textNode.letterSpacing();
        double value = firstNumber(letterSpacing, 0);
        if (letterSpacing.toUpperCase().contains("PERCENT")) {
            return fontSize * value / 100d;
        }

        return value;
    }

    private double firstNumber(String value, double fallback) {
        Matcher matcher = NUMBER_PATTERN.matcher(value);
        if (matcher.find()) {
            return Double.parseDouble(matcher.group());
        }
        return fallback;
    }

    private String sanitize(String text) {
        return text
                .replace('\t', ' ')
                .replaceAll("[\\p{Cntrl}&&[^\n\r]]", "");
    }

    private double firstNonNull(Double value, double fallback) {
        return value == null ? fallback : value;
    }

    private record Scale(double x, double y) {
        static Scale from(Rectangle pageSize, ReadablePdfPage pageMetadata) {
            double scaleX = pageMetadata.width() == 0 ? 1 : pageSize.getWidth() / pageMetadata.width();
            double scaleY = pageMetadata.height() == 0 ? 1 : pageSize.getHeight() / pageMetadata.height();
            return new Scale(scaleX, scaleY);
        }
    }

    private record TextMatrix(double a, double b, double c, double d, double horizontalScaling) {
        static TextMatrix from(SvgTextItem svgItem) {
            if (svgItem == null) {
                return new TextMatrix(1, 0, 0, 1, 100);
            }

            double horizontalScale = svgItem.horizontalScale();
            double verticalScale = svgItem.verticalScale();
            double horizontalScaling = Math.max(1, horizontalScale / verticalScale * 100d);

            return new TextMatrix(
                    svgItem.normalizedA(),
                    -svgItem.normalizedB(),
                    -svgItem.normalizedC(),
                    svgItem.normalizedD(),
                    horizontalScaling
            );
        }
    }

    private static class SvgTextMatcher {
        private final List<SvgTextItem> items;
        private final boolean[] consumed;
        private int cursor;

        SvgTextMatcher(List<SvgTextItem> items) {
            this.items = items;
            this.consumed = new boolean[items.size()];
            this.cursor = 0;
        }

        List<SvgTextItem> take(String wantedText) {
            String wanted = normalize(wantedText);
            if (wanted.isBlank()) {
                return List.of();
            }

            List<SvgTextItem> fromCursor = findMatch(wanted, cursor);
            if (!fromCursor.isEmpty()) {
                return fromCursor;
            }

            return findMatch(wanted, 0);
        }

        private List<SvgTextItem> findMatch(String wanted, int startIndex) {
            for (int start = startIndex; start < items.size(); start += 1) {
                if (consumed[start] || normalize(items.get(start).text()).isBlank()) {
                    continue;
                }

                List<Integer> candidateIndexes = new ArrayList<>();
                StringBuilder combined = new StringBuilder();

                for (int index = start; index < items.size(); index += 1) {
                    if (consumed[index]) {
                        break;
                    }

                    String normalizedItem = normalize(items.get(index).text());
                    if (normalizedItem.isBlank()) {
                        continue;
                    }

                    combined.append(normalizedItem);
                    candidateIndexes.add(index);

                    String combinedText = combined.toString();
                    if (wanted.equals(combinedText)) {
                        return consume(candidateIndexes);
                    }

                    if (!wanted.startsWith(combinedText) || combinedText.length() > wanted.length() * 2) {
                        break;
                    }
                }
            }

            return List.of();
        }

        private List<SvgTextItem> consume(List<Integer> indexes) {
            List<SvgTextItem> matchedItems = new ArrayList<>();
            for (Integer index : indexes) {
                consumed[index] = true;
                matchedItems.add(items.get(index));
                cursor = Math.max(cursor, index + 1);
            }
            return matchedItems;
        }

        private static String normalize(String value) {
            if (value == null) {
                return "";
            }

            return value.replace('\u00a0', ' ').replaceAll("\\s+", "");
        }
    }
}
