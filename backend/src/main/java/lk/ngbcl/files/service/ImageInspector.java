package lk.ngbcl.files.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Optional;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import lk.ngbcl.files.domain.FilePurpose;
import org.springframework.stereotype.Component;

/**
 * Checks what a file really is from its bytes (never the extension or browser MIME type —
 * SRS §7.3), reads dimensions without decoding huge images, and re-encodes public copies.
 */
@Component
public class ImageInspector {

    /** Guards against decompression bombs. */
    static final int MAX_SIDE = 12_000;
    static final long MAX_PIXELS = 40_000_000L;

    public enum Format {
        JPEG("image/jpeg", "jpg"),
        PNG("image/png", "png"),
        WEBP("image/webp", "webp");

        public final String contentType;
        public final String extension;

        Format(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }

        public static Optional<Format> fromContentType(String type) {
            for (Format f : values()) {
                if (f.contentType.equalsIgnoreCase(type)) {
                    return Optional.of(f);
                }
            }
            return Optional.empty();
        }
    }

    public record Dimensions(int width, int height) {
    }

    public ImageInspector() {
        // Register the TwelveMonkeys WebP reader inside the Spring Boot fat jar.
        ImageIO.scanForPlugins();
    }

    /** Magic-byte sniffing. */
    public Optional<Format> sniff(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return Optional.of(Format.JPEG);
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return Optional.of(Format.PNG);
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return Optional.of(Format.WEBP);
        }
        return Optional.empty();
    }

    /** Reads width/height from the header only. Empty if the image can't be parsed. */
    public Optional<Dimensions> dimensions(byte[] bytes) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (in == null) {
                return Optional.empty();
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return Optional.empty();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                return Optional.of(new Dimensions(reader.getWidth(0), reader.getHeight(0)));
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    public boolean withinSafeLimits(Dimensions d) {
        return d.width() <= MAX_SIDE && d.height() <= MAX_SIDE && (long) d.width() * d.height() <= MAX_PIXELS;
    }

    /** True if the w:h ratio matches the purpose's required ratio within 2 %. */
    public boolean ratioMatches(FilePurpose purpose, Dimensions d) {
        if (!purpose.hasFixedRatio()) {
            return true;
        }
        double expected = (double) purpose.ratioW / purpose.ratioH;
        double actual = (double) d.width() / d.height();
        return Math.abs(actual - expected) / expected <= 0.02;
    }

    /**
     * Decodes and re-encodes for public delivery: strips EXIF/GPS and anything appended to the
     * file, scales down to {@code maxSide}, flattens transparency onto white for JPEG.
     */
    public byte[] reencode(byte[] bytes, FilePurpose.PublicFormat target, int maxSide) throws IOException {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(bytes));
        if (src == null) {
            throw new IOException("Unreadable image");
        }
        int w = src.getWidth();
        int h = src.getHeight();
        double scale = Math.min(1.0, (double) maxSide / Math.max(w, h));
        int tw = Math.max(1, (int) Math.round(w * scale));
        int th = Math.max(1, (int) Math.round(h * scale));
        boolean jpeg = target == FilePurpose.PublicFormat.JPEG;
        BufferedImage out = new BufferedImage(tw, th, jpeg ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (jpeg) {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, tw, th);
            }
            g.drawImage(src, 0, 0, tw, th, null);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        if (jpeg) {
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.88f);
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(buffer)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(out, null, null), param);
            } finally {
                writer.dispose();
            }
        } else {
            ImageIO.write(out, "png", buffer);
        }
        return buffer.toByteArray();
    }
}
