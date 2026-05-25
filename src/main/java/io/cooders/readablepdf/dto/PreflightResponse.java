package io.cooders.readablepdf.dto;

public record PreflightResponse(
        int pages,
        int textNodes,
        int redrawableTextNodes,
        int fallbackTextNodes
) {
}

