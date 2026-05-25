package io.cooders.readablepdf.service;

import io.cooders.readablepdf.dto.PreflightResponse;
import io.cooders.readablepdf.dto.ReadablePdfPage;
import io.cooders.readablepdf.dto.ReadablePdfRequest;
import io.cooders.readablepdf.dto.ReadableTextNode;
import org.springframework.stereotype.Service;

@Service
public class PreflightService {

    public PreflightResponse inspect(ReadablePdfRequest request) {
        int textNodes = 0;
        int redrawable = 0;
        int fallback = 0;

        for (ReadablePdfPage page : request.safePages()) {
            for (ReadableTextNode textNode : page.safeTextNodes()) {
                textNodes += 1;
                if (textNode.isRedrawable()) {
                    redrawable += 1;
                } else if (shouldFallback(textNode)) {
                    fallback += 1;
                }
            }
        }

        return new PreflightResponse(request.safePages().size(), textNodes, redrawable, fallback);
    }

    private boolean shouldFallback(ReadableTextNode textNode) {
        return textNode != null
                && !"hidden-or-empty".equals(textNode.fallbackReason())
                && !"decorative-placeholder".equals(textNode.fallbackReason());
    }
}
