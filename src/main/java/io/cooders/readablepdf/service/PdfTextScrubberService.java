package io.cooders.readablepdf.service;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfStream;
import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class PdfTextScrubberService {

    public void removeTextObjects(PdfPage page) {
        if (page == null) {
            return;
        }

        for (int index = 0; index < page.getContentStreamCount(); index += 1) {
            scrubStream(page.getContentStream(index));
        }

        Set<PdfObject> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        scrubResources(page.getResources().getPdfObject(), visited);
    }

    private void scrubResources(PdfDictionary resources, Set<PdfObject> visited) {
        if (resources == null) {
            return;
        }

        PdfDictionary xObjects = resources.getAsDictionary(PdfName.XObject);
        if (xObjects == null) {
            return;
        }

        for (PdfName name : xObjects.keySet()) {
            PdfObject xObject = xObjects.get(name);
            if (!(xObject instanceof PdfStream stream) || !visited.add(stream)) {
                continue;
            }

            if (PdfName.Form.equals(stream.getAsName(PdfName.Subtype))) {
                scrubStream(stream);
                scrubResources(stream.getAsDictionary(PdfName.Resources), visited);
            }
        }
    }

    private void scrubStream(PdfStream stream) {
        if (stream == null) {
            return;
        }

        byte[] source = stream.getBytes();
        byte[] scrubbed = removeTextBlocks(source);
        if (scrubbed.length != source.length) {
            stream.setData(scrubbed);
        }
    }

    byte[] removeTextBlocks(byte[] source) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(source.length);
        boolean removing = false;
        int index = 0;

        while (index < source.length) {
            if (!removing && isToken(source, index, 'B', 'T')) {
                removing = true;
                index += 2;
                continue;
            }

            if (removing && isToken(source, index, 'E', 'T')) {
                removing = false;
                index += 2;
                continue;
            }

            int nextIndex = nextSegmentEnd(source, index);
            if (!removing) {
                output.write(source, index, nextIndex - index);
            }
            index = nextIndex;
        }

        return output.toByteArray();
    }

    private int nextSegmentEnd(byte[] source, int index) {
        byte value = source[index];

        if (value == '(') {
            return literalStringEnd(source, index);
        }

        if (value == '<' && index + 1 < source.length && source[index + 1] != '<') {
            return hexStringEnd(source, index);
        }

        if (value == '%') {
            return commentEnd(source, index);
        }

        if (isToken(source, index, 'B', 'I')) {
            return inlineImageEnd(source, index);
        }

        return index + 1;
    }

    private int literalStringEnd(byte[] source, int index) {
        int depth = 1;
        int cursor = index + 1;
        boolean escaped = false;

        while (cursor < source.length) {
            byte value = source[cursor];
            if (escaped) {
                escaped = false;
            } else if (value == '\\') {
                escaped = true;
            } else if (value == '(') {
                depth += 1;
            } else if (value == ')') {
                depth -= 1;
                if (depth == 0) {
                    return cursor + 1;
                }
            }
            cursor += 1;
        }

        return source.length;
    }

    private int hexStringEnd(byte[] source, int index) {
        int cursor = index + 1;
        while (cursor < source.length) {
            if (source[cursor] == '>') {
                return cursor + 1;
            }
            cursor += 1;
        }
        return source.length;
    }

    private int commentEnd(byte[] source, int index) {
        int cursor = index + 1;
        while (cursor < source.length) {
            if (source[cursor] == '\n' || source[cursor] == '\r') {
                return cursor + 1;
            }
            cursor += 1;
        }
        return source.length;
    }

    private int inlineImageEnd(byte[] source, int index) {
        int cursor = index + 2;
        while (cursor < source.length) {
            if (isToken(source, cursor, 'I', 'D')) {
                cursor += 2;
                break;
            }
            cursor = nextSegmentEnd(source, cursor);
        }

        while (cursor < source.length) {
            if (isToken(source, cursor, 'E', 'I')) {
                return cursor + 2;
            }
            cursor += 1;
        }

        return source.length;
    }

    private boolean isToken(byte[] source, int index, char first, char second) {
        return index + 1 < source.length
                && source[index] == (byte) first
                && source[index + 1] == (byte) second
                && isBoundary(source, index - 1)
                && isBoundary(source, index + 2);
    }

    private boolean isBoundary(byte[] source, int index) {
        if (index < 0 || index >= source.length) {
            return true;
        }

        byte value = source[index];
        return value == 0
                || value == '\t'
                || value == '\n'
                || value == '\f'
                || value == '\r'
                || value == ' '
                || value == '('
                || value == ')'
                || value == '<'
                || value == '>'
                || value == '['
                || value == ']'
                || value == '{'
                || value == '}'
                || value == '/'
                || value == '%';
    }
}
