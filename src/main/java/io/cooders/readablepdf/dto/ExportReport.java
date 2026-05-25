package io.cooders.readablepdf.dto;

public record ExportReport(
        int pages,
        int textNodes,
        int redrawableTextNodes,
        int fallbackTextNodes
) {
}

