package io.cooders.readablepdf.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import io.cooders.readablepdf.dto.ExportOptions;
import io.cooders.readablepdf.dto.ExportResult;
import io.cooders.readablepdf.dto.PdfRect;
import io.cooders.readablepdf.dto.ReadablePdfPage;
import io.cooders.readablepdf.dto.ReadablePdfRequest;
import io.cooders.readablepdf.dto.ReadableTextNode;
import io.cooders.readablepdf.dto.RgbaColor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ReadablePdfExportServiceTest {

    @Test
    void mergesBackgroundPdfAndAddsReadableLayer() throws Exception {
        ReadablePdfExportService exportService = new ReadablePdfExportService(
                10,
                new PreflightService(),
                new TextLayerService(new SvgTextLayerParser(), new FontResolverService(""))
        );
        byte[] backgroundPdf = blankPdf();
        String svg = """
                <svg width="200" height="100" xmlns="http://www.w3.org/2000/svg">
                  <text x="20" y="30" font-size="12" fill="#111111">Hello PDF</text>
                </svg>
                """;
        ReadableTextNode textNode = new ReadableTextNode(
                "1:2",
                "Title",
                "Hello PDF",
                true,
                null,
                new PdfRect(20, 20, 80, 14),
                List.of(List.of(1d, 0d, 20d), List.of(0d, 1d, 20d)),
                "Inter Regular",
                "Inter",
                "Regular",
                12,
                "AUTO",
                "0",
                new RgbaColor(0, 0, 0, 1),
                1d
        );
        ReadablePdfRequest request = new ReadablePdfRequest(
                "Fixture",
                "REAL_TEXT_BASIC",
                new ExportOptions(true, true),
                List.of(new ReadablePdfPage("1:1", "Page 1", 200, 100, new PdfRect(0, 0, 200, 100), svg, List.of(textNode)))
        );

        ExportResult result = exportService.export(
                request,
                List.of(new MockMultipartFile("pagePdfs", "page_1.pdf", "application/pdf", backgroundPdf))
        );

        assertThat(result.pdfBytes()).isNotEmpty();
        assertThat(result.report().pages()).isEqualTo(1);
        assertThat(result.report().redrawableTextNodes()).isEqualTo(1);

        try (PdfDocument output = new PdfDocument(new PdfReader(new ByteArrayInputStream(result.pdfBytes())))) {
            assertThat(output.getNumberOfPages()).isEqualTo(1);
            assertThat(output.getOutlines(false).getAllChildren()).hasSize(1);
        }
    }

    private byte[] blankPdf() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PdfDocument document = new PdfDocument(new PdfWriter(output))) {
            document.addNewPage();
        }
        return output.toByteArray();
    }
}
