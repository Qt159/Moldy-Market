package com.moldy.moldymarket.common.storage.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;

class FileContentValidatorTest {

    private final FileContentValidator validator = new FileContentValidator();

    @Test
    void validateAcceptsValidMp4Signature() {
        byte[] mp4Header = new byte[] {
                0x00, 0x00, 0x00, 0x18,
                'f', 't', 'y', 'p',
                'm', 'p', '4', '2'
        };

        assertDoesNotThrow(() -> validator.validate("video/mp4", new ByteArrayInputStream(mp4Header)));
    }

    @Test
    void validateRejectsInvalidMp4Signature() {
        byte[] fakeMp4 = new byte[] {
                0x00, 0x00, 0x00, 0x18,
                'n', 'o', 'p', 'e',
                0x00, 0x00, 0x00, 0x00
        };

        assertThrows(
                FileContentValidator.InvalidFileContentException.class,
                () -> validator.validate("video/mp4", new ByteArrayInputStream(fakeMp4)));
    }

    @Test
    void validateAcceptsValidWebmSignature() {
        byte[] webmHeader = new byte[] {
                0x1A, 0x45, (byte) 0xDF, (byte) 0xA3,
                0x01, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x1F
        };

        assertDoesNotThrow(() -> validator.validate("video/webm", new ByteArrayInputStream(webmHeader)));
    }

    @Test
    void validateRejectsInvalidWebmSignature() {
        byte[] fakeWebm = new byte[] {
                0x00, 0x01, 0x02, 0x03,
                0x04, 0x05, 0x06, 0x07,
                0x08, 0x09, 0x0A, 0x0B
        };

        assertThrows(
                FileContentValidator.InvalidFileContentException.class,
                () -> validator.validate("video/webm", new ByteArrayInputStream(fakeWebm)));
    }
}
