package io.cooders.readablepdf.dto;

import java.util.List;

public record ReadablePdfRequest(
        String title,
        String mode,
        ExportOptions options,
        List<ReadablePdfPage> pages
) {
    public List<ReadablePdfPage> safePages() {
        return pages == null ? List.of() : pages;
    }

    public ExportOptions safeOptions() {
        return options == null ? new ExportOptions(true, true) : options;
    }
}

