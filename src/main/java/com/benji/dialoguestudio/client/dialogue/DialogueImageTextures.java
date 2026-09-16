package com.benji.dialoguestudio.client.dialogue;

import com.benji.dialoguestudio.DialogueStudio;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DialogueImageTextures {
    public static final Cache EDITOR = new Cache("editor");
    public static final Cache DIALOGUE = new Cache("dialogue");
    public static final Cache WORLD = new Cache("world");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long MAX_CACHE_BYTES = 256L * 1024 * 1024;
    private static final int MAX_ENTRIES = 128;
    private static final Map<Key, Entry> ENTRIES = new LinkedHashMap<>(16, 0.75F, true);
    private static long cachedBytes;

    private DialogueImageTextures() {
    }

    private record Key(Cache owner, Object source) {
    }

    @FunctionalInterface
    private interface Source {
        InputStream open() throws IOException;
    }

    public static final class Cache {
        private final String name;

        private Cache(String name) {
            this.name = name;
        }

        public ResourceLocation resolve(Path path, ResourceLocation fallback) {
            return resolve(path, fallback, true);
        }

        public ResourceLocation resolve(Path path, ResourceLocation fallback, boolean animate) {
            Path normalized = path.toAbsolutePath().normalize();
            return resolve(new Key(this, normalized), () -> Files.newInputStream(normalized), isGif(normalized.toString()), fallback, animate);
        }

        public ResourceLocation resolve(ResourceLocation resource, ResourceLocation fallback) {
            return resolve(resource, fallback, true);
        }

        public ResourceLocation resolve(ResourceLocation resource, ResourceLocation fallback, boolean animate) {
            if (resource == null) return fallback;
            if (!isGif(resource.getPath())) return resource;
            return resolve(new Key(this, resource), () -> Minecraft.getInstance().getResourceManager().getResourceOrThrow(resource).open(), true, fallback, animate);
        }

        private ResourceLocation resolve(Key key, Source source, boolean gif, ResourceLocation fallback, boolean animate) {
            Entry entry = ENTRIES.get(key);
            if (entry == null) {
                try (InputStream input = source.open()) {
                    entry = gif ? loadGif(input, name) : loadPng(input, name);
                } catch (IOException | RuntimeException exception) {
                    LOGGER.warn("Dialogue Studio could not load image {}: {}", key.source(), exception.toString());
                    // Cache failures too, so broken images are not decoded/logged every draw.
                    entry = new Entry(null, null, List.of(), null, 0L);
                }
                makeRoom(entry.bytes);
                ENTRIES.put(key, entry);
                cachedBytes += entry.bytes;
            }
            return entry.id == null ? fallback : entry.currentTexture(animate);
        }

        public void restart(String declared) {
            ResourceLocation resource = declared == null ? null : ResourceLocation.tryParse(declared);
            if (resource == null) return;
            Entry entry = ENTRIES.get(new Key(this, resource));
            if (entry != null) entry.startedNanos = System.nanoTime();
        }

        public void restart() {
            long now = System.nanoTime();
            for (Map.Entry<Key, Entry> entry : ENTRIES.entrySet()) {
                if (entry.getKey().owner() == this) entry.getValue().startedNanos = now;
            }
        }

        public void invalidate(Path path) {
            Entry entry = ENTRIES.remove(new Key(this, path.toAbsolutePath().normalize()));
            if (entry != null) release(entry);
        }

        public void clear() {
            Iterator<Map.Entry<Key, Entry>> iterator = ENTRIES.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Key, Entry> entry = iterator.next();
                if (entry.getKey().owner() == this) {
                    release(entry.getValue());
                    iterator.remove();
                }
            }
        }
    }

    private static boolean isGif(String name) {
        return name.toLowerCase(Locale.ROOT).endsWith(".gif");
    }

    private static Entry loadPng(InputStream input, String owner) throws IOException {
        NativeImage pixels = NativeImage.read(input);
        long bytes = (long) pixels.getWidth() * pixels.getHeight() * 4L;
        if (bytes > MAX_CACHE_BYTES) {
            pixels.close();
            throw new IOException("PNG exceeds the decoded image cache limit (256 MiB)");
        }
        DynamicTexture texture = null;
        try {
            texture = new DynamicTexture(pixels);
            ResourceLocation id = Minecraft.getInstance().getTextureManager().register("dialogue_studio_" + owner, texture);
            return new Entry(id, texture, List.of(), null, bytes);
        } catch (RuntimeException exception) {
            if (texture != null) texture.close();
            else pixels.close();
            throw exception;
        }
    }

    private static Entry loadGif(InputStream input, String owner) throws IOException {
        DialogueGifDecoder.Animation animation = DialogueGifDecoder.read(input);
        List<NativeImage> frames = new ArrayList<>(animation.frames().size());
        NativeImage upload = null;
        DynamicTexture texture = null;
        try {
            for (int[] argb : animation.frames()) {
                NativeImage image = new NativeImage(animation.width(), animation.height(), false);
                frames.add(image);
                for (int y = 0; y < animation.height(); y++) {
                    for (int x = 0; x < animation.width(); x++) {
                        int color = argb[y * animation.width() + x];
                        // ImageIO supplies ARGB; NativeImage expects ABGR.
                        int abgr = (color & 0xFF00FF00) | (color >>> 16 & 255) | (color & 255) << 16;
                        image.setPixelRGBA(x, y, abgr);
                    }
                }
            }
            // One GPU texture per animation; decoded frames stay in bounded native memory.
            upload = new NativeImage(animation.width(), animation.height(), false);
            upload.copyFrom(frames.get(0));
            texture = new DynamicTexture(upload);
            texture.setFilter(false, false);
            ResourceLocation id = Minecraft.getInstance().getTextureManager().register("dialogue_studio_" + owner, texture);
            long bytes = (long) animation.width() * animation.height() * 4L * (frames.size() + 1L);
            return new Entry(id, texture, List.copyOf(frames), animation.timing(), bytes);
        } catch (RuntimeException exception) {
            if (texture != null) texture.close();
            else if (upload != null) upload.close();
            for (NativeImage frame : frames) frame.close();
            throw exception;
        }
    }

    private static final class Entry {
        private final ResourceLocation id;
        private final DynamicTexture texture;
        private final List<NativeImage> frames;
        private final DialogueGifDecoder.Timing timing;
        private final long bytes;
        private long startedNanos = System.nanoTime();
        private int uploadedFrame;

        private Entry(ResourceLocation id, DynamicTexture texture, List<NativeImage> frames, DialogueGifDecoder.Timing timing, long bytes) {
            this.id = id;
            this.texture = texture;
            this.frames = frames;
            this.timing = timing;
            this.bytes = bytes;
        }

        private ResourceLocation currentTexture(boolean animate) {
            if (timing != null && frames.size() > 1) {
                int frame = animate ? timing.frameAt((System.nanoTime() - startedNanos) / 1_000_000L) : 0;
                if (frame != uploadedFrame) {
                    texture.getPixels().copyFrom(frames.get(frame));
                    texture.upload();
                    uploadedFrame = frame;
                }
            }
            return id;
        }
    }

    private static void makeRoom(long incomingBytes) {
        Iterator<Entry> iterator = ENTRIES.values().iterator();
        while (iterator.hasNext() && (ENTRIES.size() >= MAX_ENTRIES || cachedBytes + incomingBytes > MAX_CACHE_BYTES)) {
            release(iterator.next());
            iterator.remove();
        }
    }

    private static void release(Entry entry) {
        if (entry.id != null) Minecraft.getInstance().getTextureManager().release(entry.id);
        for (NativeImage frame : entry.frames) frame.close();
        cachedBytes -= entry.bytes;
    }

    public static void clearAll() {
        for (Entry entry : ENTRIES.values()) release(entry);
        ENTRIES.clear();
        cachedBytes = 0L;
    }

    @Mod.EventBusSubscriber(modid = DialogueStudio.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ReloadEvents {
        @SubscribeEvent
        public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> clearAll());
        }
    }

    @Mod.EventBusSubscriber(modid = DialogueStudio.MODID, value = Dist.CLIENT)
    public static final class ConnectionEvents {
        @SubscribeEvent
        public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            clearAll();
        }
    }
}
