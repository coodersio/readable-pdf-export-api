package io.cooders.readablepdf.dto;

import java.util.List;

public record ReadablePdfPage(
        String frameId,
        String frameName,
        double width,
        double height,
        PdfRect absoluteBoundingBox,
        String svgTextLayer,
        List<ReadableTextNode> textNodes
) {
    public List<ReadableTextNode> safeTextNodes() {
        return textNodes == null ? List.of() : textNodes;
    }
}
