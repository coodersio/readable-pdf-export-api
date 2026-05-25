package io.cooders.readablepdf.dto;

public record ExportOptions(Boolean bookmarks, Boolean report) {

    public boolean includeBookmarks() {
        return Boolean.TRUE.equals(bookmarks);
    }

    public boolean includeReport() {
        return Boolean.TRUE.equals(report);
    }
}

