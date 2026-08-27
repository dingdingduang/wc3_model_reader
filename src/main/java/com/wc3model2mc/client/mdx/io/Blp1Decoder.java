package com.wc3model2mc.client.mdx.io;

import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.Raster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

/**
 * Decoder for Warcraft III BLP1 JPEG and indexed-color textures.
 * Only the full-resolution mip level is uploaded to Minecraft.
 */
public final class Blp1Decoder {
    private static final int HEADER_SIZE = 156;
    private static final int PALETTE_SIZE = 256 * 4;
    private static final int MAX_DIMENSION = 8192;

    private Blp1Decoder() {
    }

    public static DecodedImage decode(byte[] bytes) throws IOException {
        LittleEndianDataReader reader = new LittleEndianDataReader(bytes);
        if (!"BLP1".equals(reader.readTag())) {
            throw new IOException("Only Warcraft III BLP1 textures are supported");
        }

        int contentType = reader.readInt();
        int declaredAlphaBits = reader.readInt();
        int width = reader.readInt();
        int height = reader.readInt();
        reader.readInt(); // BLP1 extra field.
        reader.readInt(); // hasMipmaps flag.
        validateDimensions(width, height);

        int[] mipOffsets = new int[16];
        int[] mipSizes = new int[16];
        for (int index = 0; index < mipOffsets.length; index++) {
            mipOffsets[index] = reader.readInt();
        }
        for (int index = 0; index < mipSizes.length; index++) {
            mipSizes[index] = reader.readInt();
        }
        if (reader.position() != HEADER_SIZE) {
            throw new IOException("Invalid BLP1 header length");
        }

        int mipOffset = mipOffsets[0];
        int mipSize = mipSizes[0];
        checkRange(bytes, mipOffset, mipSize, "BLP1 full-resolution mipmap");
        return switch (contentType) {
            case 0 -> decodeJpeg(bytes, reader, mipOffset, mipSize, width, height,
                    hasJpegAlpha(declaredAlphaBits));
            case 1 -> decodeIndexed(bytes, reader, mipOffset, mipSize, width, height,
                    normalizeIndexedAlphaBits(declaredAlphaBits));
            default -> throw new IOException("Unsupported BLP1 content type: " + contentType);
        };
    }

    private static DecodedImage decodeJpeg(
            byte[] blpBytes,
            LittleEndianDataReader headerReader,
            int mipOffset,
            int mipSize,
            int width,
            int height,
            boolean hasAlpha
    ) throws IOException {
        int sharedHeaderLength = headerReader.readInt();
        if (sharedHeaderLength < 0 || sharedHeaderLength > 1_048_576) {
            throw new IOException("Invalid BLP1 shared JPEG header length: " + sharedHeaderLength);
        }
        byte[] sharedHeader = headerReader.readBytes(sharedHeaderLength);
        ByteArrayOutputStream jpegBytes = new ByteArrayOutputStream(sharedHeader.length + mipSize);
        jpegBytes.write(sharedHeader);
        jpegBytes.write(blpBytes, mipOffset, mipSize);

        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("jpeg");
        ImageReader jpegReader = null;
        while (readers.hasNext()) {
            ImageReader candidate = readers.next();
            if (candidate.canReadRaster()) {
                jpegReader = candidate;
                break;
            }
            candidate.dispose();
        }
        if (jpegReader == null) {
            throw new IIOException("No raster-capable JPEG ImageReader is installed");
        }

        try (ImageInputStream input = ImageIO.createImageInputStream(
                new ByteArrayInputStream(jpegBytes.toByteArray())
        )) {
            jpegReader.setInput(input, true, true);
            ImageReadParam parameters = jpegReader.getDefaultReadParam();
            // WC3 stores the JPEG samples as BGRA. Reorder them to RGBA.
            parameters.setSourceBands(new int[]{2, 1, 0, 3});
            Raster raster = jpegReader.readRaster(0, parameters);
            if (raster.getWidth() != width || raster.getHeight() != height) {
                throw new IOException(
                        "BLP1 JPEG dimensions " + raster.getWidth() + 'x' + raster.getHeight()
                                + " do not match the header " + width + 'x' + height
                );
            }

            int[] argb = new int[Math.multiplyExact(width, height)];
            int bandCount = raster.getNumBands();
            if (bandCount < 3) {
                throw new IOException("BLP1 JPEG has only " + bandCount + " sample band(s)");
            }
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int red = raster.getSample(x, y, 0);
                    int green = raster.getSample(x, y, 1);
                    int blue = raster.getSample(x, y, 2);
                    int alpha = hasAlpha && bandCount >= 4
                            ? raster.getSample(x, y, 3)
                            : 255;
                    argb[x + y * width] = argb(alpha, red, green, blue);
                }
            }
            return new DecodedImage(width, height, argb);
        } finally {
            jpegReader.dispose();
        }
    }

    private static DecodedImage decodeIndexed(
            byte[] blpBytes,
            LittleEndianDataReader headerReader,
            int mipOffset,
            int mipSize,
            int width,
            int height,
            int alphaBits
    ) throws IOException {
        byte[] palette = headerReader.readBytes(PALETTE_SIZE);
        int pixelCount = Math.multiplyExact(width, height);
        int alphaByteCount = alphaBits == 0
                ? 0
                : Math.addExact(Math.multiplyExact(pixelCount, alphaBits), 7) / 8;
        int requiredSize = Math.addExact(pixelCount, alphaByteCount);
        if (mipSize < requiredSize) {
            throw new IOException(
                    "BLP1 indexed mipmap needs " + requiredSize
                            + " bytes but contains " + mipSize
            );
        }

        int[] argb = new int[pixelCount];
        int alphaOffset = mipOffset + pixelCount;
        for (int pixel = 0; pixel < pixelCount; pixel++) {
            int paletteIndex = Byte.toUnsignedInt(blpBytes[mipOffset + pixel]);
            int paletteOffset = paletteIndex * 4;
            int blue = Byte.toUnsignedInt(palette[paletteOffset]);
            int green = Byte.toUnsignedInt(palette[paletteOffset + 1]);
            int red = Byte.toUnsignedInt(palette[paletteOffset + 2]);
            int alpha = readPackedAlpha(blpBytes, alphaOffset, pixel, alphaBits);
            argb[pixel] = argb(alpha, red, green, blue);
        }
        return new DecodedImage(width, height, argb);
    }

    private static int readPackedAlpha(byte[] bytes, int offset, int pixel, int alphaBits) {
        if (alphaBits == 0) {
            return 255;
        }
        int bitIndex = pixel * alphaBits;
        int packed = Byte.toUnsignedInt(bytes[offset + bitIndex / 8]);
        int mask = (1 << alphaBits) - 1;
        int sample = packed >>> (bitIndex % 8) & mask;
        return sample * 255 / mask;
    }

    private static int normalizeIndexedAlphaBits(int alphaBits) {
        return alphaBits == 1 || alphaBits == 4 || alphaBits == 8 ? alphaBits : 0;
    }

    private static boolean hasJpegAlpha(int alphaBits) {
        // JPEG BLP1 supports exactly 0-bit or 8-bit alpha. Values such as 9 are
        // legacy flags, not an alpha depth, and Warcraft treats them as opaque.
        return alphaBits == 8;
    }

    private static void validateDimensions(int width, int height) throws IOException {
        if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
            throw new IOException("Invalid BLP1 dimensions: " + width + 'x' + height);
        }
        try {
            Math.multiplyExact(width, height);
        } catch (ArithmeticException exception) {
            throw new IOException("BLP1 dimensions overflow: " + width + 'x' + height, exception);
        }
    }

    private static void checkRange(
            byte[] bytes,
            int offset,
            int size,
            String label
    ) throws IOException {
        if (offset < 0 || size <= 0 || (long) offset + size > bytes.length) {
            throw new IOException(
                    label + " range is outside the file: offset="
                            + Integer.toUnsignedLong(offset) + ", size="
                            + Integer.toUnsignedLong(size)
            );
        }
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    public record DecodedImage(int width, int height, int[] argbPixels) {
        public DecodedImage {
            argbPixels = Arrays.copyOf(argbPixels, argbPixels.length);
            if (argbPixels.length != Math.multiplyExact(width, height)) {
                throw new IllegalArgumentException("Pixel array does not match image dimensions");
            }
        }

        @Override
        public int[] argbPixels() {
            return argbPixels.clone();
        }
    }
}
