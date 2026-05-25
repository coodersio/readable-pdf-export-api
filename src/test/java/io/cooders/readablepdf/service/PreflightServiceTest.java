package io.cooders.readablepdf.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.cooders.readablepdf.dto.PreflightResponse;
import io.cooders.readablepdf.dto.ReadablePdfPage;
import io.cooders.readablepdf.dto.ReadablePdfRequest;
import io.cooders.readablepdf.dto.ReadableTextNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class PreflightServiceTest {

    private final PreflightService preflightService = new PreflightService();

    @Test
    void doesNotCountDecorativePlaceholdersAsFallback() {
        ReadableTextNode decorative = new ReadableTextNode(
                "decorative",
                "Dotted line",
                "................................",
                false,
                "decorative-placeholder",
                null,
                List.of(),
                "Inter Regular",
                "Inter",
                "Regular",
                12,
                "AUTO",
                "0",
                null,
                1d
        );
        ReadableTextNode fallback = new ReadableTextNode(
                "complex",
                "Shadow heading",
                "Complex text",
                false,
                "effects",
                null,
                List.of(),
                "Inter Regular",
                "Inter",
                "Regular",
                12,
                "AUTO",
                "0",
                null,
                1d
        );
        ReadablePdfRequest request = new ReadablePdfRequest(
                "Fixture",
                "REAL_TEXT_BASIC",
                null,
                List.of(new ReadablePdfPage("frame", "Frame", 100, 100, null, "", List.of(decorative, fallback)))
        );

        PreflightResponse response = preflightService.inspect(request);

        assertThat(response.textNodes()).isEqualTo(2);
        assertThat(response.redrawableTextNodes()).isZero();
        assertThat(response.fallbackTextNodes()).isEqualTo(1);
    }
}
