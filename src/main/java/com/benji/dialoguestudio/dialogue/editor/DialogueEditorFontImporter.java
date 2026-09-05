package com.benji.dialoguestudio.dialogue.editor;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;


public final class DialogueEditorFontImporter {

    private static final Gson GSON = new Gson();

    private DialogueEditorFontImporter() {
    }

    public static String importTtf(DialogueEditorProject project, Path source) throws IOException {
        require(source, ".ttf");

        String key = fontKey(source.getFileName().toString());
        Path target = DialogueEditorWorkspace.assetRoot(project).resolve("font").resolve(key + ".ttf");

        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);

        DialogueEditorProject.FontAsset asset = new DialogueEditorProject.FontAsset();

        asset.type = "ttf";
        asset.file = "font/" + key + ".ttf";
        asset.size = 11.0F;
        asset.oversample = 8.0F;

        project.fonts.put(key, asset);
        DialogueEditorWorkspace.save(project);

        return project.namespace + ":" + key;
    }

    public static String importMsdf(DialogueEditorProject project, Path jsonFile) throws IOException {
        require(jsonFile, ".json");

        JsonObject root = GSON.fromJson(Files.readString(jsonFile, StandardCharsets.UTF_8), JsonObject.class);

        if (root == null || !root.has("glyphs") || !root.get("glyphs").isJsonArray()) {
            throw new IOException("MSDF JSON must contain a glyphs array (msdf-atlas-gen format)");
        }

        Path pngFile = findAtlasPng(jsonFile, root);
        BufferedImage source = ImageIO.read(pngFile.toFile());

        if (source == null) {
            throw new IOException("Could not read MSDF atlas PNG: " + pngFile.getFileName());
        }

        JsonObject atlas = object(root, "atlas");
        JsonObject metrics = object(root, "metrics");

        float atlasSize = number(atlas, "size", 32.0F);
        float distanceRange = Math.max(1.0F, number(atlas, "distanceRange", 4.0F));
        String yOrigin = string(atlas, "yOrigin", "bottom");

        float ascender = number(metrics, "ascender", 0.9F);
        float descender = number(metrics, "descender", -0.25F);
        float lineHeight = number(metrics, "lineHeight", ascender - descender);

        List<Glyph> glyphs = readGlyphs(root.getAsJsonArray("glyphs"));
        if (glyphs.isEmpty()) {
            throw new IOException("MSDF atlas contains no BMP unicode glyphs");
        }

        glyphs.sort(Comparator.comparingInt(g -> g.unicode));

        int padding = 2;
        int ascent = Math.max(1, Math.round(ascender * atlasSize) + padding);
        int cellH = Math.max(ascent + 2, Math.round(lineHeight * atlasSize) + padding * 2);

        int maxAdvance = 1;
        int maxOverhang = 1;

        for (Glyph glyph : glyphs) {
            maxAdvance = Math.max(maxAdvance, Math.round(glyph.advance * atlasSize));
            if (glyph.plane != null) {
                maxOverhang = Math.max(maxOverhang, Math.round((glyph.plane.right - Math.min(0.0F, glyph.plane.left)) * atlasSize));
            }
        }

        int cellW = Math.max(4, Math.max(maxAdvance, maxOverhang) + padding * 2);
        int columns = Math.min(16, Math.max(1, glyphs.size()));
        int rows = (glyphs.size() + columns - 1) / columns;

        BufferedImage output = new BufferedImage(cellW * columns, cellH * rows, BufferedImage.TYPE_INT_ARGB);

        List<String> charRows = new ArrayList<>();

        for (int row = 0; row < rows; row++) {
            StringBuilder chars = new StringBuilder(columns);

            for (int col = 0; col < columns; col++) {
                int index = row * columns + col;

                if (index >= glyphs.size()) {
                    chars.append('\u0000');
                    continue;
                }

                Glyph glyph = glyphs.get(index);
                chars.append((char) glyph.unicode);

                bakeGlyph(source, output, glyph, col * cellW, row * cellH, cellW, cellH, ascent, atlasSize, distanceRange, yOrigin, padding);
            }

            charRows.add(chars.toString());
        }

        String key = fontKey(jsonFile.getFileName().toString());
        Path texture = DialogueEditorWorkspace.assetRoot(project).resolve("textures/font").resolve(key + ".png");

        Files.createDirectories(texture.getParent());
        ImageIO.write(output, "PNG", texture.toFile());

        DialogueEditorProject.FontAsset asset = new DialogueEditorProject.FontAsset();

        asset.type = "bitmap_msdf";
        asset.file = "font/" + key + ".png";
        asset.height = 11;
        float normalizedAscent = lineHeight > 0.0001F ? ascender / lineHeight : 0.82F;
        asset.ascent = Math.max(1, Math.min(asset.height, Math.round(asset.height * normalizedAscent)));
        asset.chars = charRows;

        project.fonts.put(key, asset);
        DialogueEditorWorkspace.save(project);

        return project.namespace + ":" + key;
    }

    private static void bakeGlyph(BufferedImage source, BufferedImage output, Glyph glyph, int cellX, int cellY, int cellW, int cellH, int ascent, float atlasSize, float distanceRange, String yOrigin, int padding) {
        if (glyph.atlas == null || glyph.plane == null) {
            writeAdvanceMarker(output, glyph, cellX, cellY, cellW, cellH, atlasSize, padding);
            return;
        }

        int srcLeft = clamp((int) Math.floor(glyph.atlas.left), 0, source.getWidth());
        int srcRight = clamp((int) Math.ceil(glyph.atlas.right), 0, source.getWidth());

        int srcTop;
        int srcBottom;

        if ("top".equalsIgnoreCase(yOrigin)) {
            srcTop = clamp((int) Math.floor(glyph.atlas.top), 0, source.getHeight());
            srcBottom = clamp((int) Math.ceil(glyph.atlas.bottom), 0, source.getHeight());

            if (srcBottom < srcTop) {
                int swap = srcTop;
                srcTop = srcBottom;
                srcBottom = swap;
            }
        } else {
            srcTop = clamp(source.getHeight() - (int) Math.ceil(glyph.atlas.top), 0, source.getHeight());
            srcBottom = clamp(source.getHeight() - (int) Math.floor(glyph.atlas.bottom), 0, source.getHeight());
        }

        int copyW = Math.max(0, srcRight - srcLeft);
        int copyH = Math.max(0, srcBottom - srcTop);

        int destX = cellX + padding + Math.round(Math.max(0.0F, glyph.plane.left) * atlasSize);
        int destY = cellY + ascent - Math.round(glyph.plane.top * atlasSize);

        for (int y = 0; y < copyH; y++) {
            int oy = destY + y;
            if (oy < cellY || oy >= cellY + cellH) continue;

            for (int x = 0; x < copyW; x++) {
                int ox = destX + x;
                if (ox < cellX || ox >= cellX + cellW) continue;

                int argb = source.getRGB(srcLeft + x, srcTop + y);
                int r = (argb >> 16) & 255;
                int g = (argb >> 8) & 255;
                int b = argb & 255;

                float median = median(r / 255.0F, g / 255.0F, b / 255.0F);
                float alpha = clamp01((median - 0.5F) * distanceRange + 0.5F);
                int a = Math.round(alpha * 255.0F);

                output.setRGB(ox, oy, (a << 24) | 0xFFFFFF);
            }
        }
        writeAdvanceMarker(output, glyph, cellX, cellY, cellW, cellH, atlasSize, padding);
    }

    private static void writeAdvanceMarker(BufferedImage output, Glyph glyph, int cellX, int cellY, int cellW, int cellH, float atlasSize, int padding) {
        int advance = Math.max(1, Math.round(glyph.advance * atlasSize));
        int markerX = clamp(cellX + padding + advance - 1, cellX, cellX + cellW - 1);
        int markerY = cellY + cellH - 1;
        int existing = output.getRGB(markerX, markerY);
        if (((existing >>> 24) & 255) == 0) {
            output.setRGB(markerX, markerY, 0x01FFFFFF);
        }
    }

    private static List<Glyph> readGlyphs(JsonArray array) {
        List<Glyph> result = new ArrayList<>();

        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject glyph = element.getAsJsonObject();

            int unicode = integer(glyph, "unicode", -1);
            if (unicode < 1 || unicode > Character.MAX_VALUE) continue;

            float advance = number(glyph, "advance", 0.6F);
            Bounds plane = bounds(glyph, "planeBounds");
            Bounds atlas = bounds(glyph, "atlasBounds");

            result.add(new Glyph(unicode, advance, plane, atlas));
        }

        return result;
    }

    private static Bounds bounds(JsonObject parent, String key) {
        if (!parent.has(key) || !parent.get(key).isJsonObject()) return null;
        JsonObject value = parent.getAsJsonObject(key);

        return new Bounds(number(value, "left", 0.0F), number(value, "bottom", 0.0F), number(value, "right", 0.0F), number(value, "top", 0.0F));
    }

    private static Path findAtlasPng(Path jsonFile, JsonObject root) throws IOException {
        JsonObject atlas = object(root, "atlas");

        for (String key : List.of("file", "image", "texture")) {
            String declared = string(atlas, key, null);
            if (declared != null && !declared.isBlank()) {
                Path candidate = jsonFile.getParent().resolve(declared).normalize();
                if (Files.isRegularFile(candidate)) return candidate;
            }
        }

        String base = jsonFile.getFileName().toString();
        int dot = base.lastIndexOf('.');
        if (dot >= 0) base = base.substring(0, dot);

        Path sameName = jsonFile.resolveSibling(base + ".png");
        if (Files.isRegularFile(sameName)) return sameName;

        try (var stream = Files.list(jsonFile.getParent())) {
            List<Path> pngs = stream.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")).toList();

            if (pngs.size() == 1) return pngs.get(0);
        }

        throw new IOException("Could not find the MSDF atlas PNG next to " + jsonFile.getFileName());
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : new JsonObject();
    }

    private static float number(JsonObject object, String key, float fallback) {
        try {
            return object.has(key) ? object.get(key).getAsFloat() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int integer(JsonObject object, String key, int fallback) {
        try {
            return object.has(key) ? object.get(key).getAsInt() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String string(JsonObject object, String key, String fallback) {
        try {
            return object.has(key) ? object.get(key).getAsString() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static void require(Path path, String extension) throws IOException {
        if (path == null || !Files.isRegularFile(path) || !path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(extension)) {
            throw new IOException("Expected " + extension + " file");
        }
    }

    private static String fontKey(String name) {
        String value = name.toLowerCase(Locale.ROOT);
        int dot = value.lastIndexOf('.');
        if (dot > 0) value = value.substring(0, dot);
        value = value.replace(' ', '_').replaceAll("[^a-z0-9_.-]", "_");
        return value.isBlank() ? "custom_font" : value;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static float median(float a, float b, float c) {
        return Math.max(Math.min(a, b), Math.min(Math.max(a, b), c));
    }

    private record Glyph(int unicode, float advance, Bounds plane, Bounds atlas) {
    }

    private record Bounds(float left, float bottom, float right, float top) {
    }
}
