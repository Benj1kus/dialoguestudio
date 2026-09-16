package com.benji.dialoguestudio.client.dialogue;

import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public final class DialogueGifDecoder {
    public static final int MAX_DIMENSION = 4096;
    public static final int MAX_FRAMES = 2048;
    public static final long MAX_DECODED_BYTES = 128L * 1024 * 1024;
    private static final String STREAM_FORMAT = "javax_imageio_gif_stream_1.0";
    private static final String IMAGE_FORMAT = "javax_imageio_gif_image_1.0";

    private DialogueGifDecoder() {
    }

    public record Animation(int width, int height, List<int[]> frames, Timing timing) {
    }

    public static final class Timing {
        private final long[] frameEnds;
        private final long durationMillis;
        private final int plays;

        private Timing(long[] frameEnds, int plays) {
            this.frameEnds = frameEnds;
            this.durationMillis = frameEnds[frameEnds.length - 1];
            this.plays = plays;
        }

        public int frameAt(long elapsedMillis) {
            long elapsed = Math.max(0L, elapsedMillis);
            if (plays != 0 && elapsed / durationMillis >= plays) return frameEnds.length - 1;
            long phase = elapsed % durationMillis;
            int low = 0;
            int high = frameEnds.length - 1;
            while (low < high) {
                int middle = (low + high) >>> 1;
                if (phase < frameEnds[middle]) high = middle;
                else low = middle + 1;
            }
            return low;
        }

        public long durationMillis() {
            return durationMillis;
        }

        public int plays() {
            return plays;
        }
    }

    public static Animation read(InputStream source) throws IOException {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext()) throw new IOException("Java GIF decoder is unavailable");
        ImageReader reader = readers.next();
        try (MemoryCacheImageInputStream input = new MemoryCacheImageInputStream(source)) {
            reader.setInput(input, false, false);
            Node stream = reader.getStreamMetadata().getAsTree(STREAM_FORMAT);
            Node logical = child(stream, "LogicalScreenDescriptor");
            int width = integer(logical, "logicalScreenWidth", 0);
            int height = integer(logical, "logicalScreenHeight", 0);
            if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
                throw new IOException("GIF dimensions must be between 1 and " + MAX_DIMENSION);
            }
            int count = reader.getNumImages(true);
            if (count < 1 || count > MAX_FRAMES)
                throw new IOException("GIF must contain 1 to " + MAX_FRAMES + " frames");
            long pixels = (long) width * height;
            if (pixels * 4L * (count + 3L) > MAX_DECODED_BYTES) {
                throw new IOException("GIF is too large when decoded (max 128 MiB); reduce its resolution or frame count");
            }

            int background = backgroundColor(stream);
            int[] canvas = new int[(int) pixels];
            List<int[]> frames = new ArrayList<>(count);
            long[] frameEnds = new long[count];
            long duration = 0L;
            int plays = 1;
            for (int index = 0; index < count; index++) {
                Node metadata = reader.getImageMetadata(index).getAsTree(IMAGE_FORMAT);
                Node descriptor = child(metadata, "ImageDescriptor");
                Node control = child(metadata, "GraphicControlExtension");
                int left = integer(descriptor, "imageLeftPosition", 0);
                int top = integer(descriptor, "imageTopPosition", 0);
                int patchWidth = integer(descriptor, "imageWidth", 0);
                int patchHeight = integer(descriptor, "imageHeight", 0);
                if (patchWidth < 1 || patchHeight < 1 || left < 0 || top < 0 || (long) left + patchWidth > width || (long) top + patchHeight > height) {
                    throw new IOException("GIF frame " + index + " lies outside its canvas");
                }
                boolean transparent = "TRUE".equalsIgnoreCase(attribute(control, "transparentColorFlag", "FALSE"));
                String disposal = attribute(control, "disposalMethod", "none");
                if (index == 0) Arrays.fill(canvas, transparent ? 0 : background);
                int[] previous = "restoreToPrevious".equals(disposal) ? canvas.clone() : null;

                BufferedImage patch = reader.read(index);
                int[] row = new int[patchWidth];
                for (int y = 0; y < patchHeight; y++) {
                    patch.getRGB(0, y, patchWidth, 1, row, 0, patchWidth);
                    int target = (top + y) * width + left;
                    for (int x = 0; x < patchWidth; x++) {
                        if ((row[x] >>> 24) != 0) canvas[target + x] = row[x];
                    }
                }
                frames.add(canvas.clone());
                int delay = integer(control, "delayTime", 0);
                duration += delay > 0 ? delay * 10L : 100L;
                frameEnds[index] = duration;
                int loop = loopCount(metadata);
                if (loop >= 0) plays = loop == 0 ? 0 : loop + 1;

                if ("restoreToBackgroundColor".equals(disposal)) {
                    int clear = transparent ? 0 : background;
                    for (int y = top; y < top + patchHeight; y++) {
                        Arrays.fill(canvas, y * width + left, y * width + left + patchWidth, clear);
                    }
                } else if (previous != null) {
                    canvas = previous;
                }
            }
            return new Animation(width, height, List.copyOf(frames), new Timing(frameEnds, plays));
        } catch (IllegalArgumentException | IndexOutOfBoundsException exception) {
            throw new IOException("Invalid GIF image", exception);
        } finally {
            reader.dispose();
        }
    }

    private static int backgroundColor(Node stream) {
        Node table = child(stream, "GlobalColorTable");
        int backgroundIndex = integer(table, "backgroundColorIndex", -1);
        if (table != null) {
            for (Node entry = table.getFirstChild(); entry != null; entry = entry.getNextSibling()) {
                if (integer(entry, "index", -2) == backgroundIndex) {
                    return 0xFF000000 | integer(entry, "red", 0) << 16 | integer(entry, "green", 0) << 8 | integer(entry, "blue", 0);
                }
            }
        }
        return 0;
    }

    private static int loopCount(Node metadata) {
        Node extensions = child(metadata, "ApplicationExtensions");
        if (extensions == null) return -1;
        for (Node node = extensions.getFirstChild(); node != null; node = node.getNextSibling()) {
            String id = attribute(node, "applicationID", "") + attribute(node, "authenticationCode", "");
            if (("NETSCAPE2.0".equals(id) || "ANIMEXTS1.0".equals(id)) && node instanceof IIOMetadataNode extension && extension.getUserObject() instanceof byte[] bytes && bytes.length >= 3 && bytes[0] == 1) {
                return (bytes[1] & 255) | (bytes[2] & 255) << 8;
            }
        }
        return -1;
    }

    private static Node child(Node parent, String name) {
        if (parent == null) return null;
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (name.equals(node.getNodeName())) return node;
        }
        return null;
    }

    private static String attribute(Node node, String name, String fallback) {
        Node value = node == null || node.getAttributes() == null ? null : node.getAttributes().getNamedItem(name);
        return value == null ? fallback : value.getNodeValue();
    }

    private static int integer(Node node, String name, int fallback) {
        String value = attribute(node, name, "");
        return value.isEmpty() ? fallback : Integer.parseInt(value);
    }
}
