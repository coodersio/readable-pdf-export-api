package io.cooders.readablepdf.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pdf")
public class DocumentSourceController {

    @Value("${readable-pdf.source}")
    private String source;

    @Value("${readable-pdf.version}")
    private String version;

    @Value("${readable-pdf.commit}")
    private String commit;

    @Value("${readable-pdf.tag}")
    private String tag;

    @Value("${readable-pdf.image-digest}")
    private String imageDigest;

    @Value("${readable-pdf.license}")
    private String license;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("service", "readable-pdf-api");
        response.put("status", "ok");
        response.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/source")
    public ResponseEntity<Map<String, Object>> source() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("source", source);
        response.put("license", license);
        response.put("commit", commit);
        response.put("tag", tag);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/version")
    public ResponseEntity<Map<String, Object>> version() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("service", "readable-pdf-api");
        response.put("version", version);
        response.put("commit", commit);
        response.put("tag", tag);
        response.put("source", source);
        response.put("license", license);
        response.put("imageDigest", imageDigest);
        return ResponseEntity.ok(response);
    }
}

