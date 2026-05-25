package io.cooders.readablepdf.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfOutline;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.navigation.PdfExplicitDestination;
import io.cooders.readablepdf.dto.ExportReport;
import io.cooders.readablepdf.dto.ExportResult;
import io.cooders.readablepdf.dto.PreflightResponse;
import io.cooders.readablepdf.dto.ReadablePdfPage;
import io.cooders.readablepdf.dto.ReadablePdfRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ReadablePdfExportService {

    private final int maxPages;
    private final PreflightService preflightService;
    private final TextLayerService textLayerService;

    public ReadablePdfExportService(
            @Value("${readable-pdf.max-pages}") int maxPages,
            PreflightService preflightService,
            TextLayerService textLayerService
    ) {
        this.maxPages = maxPages;
        this.preflightService = preflightService;
        this.textLayerService = textLayerService;
    }

    public ExportResult export(ReadablePdfRequest request, List<MultipartFile> pagePdfs) throws IOException {
        validateRequest(request, pagePdfs);

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (PdfDocument target = new PdfDocument(new PdfWriter(output))) {
            for (int pageIndex = 0; pageIndex < request.safePages().size(); pageIndex += 1) {
                ReadablePdfPage pageMetadata = request.safePages().get(pageIndex);
                MultipartFile pagePdf = pagePdfs.get(pageIndex);
                int targetPageCountBeforeCopy = target.getNumberOfPages();

                try (PdfDocument source = new PdfDocument(new PdfReader(pagePdf.getInputStream()))) {
                    source.copyPagesTo(1, source.getNumberOfPages(), target);
                }

                for (int copiedPageNumber = targetPageCountBeforeCopy + 1;
                     copiedPageNumber <= target.getNumberOfPages();
                     copiedPageNumber += 1) {
                    textLayerService.addReadableTextLayer(target, target.getPage(copiedPageNumber), pageMetadata);
                }

                if (request.safeOptions().includeBookmarks()) {
                    addBookmark(target, pageMetadata, targetPageCountBeforeCopy + 1);
                }
            }

            target.getDocumentInfo()
                    .setTitle(defaultString(request.title(), "PDF Document"))
                    .setCreator("PDF API")
                    .setProducer("readable-pdf-api");
        }

        PreflightResponse preflight = preflightService.inspect(request);
        ExportReport report = new ExportReport(
                preflight.pages(),
                preflight.textNodes(),
                preflight.redrawableTextNodes(),
                preflight.fallbackTextNodes()
        );

        return new ExportResult(output.toByteArray(), buildFileName(request.title()), report);
    }

    private void validateRequest(ReadablePdfRequest request, List<MultipartFile> pagePdfs) {
        if (request == null || request.safePages().isEmpty()) {
            throw new IllegalArgumentException("metadata.pages is required.");
        }

        if (!"REAL_TEXT_BASIC".equals(request.mode())) {
            throw new IllegalArgumentException("Only REAL_TEXT_BASIC mode is supported in V1.");
        }

        if (request.safePages().size() > maxPages) {
            throw new IllegalArgumentException("Too many pages. Maximum is " + maxPages + ".");
        }

        if (pagePdfs == null || pagePdfs.size() != request.safePages().size()) {
            throw new IllegalArgumentException("pagePdfs must match metadata.pages length.");
        }

        for (MultipartFile pagePdf : pagePdfs) {
            if (pagePdf.isEmpty()) {
                throw new IllegalArgumentException("Uploaded page PDF is empty.");
            }
        }
    }

    private String buildFileName(String title) {
        String baseName = defaultString(title, "document")
                .toLowerCase()
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("(^-+|-+$)", "");

        if (baseName.isBlank()) {
            baseName = "document";
        }

        return baseName + ".pdf";
    }

    private void addBookmark(PdfDocument target, ReadablePdfPage pageMetadata, int pageNumber) {
        PdfOutline root = target.getOutlines(false);
        PdfOutline outline = root.addOutline(defaultString(pageMetadata.frameName(), "Page " + pageNumber));
        outline.addDestination(PdfExplicitDestination.createFit(target.getPage(pageNumber)));
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
