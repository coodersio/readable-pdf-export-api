package io.cooders.readablepdf.dto;

public record ExportResult(byte[] pdfBytes, String fileName, ExportReport report) {
}

