package io.cooders.readablepdf.controller;

import io.cooders.readablepdf.dto.ExportResult;
import io.cooders.readablepdf.dto.PreflightResponse;
import io.cooders.readablepdf.dto.ReadablePdfRequest;
import io.cooders.readablepdf.service.PreflightService;
import io.cooders.readablepdf.service.ReadablePdfExportService;
import java.io.IOException;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/pdf/readable")
public class ReadablePdfController {

    private final ReadablePdfExportService exportService;
    private final PreflightService preflightService;

    public ReadablePdfController(
            ReadablePdfExportService exportService,
            PreflightService preflightService
    ) {
        this.exportService = exportService;
        this.preflightService = preflightService;
    }

    @PostMapping("/preflight")
    public ResponseEntity<PreflightResponse> preflight(@RequestBody ReadablePdfRequest request) {
        return ResponseEntity.ok(preflightService.inspect(request));
    }

    @PostMapping(
            value = "/export",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_PDF_VALUE
    )
    public ResponseEntity<byte[]> export(
            @RequestPart("metadata") ReadablePdfRequest request,
            @RequestPart("pagePdfs") List<MultipartFile> pagePdfs
    ) throws IOException {
        ExportResult result = exportService.export(request, pagePdfs);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(result.fileName()).build().toString()
                )
                .header("X-Readable-Pdf-Pages", String.valueOf(result.report().pages()))
                .header("X-Readable-Pdf-Text-Nodes", String.valueOf(result.report().textNodes()))
                .header("X-Readable-Pdf-Redrawable", String.valueOf(result.report().redrawableTextNodes()))
                .header("X-Readable-Pdf-Fallback", String.valueOf(result.report().fallbackTextNodes()))
                .body(result.pdfBytes());
    }
}

