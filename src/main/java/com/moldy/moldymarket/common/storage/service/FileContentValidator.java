package com.moldy.moldymarket.common.storage.service;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;

@Component
public class FileContentValidator {

    private static final long MAX_IMAGE_PIXELS = 16_000_000L;
    private static final Map<String, String> MIME_FORMATS = Map.of(
            "image/jpeg", "jpeg",
            "image/png", "png",
            "image/webp", "webp");

    public void validate(String contentType, InputStream input) throws IOException {
        if ("application/pdf".equals(contentType)) {
            validatePdf(input);
            return;
        }

        String expectedFormat = MIME_FORMATS.get(contentType);
        if (expectedFormat == null) {
            throw new InvalidFileContentException("Unsupported file type");
        }
        validateImage(expectedFormat, input);
    }

    private void validatePdf(InputStream input) throws IOException {
        try (RandomAccessReadBuffer buffer = new RandomAccessReadBuffer(input);
                PDDocument document = Loader.loadPDF(buffer)) {
            if (document.getNumberOfPages() < 1) {
                throw new InvalidFileContentException("PDF contains no pages");
            }
        } catch (InvalidFileContentException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new InvalidFileContentException("PDF data is malformed", exception);
        }
    }

    private void validateImage(String expectedFormat, InputStream input) throws IOException {
        BufferedInputStream buffered = new BufferedInputStream(input);
        buffered.mark(16);
        byte[] header = buffered.readNBytes(12);
        buffered.reset();
        if (!hasExpectedSignature(expectedFormat, header)) {
            throw new InvalidFileContentException("File signature does not match its content type");
        }

        try (ImageInputStream imageInput = ImageIO.createImageInputStream(buffered)) {
            if (imageInput == null) {
                throw new InvalidFileContentException("Image decoder could not open the file");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw new InvalidFileContentException("No image decoder accepted the file");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                String actualFormat = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!formatMatches(expectedFormat, actualFormat)) {
                    throw new InvalidFileContentException("Image format does not match its content type");
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_IMAGE_PIXELS) {
                    throw new InvalidFileContentException("Image dimensions are invalid or too large");
                }
                BufferedImage decoded = reader.read(0);
                if (decoded == null) {
                    throw new InvalidFileContentException("Image decoder returned no image");
                }
                decoded.flush();
            } catch (InvalidFileContentException exception) {
                throw exception;
            } catch (IOException exception) {
                throw new InvalidFileContentException("Image data is malformed", exception);
            } catch (RuntimeException exception) {
                throw new InvalidFileContentException("Image data is malformed", exception);
            } finally {
                reader.dispose();
            }
        }
    }

    private boolean formatMatches(String expected, String actual) {
        return expected.equals(actual)
                || ("jpeg".equals(expected) && "jpg".equals(actual));
    }

    private boolean hasExpectedSignature(String type, byte[] header) {
        if ("jpeg".equals(type)) {
            return header.length >= 3 && (header[0] & 0xff) == 0xff
                    && (header[1] & 0xff) == 0xd8 && (header[2] & 0xff) == 0xff;
        }
        if ("png".equals(type)) {
            byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
            return startsWith(header, png);
        }
        if ("webp".equals(type)) {
            return header.length >= 12 && header[0] == 'R' && header[1] == 'I'
                    && header[2] == 'F' && header[3] == 'F'
                    && header[8] == 'W' && header[9] == 'E'
                    && header[10] == 'B' && header[11] == 'P';
        }
        return false;
    }

    private boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (value[i] != prefix[i]) return false;
        }
        return true;
    }

    public static class InvalidFileContentException extends IOException {
        public InvalidFileContentException(String message) {
            super(message);
        }

        public InvalidFileContentException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
