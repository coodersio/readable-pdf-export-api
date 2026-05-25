package io.cooders.readablepdf.dto;

import java.util.List;

public record ReadableTextNode(
        String id,
        String name,
        String characters,
        Boolean redrawable,
        String fallbackReason,
        PdfRect absoluteBoundingBox,
        List<List<Double>> absoluteTransform,
        String fontName,
        String fontFamily,
        String fontStyle,
        Object fontSize,
        String lineHeight,
        String letterSpacing,
        RgbaColor fillColor,
        Double opacity
) {
    public boolean isRedrawable() {
        return Boolean.TRUE.equals(redrawable);
    }

    public RgbaColor safeFillColor() {
        return fillColor == null ? RgbaColor.black() : fillColor;
    }

    public double safeOpacity() {
        if (fillColor != null) {
            return fillColor.alpha();
        }

        if (opacity == null) {
            return 1;
        }

        return Math.max(0, Math.min(1, opacity));
    }
}
