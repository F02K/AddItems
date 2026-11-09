package dev.fosk.additems.web;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PngValidationTest {

    private static byte[] png(int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB), "png", out);
        return out.toByteArray();
    }

    @Test
    void acceptsSquarePng() throws Exception {
        assertNull(DashboardServer.validatePng(png(16, 16)));
    }

    @Test
    void rejectsNonSquareOversizedAndNonPng() throws Exception {
        assertEquals("texture must be square", DashboardServer.validatePng(png(16, 32)));
        assertEquals("texture larger than 1024x1024", DashboardServer.validatePng(png(2048, 2048)));
        assertNotNull(DashboardServer.validatePng("<svg/>".getBytes()));
    }
}
