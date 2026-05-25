package io.cooders.readablepdf.service;

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FontResolverService {

    private final String configuredUnicodeFont;
    private final Map<PdfDocument, PdfFont> unicodeFontCache = new WeakHashMap<>();
    private final Map<PdfDocument, PdfFont> latinFontCache = new WeakHashMap<>();
    private final Map<PdfDocument, Map<String, PdfFont>> namedFontCache = new WeakHashMap<>();
    private static boolean systemFontsRegistered;

    public FontResolverService(
            @Value("${readable-pdf.fonts.unicode-fallback:}") String configuredUnicodeFont
    ) {
        this.configuredUnicodeFont = configuredUnicodeFont;
        registerSystemFontsOnce();
    }

    public PdfFont resolve(PdfDocument pdfDocument, String text) throws IOException {
        return resolve(pdfDocument, text, null, null, null);
    }

    public PdfFont resolve(
            PdfDocument pdfDocument,
            String text,
            String fontName,
            String fontFamily,
            String fontStyle
    ) throws IOException {
        PdfFont namedFont = namedFont(pdfDocument, text, fontName, fontFamily, fontStyle);
        if (namedFont != null) {
            return namedFont;
        }

        if (requiresUnicodeFont(text)) {
            return unicodeFont(pdfDocument);
        }

        return standardFontForStyle(pdfDocument, fontName, fontFamily, fontStyle);
    }

    private synchronized PdfFont namedFont(
            PdfDocument pdfDocument,
            String text,
            String fontName,
            String fontFamily,
            String fontStyle
    ) {
        List<String> candidates = fontCandidates(fontName, fontFamily, fontStyle);
        if (candidates.isEmpty()) {
            return null;
        }

        Map<String, PdfFont> documentCache = namedFontCache.computeIfAbsent(pdfDocument, ignored -> new java.util.HashMap<>());
        for (String candidate : candidates) {
            PdfFont cached = documentCache.get(candidate);
            if (cached != null && supportsText(cached, text)) {
                return cached;
            }

            try {
                PdfFont font = PdfFontFactory.createRegisteredFont(
                        candidate,
                        PdfEncodings.IDENTITY_H,
                        PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED
                );
                if (font != null && supportsText(font, text)) {
                    documentCache.put(candidate, font);
                    return font;
                }
            } catch (IOException | RuntimeException ignored) {
                // Try the next registered name. Figma exposes family/style names,
                // while OS font registries vary by platform and installed fonts.
            }
        }

        return null;
    }

    private synchronized PdfFont unicodeFont(PdfDocument pdfDocument) throws IOException {
        PdfFont cached = unicodeFontCache.get(pdfDocument);
        if (cached != null) {
            return cached;
        }

        for (String fontPath : unicodeFontCandidates()) {
            if (fontPath == null || fontPath.isBlank()) {
                continue;
            }

            File fontFile = new File(fontPath);
            if (!fontFile.isFile()) {
                continue;
            }

            try {
                PdfFont font = createEmbeddedFont(fontPath, pdfDocument);
                unicodeFontCache.put(pdfDocument, font);
                return font;
            } catch (IOException | RuntimeException ignored) {
                // Try the next known fallback path. Font availability differs across local,
                // container, and production environments.
            }
        }

        PdfFont fallback = latinFont(pdfDocument);
        unicodeFontCache.put(pdfDocument, fallback);
        return fallback;
    }

    private synchronized PdfFont latinFont(PdfDocument pdfDocument) throws IOException {
        PdfFont cached = latinFontCache.get(pdfDocument);
        if (cached != null) {
            return cached;
        }

        PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        latinFontCache.put(pdfDocument, font);
        return font;
    }

    private synchronized PdfFont standardFontForStyle(
            PdfDocument pdfDocument,
            String fontName,
            String fontFamily,
            String fontStyle
    ) throws IOException {
        if (containsIgnoreCase(fontFamily, "courier") || containsIgnoreCase(fontName, "courier")) {
            if (isBold(fontName, fontStyle)) {
                return standardFont(pdfDocument, isItalic(fontName, fontStyle)
                        ? StandardFonts.COURIER_BOLDOBLIQUE
                        : StandardFonts.COURIER_BOLD);
            }
            return standardFont(pdfDocument, isItalic(fontName, fontStyle)
                    ? StandardFonts.COURIER_OBLIQUE
                    : StandardFonts.COURIER);
        }

        if (containsIgnoreCase(fontFamily, "times") || containsIgnoreCase(fontName, "times")) {
            if (isBold(fontName, fontStyle)) {
                return standardFont(pdfDocument, isItalic(fontName, fontStyle)
                        ? StandardFonts.TIMES_BOLDITALIC
                        : StandardFonts.TIMES_BOLD);
            }
            return standardFont(pdfDocument, isItalic(fontName, fontStyle)
                    ? StandardFonts.TIMES_ITALIC
                    : StandardFonts.TIMES_ROMAN);
        }

        if (isBold(fontName, fontStyle)) {
            return standardFont(pdfDocument, isItalic(fontName, fontStyle)
                    ? StandardFonts.HELVETICA_BOLDOBLIQUE
                    : StandardFonts.HELVETICA_BOLD);
        }

        if (isItalic(fontName, fontStyle)) {
            return standardFont(pdfDocument, StandardFonts.HELVETICA_OBLIQUE);
        }

        return latinFont(pdfDocument);
    }

    private PdfFont standardFont(PdfDocument pdfDocument, String standardFontName) throws IOException {
        String cacheKey = "standard:" + standardFontName;
        Map<String, PdfFont> documentCache = namedFontCache.computeIfAbsent(pdfDocument, ignored -> new java.util.HashMap<>());
        PdfFont cached = documentCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        PdfFont font = PdfFontFactory.createFont(standardFontName);
        documentCache.put(cacheKey, font);
        return font;
    }

    private PdfFont createEmbeddedFont(String fontPath, PdfDocument pdfDocument) throws IOException {
        if (fontPath.toLowerCase().endsWith(".ttc")) {
            return PdfFontFactory.createTtcFont(
                    fontPath,
                    0,
                    PdfEncodings.IDENTITY_H,
                    PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED,
                    true
            );
        }

        return PdfFontFactory.createFont(
                fontPath,
                PdfEncodings.IDENTITY_H,
                PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED,
                pdfDocument
        );
    }

    private List<String> unicodeFontCandidates() {
        return List.of(
                configuredUnicodeFont,
                "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
                "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
                "/usr/share/fonts/truetype/noto/NotoSansCJKsc-Regular.otf",
                "/Library/Fonts/NotoSansCJK-Regular.ttc",
                "/Library/Fonts/Noto Sans CJK SC Regular.otf",
                "/System/Library/Fonts/Hiragino Sans GB.ttc",
                "/System/Library/Fonts/STHeiti Medium.ttc",
                "/System/Library/Fonts/AppleSDGothicNeo.ttc"
        );
    }

    private List<String> fontCandidates(String fontName, String fontFamily, String fontStyle) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        addCandidate(candidates, fontName);
        addCandidate(candidates, fontFamily);

        if (isUsable(fontFamily) && isUsable(fontStyle)) {
            addCandidate(candidates, fontFamily + " " + fontStyle);
            addCandidate(candidates, fontFamily + "-" + fontStyle);
            addCandidate(candidates, fontFamily + fontStyle);
        }

        Set<String> registeredFonts = PdfFontFactory.getRegisteredFonts();
        Set<String> registeredFamilies = PdfFontFactory.getRegisteredFamilies();
        List<String> expanded = new ArrayList<>();

        for (String candidate : candidates) {
            expanded.add(candidate);
            expanded.add(candidate.toLowerCase());
            expanded.add(candidate.replace(" ", "-"));
            expanded.add(candidate.replace(" ", ""));

            String normalizedCandidate = normalizeFontKey(candidate);
            for (String registered : registeredFonts) {
                if (normalizeFontKey(registered).equals(normalizedCandidate)) {
                    expanded.add(registered);
                }
            }
            for (String registeredFamily : registeredFamilies) {
                if (normalizeFontKey(registeredFamily).equals(normalizedCandidate)) {
                    expanded.add(registeredFamily);
                }
            }
        }

        LinkedHashSet<String> deduped = new LinkedHashSet<>();
        for (String candidate : expanded) {
            if (isUsable(candidate)) {
                deduped.add(candidate);
            }
        }
        return List.copyOf(deduped);
    }

    private void addCandidate(LinkedHashSet<String> candidates, String value) {
        if (isUsable(value)) {
            candidates.add(value.trim());
        }
    }

    private boolean isUsable(String value) {
        return value != null && !value.isBlank() && !"mixed".equalsIgnoreCase(value.trim());
    }

    private boolean supportsText(PdfFont font, String text) {
        if (font == null || text == null || text.isBlank()) {
            return font != null;
        }

        return text.codePoints()
                .filter(codePoint -> !Character.isWhitespace(codePoint))
                .allMatch(font::containsGlyph);
    }

    private boolean isBold(String fontName, String fontStyle) {
        return containsIgnoreCase(fontName, "bold")
                || containsIgnoreCase(fontStyle, "bold")
                || containsIgnoreCase(fontStyle, "semibold")
                || containsIgnoreCase(fontStyle, "demibold")
                || containsIgnoreCase(fontStyle, "black");
    }

    private boolean isItalic(String fontName, String fontStyle) {
        return containsIgnoreCase(fontName, "italic")
                || containsIgnoreCase(fontName, "oblique")
                || containsIgnoreCase(fontStyle, "italic")
                || containsIgnoreCase(fontStyle, "oblique");
    }

    private boolean containsIgnoreCase(String value, String needle) {
        return value != null && value.toLowerCase().contains(needle);
    }

    private String normalizeFontKey(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("[^a-z0-9]+", "");
    }

    private static synchronized void registerSystemFontsOnce() {
        if (systemFontsRegistered) {
            return;
        }

        try {
            PdfFontFactory.registerSystemDirectories();
        } catch (RuntimeException ignored) {
            // Font registration is a best-effort style fidelity upgrade.
        }
        systemFontsRegistered = true;
    }

    private boolean requiresUnicodeFont(String text) {
        if (text == null) {
            return false;
        }

        return text.codePoints().anyMatch(codePoint -> codePoint > 0x00FF);
    }
}
