package com.benji.dialoguestudio.client.dialogue;

import com.benji.dialoguestudio.DialogueStudio;
import com.benji.dialoguestudio.dialogue.data.DialogueDefinition;
import com.benji.dialoguestudio.dialogue.text.DialogueMarkdown;
import com.benji.dialoguestudio.dialogue.text.DialogueRichTextUtil;
import com.benji.dialoguestudio.dialogue.text.DialogueTextRenderUtil;
import com.benji.dialoguestudio.network.dialogueengine.DialogueNetwork;
import com.google.gson.Gson;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.awt.Color;
import java.util.*;

@Mod.EventBusSubscriber(modid = DialogueStudio.MODID, value = Dist.CLIENT)
public final class DialogueClient {

    private static final Gson GSON = new Gson();

    private static final ResourceLocation DEFAULT_FRAME = ResourceLocation.fromNamespaceAndPath(DialogueStudio.MODID, "textures/gui/dialogue/frame_dialog.png");

    private static final ResourceLocation DEFAULT_BACKGROUND = ResourceLocation.fromNamespaceAndPath(DialogueStudio.MODID, "textures/gui/dialogue/dithering_gradient.png");

    private static DialogueDefinition definition;

    private static UUID sessionId;
    private static ResourceLocation dialogueId;

    private static int lineIndex;
    private static boolean nodeMode;
    private static String currentNodeId;
    private static List<Boolean> enabledChoices = List.of();
    private static int selectedChoice;
    private static int hoveredChoice = -1;

    private static boolean waitingForNodeState;

    private static int revealedChars;
    private static int typingDelay;
    private static int holdTicks;
    private static int totalTicks;

    private static boolean active;
    private static boolean ending;
    private static int endingTicks;

    private static String currentText = "";
    private static DialogueMarkdown.Result currentMarkdown = DialogueMarkdown.parse("", false);
    private static int[] revealTicks = new int[0];

    private static String previousSprite;
    private static float spriteMoveFromX;
    private static int spriteMoveAge = 1000;
    private static int spriteTransitionAge = 1000;

    private static int voiceLetterCounter;

    private DialogueClient() {
    }


    public static void start(UUID newSessionId, ResourceLocation newDialogueId, String json) {
        try {
            DialogueDefinition loaded = GSON.fromJson(json, DialogueDefinition.class);

            boolean graph = loaded != null && loaded.hasGraph();

            boolean legacy = loaded != null && loaded.lines != null && !loaded.lines.isEmpty();

            if (loaded == null || (!graph && !legacy)) {

                DialogueNetwork.finish(newSessionId);
                return;
            }

            definition = loaded;
            sessionId = newSessionId;
            dialogueId = newDialogueId;

            lineIndex = 0;

            nodeMode = graph;
            currentNodeId = null;
            enabledChoices = List.of();
            selectedChoice = 0;
            hoveredChoice = -1;
            waitingForNodeState = graph;

            revealedChars = 0;
            typingDelay = 0;
            holdTicks = 0;
            totalTicks = 0;

            ending = false;
            endingTicks = 0;
            active = true;

            previousSprite = null;
            spriteTransitionAge = 1000;
            spriteMoveAge = 1000;

            voiceLetterCounter = 0;

            if (!graph) {
                updateCurrentText();

                spriteMoveFromX = resolveSpriteTargetX(currentLine());
            } else {
                currentText = "";
                currentMarkdown = DialogueMarkdown.parse("", false);
                revealTicks = new int[0];
                spriteMoveFromX = definition.layout.sprite_center_x;
            }

        } catch (Exception exception) {
            exception.printStackTrace();
            DialogueNetwork.finish(newSessionId);
        }
    }

    public static void setNodeState(UUID targetSession, String nodeId, List<Boolean> availability) {
        if (!active || !nodeMode || sessionId == null || !sessionId.equals(targetSession) || definition == null) {
            return;
        }

        if ("__oasiso_end__".equals(nodeId)) {
            waitingForNodeState = false;
            closeChoiceInputScreen();
            ending = true;
            endingTicks = 0;
            return;
        }

        DialogueDefinition.Node nextNode = definition.nodes != null ? definition.nodes.get(nodeId) : null;

        if (nextNode == null) {
            return;
        }

        DialogueDefinition.Line oldLine = currentLineOrNull();

        float oldX = oldLine != null ? currentSpriteX(0.0F) : definition.layout.sprite_center_x;

        String oldSprite = oldLine != null ? oldLine.sprite : null;

        currentNodeId = nodeId;
        enabledChoices = availability != null ? List.copyOf(availability) : List.of();

        selectedChoice = firstSelectableChoice();

        hoveredChoice = -1;
        waitingForNodeState = false;

        DialogueDefinition.Line newLine = currentLineOrNull();

        previousSprite = oldSprite;

        spriteMoveFromX = oldX;

        spriteMoveAge = 0;

        String nextSprite = newLine != null ? newLine.sprite : null;

        if (!Objects.equals(oldSprite, nextSprite)) {
            spriteTransitionAge = 0;
        } else {
            spriteTransitionAge = 1000;
        }

        revealedChars = 0;
        typingDelay = 0;
        holdTicks = 0;
        voiceLetterCounter = 0;

        updateCurrentText();

        closeChoiceInputScreen();
    }


    public static void cancel(UUID targetSession) {
        if (!active || sessionId == null || !sessionId.equals(targetSession)) {
            return;
        }

        reset();
    }


    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null || minecraft.player == null) {
            reset();
            return;
        }

        if (minecraft.isPaused()) {
            return;
        }

        totalTicks++;
        spriteMoveAge++;
        spriteTransitionAge++;

        if (ending) {
            endingTicks++;

            if (endingTicks >= getFadeTicks()) {
                finishLegacy();
            }

            return;
        }

        if (nodeMode && waitingForNodeState) {
            return;
        }

        if (revealedChars < currentText.length()) {
            if (typingDelay > 0) {
                typingDelay--;
                return;
            }

            char character = currentText.charAt(revealedChars);

            revealTicks[revealedChars] = totalTicks;

            revealedChars++;

            playVoice(character);

            typingDelay = Math.max(0, getCharTicks() - 1) + punctuationPause(character);

            return;
        }

        if (nodeMode) {
            tickGraphNode();
            return;
        }

        holdTicks++;

        if (holdTicks >= getHoldTicks()) {
            advanceLegacyLine();
        }
    }


    private static void tickGraphNode() {
        DialogueDefinition.Node node = currentNode();

        if (node == null) {
            return;
        }

        String type = node.type != null ? node.type.toLowerCase(Locale.ROOT) : "line";

        if ("choice".equals(type)) {
            ensureChoiceInputScreen();
            return;
        }

        if (!"line".equals(type)) {
            return;
        }

        holdTicks++;

        if (holdTicks >= getHoldTicks() && !waitingForNodeState) {

            waitingForNodeState = true;

            DialogueNetwork.advanceNode(sessionId, currentNodeId);
        }
    }


    private static void advanceLegacyLine() {
        if (lineIndex >= definition.lines.size() - 1) {

            ending = true;
            endingTicks = 0;
            return;
        }

        DialogueDefinition.Line old = currentLine();

        float oldX = currentSpriteX(0.0F);

        String oldSprite = old.sprite;

        lineIndex++;

        DialogueDefinition.Line next = currentLine();

        previousSprite = oldSprite;

        spriteMoveFromX = oldX;

        spriteMoveAge = 0;

        if (!Objects.equals(oldSprite, next.sprite)) {
            spriteTransitionAge = 0;
        } else {
            spriteTransitionAge = 1000;
        }

        revealedChars = 0;
        typingDelay = 0;
        holdTicks = 0;
        voiceLetterCounter = 0;

        updateCurrentText();
    }


    private static void updateCurrentText() {
        DialogueDefinition.Line line = currentLineOrNull();

        String source = resolveText(line);
        currentMarkdown = DialogueMarkdown.parse(source, markdownEnabled(line));
        currentText = currentMarkdown.text();
        revealTicks = new int[currentText.length()];

        Arrays.fill(revealTicks, Integer.MIN_VALUE / 2);
    }


    private static String resolveText(DialogueDefinition.Line line) {
        if (line == null) {
            return "";
        }

        if (line.literal != null) {
            return line.literal;
        }

        if (line.text != null) {
            return I18n.get(line.text);
        }

        return "";
    }


    private static String resolveChoiceText(DialogueDefinition.Choice choice) {
        if (choice == null) {
            return "";
        }

        if (choice.literal != null) {
            return choice.literal;
        }

        if (choice.text != null) {
            return I18n.get(choice.text);
        }

        return "<choice>";
    }


    private static void finishLegacy() {
        UUID finished = sessionId;

        reset();

        if (finished != null) {
            DialogueNetwork.finish(finished);
        }
    }


    private static void reset() {
        closeChoiceInputScreen();

        definition = null;
        sessionId = null;
        dialogueId = null;

        lineIndex = 0;

        nodeMode = false;
        currentNodeId = null;
        enabledChoices = List.of();
        selectedChoice = 0;
        hoveredChoice = -1;
        waitingForNodeState = false;

        revealedChars = 0;
        typingDelay = 0;
        holdTicks = 0;
        totalTicks = 0;

        ending = false;
        endingTicks = 0;

        currentText = "";
        currentMarkdown = DialogueMarkdown.parse("", false);
        revealTicks = new int[0];

        previousSprite = null;
        spriteTransitionAge = 1000;
        spriteMoveAge = 1000;

        voiceLetterCounter = 0;
        active = false;
    }


    private static void playVoice(char character) {
        if (!Character.isLetterOrDigit(character)) {
            return;
        }

        DialogueDefinition.Line line = currentLineOrNull();

        if (line == null) {
            return;
        }

        voiceLetterCounter++;

        int every = line.voice_every != null ? line.voice_every : definition.voice_every;

        every = Math.max(1, every);

        if ((voiceLetterCounter - 1) % every != 0) {
            return;
        }

        ResourceLocation soundId = resolveVoiceId(line);

        if (soundId == null) {
            return;
        }

        float pitch = line.voice_pitch != null ? line.voice_pitch : definition.voice_pitch;

        float volume = line.voice_volume != null ? line.voice_volume : definition.voice_volume;

        SoundSource source = resolveVoiceSource(line);
        SimpleSoundInstance sound = new SimpleSoundInstance(soundId, source, volume, pitch, RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, 0.0D, 0.0D, 0.0D, true);
        Minecraft.getInstance().getSoundManager().play(sound);
    }


    private static ResourceLocation resolveVoiceId(DialogueDefinition.Line line) {
        String value = line.voice != null ? line.voice : definition.voice;

        if (value == null || value.isBlank()) {
            return null;
        }

        return ResourceLocation.tryParse(value);
    }


    private static SoundSource resolveVoiceSource(DialogueDefinition.Line line) {
        String value = line.voice_source != null ? line.voice_source : definition.voice_source;

        if (value == null || value.isBlank()) {
            return SoundSource.MASTER;
        }

        try {
            return SoundSource.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return SoundSource.MASTER;
        }
    }


    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (Minecraft.getInstance().screen != null) {
            return;
        }

        render(event.getGuiGraphics());
    }


    @SubscribeEvent
    public static void onRenderScreen(ScreenEvent.Render.Post event) {
        render(event.getGuiGraphics());
    }


    private static void render(GuiGraphics graphics) {
        if (!active || definition == null) {
            return;
        }

        if (nodeMode && waitingForNodeState && currentNodeId == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();

        float partialTick = minecraft.getFrameTime();

        float alpha = globalAlpha(partialTick);

        if (alpha <= 0.001F) {
            return;
        }

        float time = (totalTicks + partialTick) / 20.0F;

        renderBackground(graphics, time, alpha);

        renderDialogue(graphics, minecraft.font, time, partialTick, alpha);
    }


    private static void renderBackground(GuiGraphics graphics, float time, float alpha) {
        DialogueDefinition.Layout layout = definition.layout;

        ResourceLocation texture = currentBackground();

        int screenW = graphics.guiWidth();

        int screenH = graphics.guiHeight();

        float scale = Math.max(screenW / (float) layout.canvas_width, screenH / (float) layout.canvas_height) * 1.05F;

        float width = layout.canvas_width * scale;
        float height = layout.canvas_height * scale;

        float bob = (float) Math.sin(time * definition.background_speed) * definition.background_bob * scale;

        float x = (screenW - width) * 0.5F;
        float y = (screenH - height) * 0.5F + bob;

        PoseStack pose = graphics.pose();

        pose.pushPose();

        pose.translate(x, y, 300.0F);
        pose.scale(scale, scale, 1.0F);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        graphics.setColor(1.0F, 1.0F, 1.0F, definition.background_alpha * alpha);
        graphics.blit(texture, 0, 0, 0, 0, layout.canvas_width, layout.canvas_height, layout.canvas_width, layout.canvas_height);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        pose.popPose();
    }


    private static void renderDialogue(GuiGraphics graphics, Font font, float time, float partialTick, float alpha) {
        DialogueDefinition.Layout layout = definition.layout;

        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();
        float scale = Math.min(screenW / (float) layout.canvas_width, screenH / (float) layout.canvas_height);
        float originX = (screenW - layout.canvas_width * scale) * 0.5F;
        float originY = (screenH - layout.canvas_height * scale) * 0.5F;

        PoseStack pose = graphics.pose();

        pose.pushPose();

        pose.translate(originX, originY, 400.0F);
        pose.scale(scale, scale, 1.0F);

        renderSprite(graphics, partialTick, alpha);
        renderFrame(graphics, alpha);
        renderText(graphics, font, time, partialTick, alpha, scale);

        if (nodeMode && !waitingForNodeState && isCurrentChoiceNode() && revealedChars >= currentText.length()) {

            renderChoices(graphics, font, alpha);
        }

        pose.popPose();
    }


    private static void renderSprite(GuiGraphics graphics, float partialTick, float alpha) {
        DialogueDefinition.Line line = currentLineOrNull();

        if (line == null || line.sprite == null) {
            return;
        }

        ResourceLocation sprite = ResourceLocation.tryParse(line.sprite);

        if (sprite == null) {
            return;
        }

        String transition = spriteTransition(line);

        int transitionTicks = spriteTransitionTicks(line);
        float progress = Mth.clamp((spriteTransitionAge + partialTick) / transitionTicks, 0.0F, 1.0F);
        float x = currentSpriteX(partialTick);
        int width = spriteWidth(line);
        int height = spriteHeight(line);

        int y = definition.layout.sprite_y;

        if (("fade".equals(transition) || "fade_up".equals(transition)) && previousSprite != null && progress < 1.0F) {

            ResourceLocation old = ResourceLocation.tryParse(previousSprite);

            if (old != null) {
                int visibleHeight = Math.max(0, Math.round(height * (1.0F - progress)));

                if (visibleHeight > 0) {
                    graphics.setColor(1.0F, 1.0F, 1.0F, alpha);

                    graphics.blit(old, Math.round(spriteMoveFromX), y, 0, 0, width, visibleHeight, width, height);
                }
            }
        }
        PoseStack pose = graphics.pose();

        pose.pushPose();

        float centerX = x + width * 0.5F;
        float centerY = y + height * 0.5F;

        float drawAlpha = alpha;

        switch (transition) {
            case "bounce" -> {
                float age = spriteTransitionAge + partialTick;
                float damping = (float) Math.exp(-age * 0.22F);
                float bounceY = -(float) Math.sin(age * 0.92F) * damping * 4.0F;
                float bounceScale = 1.0F + Math.max(0.0F, (float) Math.sin(age * 0.92F)) * damping * 0.035F;

                pose.translate(centerX, centerY + bounceY, 0.0F);

                pose.scale(bounceScale, bounceScale, 1.0F);

                pose.translate(-width * 0.5F, -height * 0.5F, 0.0F);
            }

            case "sway" -> {
                float age = spriteTransitionAge + partialTick;
                float damping = (float) Math.exp(-age * 0.20F);
                float sway = (float) Math.sin(age * 0.95F) * damping;

                pose.translate(centerX + sway * 3.5F, centerY, 0.0F);

                pose.mulPose(Axis.ZP.rotationDegrees(sway * 5.0F));

                pose.translate(-width * 0.5F, -height * 0.5F, 0.0F);
            }

            case "fade", "fade_up" -> {
                drawAlpha *= progress;

                pose.translate(x, y, 0.0F);
            }

            default -> pose.translate(x, y, 0.0F);
        }

        graphics.setColor(1.0F, 1.0F, 1.0F, drawAlpha);
        graphics.blit(sprite, 0, 0, 0, 0, width, height, width, height);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        pose.popPose();
    }


    private static void renderFrame(GuiGraphics graphics, float alpha) {
        DialogueDefinition.Layout layout = definition.layout;

        ResourceLocation frame = currentFrame();
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        graphics.blit(frame, layout.frame_x, layout.frame_y, 0, 0, layout.frame_width, layout.frame_height, layout.frame_width, layout.frame_height);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }


    private static void renderText(GuiGraphics graphics, Font font, float time, float partialTick, float alpha, float dialogueCanvasScale) {
        DialogueDefinition.Line line = currentLineOrNull();

        if (line == null) {
            return;
        }

        DialogueDefinition.Layout layout = definition.layout;

        int maxWidth = Mth.floor(layout.text_width / layout.text_scale);

        List<Glyph> glyphs = layoutGlyphs(font, currentText, maxWidth);

        PoseStack pose = graphics.pose();

        pose.pushPose();

        pose.translate(layout.text_x, layout.text_y, 10.0F);

        pose.scale(layout.text_scale, layout.text_scale, 1.0F);

        int alphaByte = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);

        List<String> baseEffects = textEffects(line);

        String locale = currentTextLocale();

        for (Glyph glyph : glyphs) {

            if (glyph.index >= revealedChars) {
                continue;
            }

            DialogueRichTextUtil.ResolvedStyle rich = DialogueRichTextUtil.resolve(line, currentText, glyph.index, locale);

            List<String> effects = rich.effects != null ? normalizedEffects(rich.effects) : baseEffects;

            DialogueDefinition.TextAnimation animation = rich.animation;

            float age = totalTicks + partialTick - revealTicks[glyph.index];

            float x = glyph.x;
            float y = glyph.y;

            float glyphScale = 1.0F;

            for (String effect : effects) {

                if (effect == null) {
                    continue;
                }

                switch (effect.toLowerCase(Locale.ROOT)) {
                    case "wave" -> {
                        float amplitude = animation.wave_amplitude != null ? animation.wave_amplitude : 0.85F;
                        float speed = animation.wave_speed != null ? animation.wave_speed : 5.0F;
                        float frequency = animation.wave_frequency != null ? animation.wave_frequency : 0.55F;

                        y += (float) Math.sin(time * speed + glyph.index * frequency) * amplitude;
                    }

                    case "shake" -> {
                        float strength = animation.shake_strength != null ? animation.shake_strength : 1.0F;

                        long seed = glyph.index * 734287L + totalTicks * 912271L;

                        x += hashOffset(seed) * strength;
                        y += hashOffset(seed + 19L) * strength;
                    }

                    case "explode" -> {
                        int duration = animation.explode_ticks != null ? Math.max(1, animation.explode_ticks) : 6;

                        float amount = animation.explode_amount != null ? Math.max(0.0F, animation.explode_amount) : 0.85F;
                        float progress = smooth(age / duration);

                        glyphScale *= 1.0F + (1.0F - progress) * amount;
                    }

                    case "slide", "linear" -> {

                        int duration = animation.slide_ticks != null ? Math.max(1, animation.slide_ticks) : 6;

                        float distance = animation.slide_distance != null ? animation.slide_distance : 13.0F;
                        float progress = smooth(age / duration);

                        x -= (1.0F - progress) * distance;
                    }
                }
            }

            int rgb = letterColor(line, glyph, maxWidth, time, rich);

            int color = (alphaByte << 24) | rgb;

            PoseStack glyphPose = graphics.pose();

            glyphPose.pushPose();

            glyphPose.translate(x, y, 0.0F);

            if (glyphScale != 1.0F) {
                int glyphWidth = glyph.width;

                glyphPose.translate(glyphWidth * 0.5F, font.lineHeight * 0.5F, 0.0F);

                glyphPose.scale(glyphScale, glyphScale, 1.0F);

                glyphPose.translate(-glyphWidth * 0.5F, -font.lineHeight * 0.5F, 0.0F);
            }

            DialogueTextRenderUtil.GlyphStyle glyphStyle = effectiveGlyphStyle(line, glyph.index, rich);

            int outlineRgb = outlineColor(line, glyph, maxWidth, time, rich);
            int outlineColor = (alphaByte << 24) | outlineRgb;
            float outlineThickness = outlineThickness(line, rich);

            float glyphToGuiScale = dialogueCanvasScale * layout.text_scale * glyphScale;

            DialogueTextRenderUtil.drawGlyph(graphics, font, glyph.character, color, outlineColor, outlineThickness, glyphStyle, glyphToGuiScale);

            glyphPose.popPose();
        }

        pose.popPose();
    }


    private static void renderChoices(GuiGraphics graphics, Font font, float alpha) {
        DialogueDefinition.Node node = currentNode();

        if (node == null || node.choices == null || node.choices.isEmpty()) {
            return;
        }

        DialogueDefinition.Layout layout = definition.layout;

        List<Integer> visible = visibleChoiceIndices();

        if (visible.isEmpty()) {
            return;
        }

        int rowHeight = Math.max(7, layout.choice_line_height);

        int startY = Math.min(layout.choice_y, layout.canvas_height - visible.size() * rowHeight - 2);

        PoseStack pose = graphics.pose();

        pose.pushPose();

        pose.translate(layout.choice_x, startY, 15.0F);

        pose.scale(layout.choice_scale, layout.choice_scale, 1.0F);

        int alphaByte = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);

        int visualRow = 0;

        for (int choiceIndex : visible) {

            DialogueDefinition.Choice choice = node.choices.get(choiceIndex);

            boolean enabled = isChoiceEnabled(choiceIndex);
            boolean selected = choiceIndex == (hoveredChoice >= 0 ? hoveredChoice : selectedChoice);

            String text = resolveChoiceText(choice);

            int rgb;

            if (!enabled) {
                rgb = parseColor(layout.choice_disabled_color);
            } else if (selected) {
                rgb = parseColor(layout.choice_selected_color);
            } else {
                rgb = parseColor(layout.choice_color);
            }

            String prefix = (selected ? "> " : "  ") + (visualRow + 1) + ". ";

            graphics.drawString(font, prefix + text, 0, visualRow * rowHeight, (alphaByte << 24) | rgb, false);

            visualRow++;
        }

        pose.popPose();
    }

    public static void updateChoiceHover(double mouseX, double mouseY) {
        if (!canChoose()) {
            hoveredChoice = -1;
            return;
        }

        int choiceIndex = choiceAtScreen(mouseX, mouseY);

        hoveredChoice = choiceIndex >= 0 && isChoiceEnabled(choiceIndex) ? choiceIndex : -1;
    }

    public static void clearChoiceHover() {
        hoveredChoice = -1;
    }

    public static boolean clickChoice(double mouseX, double mouseY) {
        if (!canChoose()) {
            return false;
        }

        int choiceIndex = choiceAtScreen(mouseX, mouseY);

        if (choiceIndex < 0 || !isChoiceEnabled(choiceIndex)) {
            return false;
        }

        selectedChoice = choiceIndex;
        hoveredChoice = choiceIndex;
        submitSelectedChoice();

        return true;
    }


    public static void moveChoiceSelection(int direction) {
        if (!canChoose()) {
            return;
        }

        hoveredChoice = -1;

        List<Integer> selectable = selectableChoiceIndices();

        if (selectable.isEmpty()) {
            return;
        }

        int current = selectable.indexOf(selectedChoice);

        if (current < 0) {
            selectedChoice = selectable.get(0);
            return;
        }

        int next = Math.floorMod(current + direction, selectable.size());

        selectedChoice = selectable.get(next);
    }


    public static void selectChoiceNumber(int number) {
        if (!canChoose()) {
            return;
        }

        hoveredChoice = -1;

        List<Integer> selectable = selectableChoiceIndices();

        int local = number - 1;

        if (local < 0 || local >= selectable.size()) {
            return;
        }

        selectedChoice = selectable.get(local);

        submitSelectedChoice();
    }


    public static void submitSelectedChoice() {
        if (!canChoose() || !isChoiceEnabled(selectedChoice)) {
            return;
        }

        waitingForNodeState = true;
        hoveredChoice = -1;

        closeChoiceInputScreen();

        DialogueNetwork.choose(sessionId, currentNodeId, selectedChoice);
    }


    public static boolean canChoose() {
        return active && nodeMode && !waitingForNodeState && isCurrentChoiceNode() && revealedChars >= currentText.length();
    }


    private static int choiceAtScreen(double mouseX, double mouseY) {
        Minecraft minecraft = Minecraft.getInstance();

        DialogueDefinition.Node node = currentNode();

        if (minecraft == null || definition == null || node == null || node.choices == null) {
            return -1;
        }

        DialogueDefinition.Layout layout = definition.layout;

        int screenW = minecraft.getWindow().getGuiScaledWidth();
        int screenH = minecraft.getWindow().getGuiScaledHeight();

        float canvasScale = Math.min(screenW / (float) layout.canvas_width, screenH / (float) layout.canvas_height);
        float originX = (screenW - layout.canvas_width * canvasScale) * 0.5F;
        float originY = (screenH - layout.canvas_height * canvasScale) * 0.5F;

        List<Integer> visible = visibleChoiceIndices();

        int rowHeight = Math.max(7, layout.choice_line_height);
        int startY = Math.min(layout.choice_y, layout.canvas_height - visible.size() * rowHeight - 2);

        double localX = (mouseX - originX) / canvasScale;
        double localY = (mouseY - originY) / canvasScale;

        if (localX < layout.choice_x || localX > layout.choice_x + layout.choice_width || localY < startY) {
            return -1;
        }

        double scaledRowHeight = rowHeight * layout.choice_scale;

        if (scaledRowHeight <= 0.0D) {
            return -1;
        }
        double choiceLocalY = (localY - startY) / layout.choice_scale;

        int row = (int) Math.floor(choiceLocalY / rowHeight);

        if (row < 0 || row >= visible.size()) {
            return -1;
        }

        return visible.get(row);
    }

    private static void ensureChoiceInputScreen() {
        if (!canChoose()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.screen == null) {
            minecraft.setScreen(new DialogueChoiceInputScreen());
        }
    }

    private static void closeChoiceInputScreen() {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.screen instanceof DialogueChoiceInputScreen) {
            minecraft.setScreen(null);
        }
    }

    private static List<Integer> visibleChoiceIndices() {
        DialogueDefinition.Node node = currentNode();

        if (node == null || node.choices == null) {
            return List.of();
        }

        List<Integer> result = new ArrayList<>();

        for (int i = 0; i < node.choices.size(); i++) {
            DialogueDefinition.Choice choice = node.choices.get(i);
            boolean enabled = isChoiceEnabled(i);
            String mode = choice != null && choice.when_unavailable != null ? choice.when_unavailable : "hide";

            if (enabled || !"hide".equalsIgnoreCase(mode)) {
                result.add(i);
            }
        }

        return result;
    }


    private static List<Integer> selectableChoiceIndices() {
        List<Integer> result = new ArrayList<>();

        for (int index : visibleChoiceIndices()) {
            if (isChoiceEnabled(index)) {
                result.add(index);
            }
        }

        return result;
    }


    private static int firstSelectableChoice() {
        DialogueDefinition.Node node = currentNode();

        if (node == null || node.choices == null) {
            return 0;
        }

        for (int i = 0; i < node.choices.size(); i++) {

            if (isChoiceEnabled(i)) {
                return i;
            }
        }

        return 0;
    }


    private static boolean isChoiceEnabled(int index) {
        return index >= 0 && index < enabledChoices.size() && Boolean.TRUE.equals(enabledChoices.get(index));
    }


    private static boolean isCurrentChoiceNode() {
        DialogueDefinition.Node node = currentNode();

        return node != null && "choice".equalsIgnoreCase(node.type);
    }


    private static int letterColor(DialogueDefinition.Line line, Glyph glyph, int maxWidth, float time, DialogueRichTextUtil.ResolvedStyle rich) {
        if (rich.gradient != null) {

            if (rich.gradient.size() >= 2) {
                int span = Math.max(1, rich.gradientEnd - rich.gradientStart - 1);

                float t = Mth.clamp((glyph.index - rich.gradientStart) / (float) span, 0.0F, 1.0F);

                return gradientColor(rich.gradient, t);
            }

        } else {
            List<String> gradient = line.text_gradient != null ? line.text_gradient : definition.text_gradient;

            if (gradient != null && gradient.size() >= 2) {

                float t = Mth.clamp(glyph.x / (float) Math.max(1, maxWidth), 0.0F, 1.0F);

                return gradientColor(gradient, t);
            }
        }

        String value = rich.color != null ? rich.color : (line.text_color != null ? line.text_color : definition.text_color);

        if (value == null || value.equals("white")) {

            String legacy = line.text_style != null ? line.text_style : definition.text_style;

            if (legacy != null && rich.color == null) {
                value = legacy;
            }
        }

        if ("rainbow".equalsIgnoreCase(value)) {
            float hue = (glyph.index * 0.095F + time * 0.055F) % 1.0F;

            return Color.HSBtoRGB(hue, 0.76F, 1.0F) & 0xFFFFFF;
        }

        return parseColor(value);
    }


    private static String currentTextLocale() {
        DialogueDefinition.Line line = currentLineOrNull();

        if (line == null || line.literal != null) {
            return null;
        }

        try {
            return Minecraft.getInstance().getLanguageManager().getSelected();
        } catch (Exception ignored) {
            return null;
        }
    }


    private static List<String> textEffects(DialogueDefinition.Line line) {
        if (line.text_effects != null) {
            return normalizedEffects(line.text_effects);
        }

        if (definition.text_effects != null) {
            return normalizedEffects(definition.text_effects);
        }

        String legacy = line.text_effect != null ? line.text_effect : definition.text_effect;

        if (legacy == null || legacy.isBlank() || "normal".equalsIgnoreCase(legacy)) {
            return List.of();
        }

        return List.of(legacy.toLowerCase(Locale.ROOT));
    }


    private static List<String> normalizedEffects(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> result = new LinkedHashSet<>();

        for (String value : values) {

            if (value == null || value.isBlank() || "normal".equalsIgnoreCase(value)) {
                continue;
            }

            result.add(value.trim().toLowerCase(Locale.ROOT));
        }

        return List.copyOf(result);
    }


    private static int parseColor(String value) {
        if (value == null) {
            return 0xFFFFFF;
        }

        value = value.trim().toLowerCase(Locale.ROOT);

        return switch (value) {
            case "blue" -> 0x4AA3FF;
            case "red" -> 0xFF4D55;
            case "gold", "golden" -> 0xFFD45A;
            case "green" -> 0x55E878;
            case "white" -> 0xFFFFFF;
            case "black" -> 0x000000;
            case "purple" -> 0xB76CFF;
            case "cyan" -> 0x42F2E1;
            default -> parseHex(value);
        };
    }


    private static int parseHex(String value) {
        try {
            if (value.startsWith("#")) {
                value = value.substring(1);
            }

            if (value.startsWith("0x")) {
                value = value.substring(2);
            }

            if (value.length() == 3) {
                value = "" + value.charAt(0) + value.charAt(0) + value.charAt(1) + value.charAt(1) + value.charAt(2) + value.charAt(2);
            }

            return Integer.parseInt(value, 16) & 0xFFFFFF;

        } catch (Exception ignored) {
            return 0xFFFFFF;
        }
    }


    private static int gradientColor(List<String> colors, float t) {
        int sections = colors.size() - 1;

        float scaled = t * sections;

        int index = Mth.clamp((int) Math.floor(scaled), 0, sections - 1);

        float local = scaled - index;

        int a = parseColor(colors.get(index));

        int b = parseColor(colors.get(index + 1));

        int ar = a >> 16 & 255;
        int ag = a >> 8 & 255;
        int ab = a & 255;

        int br = b >> 16 & 255;
        int bg = b >> 8 & 255;
        int bb = b & 255;

        int r = Mth.lerpInt(local, ar, br);

        int g = Mth.lerpInt(local, ag, bg);

        int bl = Mth.lerpInt(local, ab, bb);

        return r << 16 | g << 8 | bl;
    }


    private static float currentSpriteX(float partialTick) {
        DialogueDefinition.Line line = currentLineOrNull();

        if (line == null) {
            return definition.layout.sprite_center_x;
        }

        float target = resolveSpriteTargetX(line);

        int ticks = line.sprite_move_ticks != null ? line.sprite_move_ticks : definition.sprite_move_ticks;

        if (ticks <= 0) {
            return target;
        }

        float p = smooth((spriteMoveAge + partialTick) / ticks);

        return Mth.lerp(p, spriteMoveFromX, target);
    }


    private static float resolveSpriteTargetX(DialogueDefinition.Line line) {
        if (line.sprite_x != null) {
            return line.sprite_x;
        }

        String position = line.sprite_position != null ? line.sprite_position : definition.sprite_position;

        DialogueDefinition.Layout layout = definition.layout;

        if (position == null) {
            position = "center";
        }

        return switch (position.toLowerCase(Locale.ROOT)) {
            case "left" -> layout.sprite_left_x;

            case "right" -> layout.sprite_right_x;

            default -> layout.sprite_center_x;
        };
    }


    private static int spriteWidth(DialogueDefinition.Line line) {
        return line.sprite_width != null ? line.sprite_width : definition.layout.sprite_width;
    }


    private static int spriteHeight(DialogueDefinition.Line line) {
        return line.sprite_height != null ? line.sprite_height : definition.layout.sprite_height;
    }


    private static String spriteTransition(DialogueDefinition.Line line) {
        String value = line.sprite_transition != null ? line.sprite_transition : definition.sprite_transition;

        return value != null ? value.toLowerCase(Locale.ROOT) : "none";
    }


    private static int spriteTransitionTicks(DialogueDefinition.Line line) {
        return Math.max(1, line.sprite_transition_ticks != null ? line.sprite_transition_ticks : definition.sprite_transition_ticks);
    }


    private static ResourceLocation currentFrame() {
        DialogueDefinition.Line line = currentLineOrNull();

        String value = line != null && line.frame != null ? line.frame : definition.frame;

        ResourceLocation parsed = value != null ? ResourceLocation.tryParse(value) : null;

        return parsed != null ? parsed : DEFAULT_FRAME;
    }


    private static ResourceLocation currentBackground() {
        DialogueDefinition.Line line = currentLineOrNull();

        String value = line != null && line.background != null ? line.background : definition.background;

        ResourceLocation parsed = value != null ? ResourceLocation.tryParse(value) : null;

        return parsed != null ? parsed : DEFAULT_BACKGROUND;
    }


    private static int getCharTicks() {
        DialogueDefinition.Line line = currentLineOrNull();

        Integer custom = line != null ? line.char_ticks : null;

        return Math.max(1, custom != null ? custom : definition.char_ticks);
    }


    private static int getHoldTicks() {
        DialogueDefinition.Line line = currentLineOrNull();

        Integer custom = line != null ? line.hold_ticks : null;

        return Math.max(1, custom != null ? custom : definition.hold_ticks);
    }


    private static int getFadeTicks() {
        return Math.max(1, definition.fade_ticks);
    }


    private static int punctuationPause(char character) {
        return switch (character) {
            case '.', '!', '?' -> 3;
            case ',', ';', ':' -> 1;
            default -> 0;
        };
    }


    private static float globalAlpha(float partialTick) {
        float fadeIn = smooth((totalTicks + partialTick) / getFadeTicks());

        if (!ending) {
            return fadeIn;
        }

        float fadeOut = 1.0F - smooth((endingTicks + partialTick) / getFadeTicks());

        return fadeIn * fadeOut;
    }


    private static float smooth(float value) {
        value = Mth.clamp(value, 0.0F, 1.0F);

        return value * value * (3.0F - 2.0F * value);
    }


    private static float hashOffset(long value) {
        value ^= value << 13;
        value ^= value >>> 7;
        value ^= value << 17;

        return ((value & 1023L) / 1023.0F - 0.5F) * 1.6F;
    }


    private static DialogueDefinition.Node currentNode() {
        if (!nodeMode || definition == null || definition.nodes == null || currentNodeId == null) {
            return null;
        }

        return definition.nodes.get(currentNodeId);
    }


    private static DialogueDefinition.Line currentLineOrNull() {
        if (definition == null) {
            return null;
        }

        if (nodeMode) {
            DialogueDefinition.Node node = currentNode();

            return node != null ? node.line : null;
        }

        if (definition.lines == null || definition.lines.isEmpty() || lineIndex < 0 || lineIndex >= definition.lines.size()) {
            return null;
        }

        return definition.lines.get(lineIndex);
    }


    private static DialogueDefinition.Line currentLine() {
        DialogueDefinition.Line line = currentLineOrNull();

        if (line == null) {
            throw new IllegalStateException("Dialogue has no current line");
        }

        return line;
    }


    private static List<Glyph> layoutGlyphs(Font font, String text, int maxWidth) {
        List<Glyph> result = new ArrayList<>();

        DialogueDefinition.Line line = currentLineOrNull();
        String locale = currentTextLocale();

        int x = 0;
        int y = 0;
        int i = 0;

        int lineHeight = definition.layout.line_height;

        while (i < text.length()) {
            char character = text.charAt(i);

            if (character == '\n') {
                x = 0;
                y += lineHeight;
                i++;
                continue;
            }

            if (Character.isWhitespace(character)) {
                int glyphWidth = styledGlyphWidth(font, line, text, locale, i, character);

                if (x + glyphWidth <= maxWidth) {
                    result.add(new Glyph(i, character, x, y, glyphWidth));
                    x += glyphWidth;
                } else {
                    x = 0;
                    y += lineHeight;
                }

                i++;
                continue;
            }

            int wordEnd = i;
            while (wordEnd < text.length()) {
                char next = text.charAt(wordEnd);
                if (Character.isWhitespace(next) || next == '\n') break;
                wordEnd++;
            }

            int wordWidth = 0;
            for (int index = i; index < wordEnd; index++) {
                wordWidth += styledGlyphWidth(font, line, text, locale, index, text.charAt(index));
            }

            if (x > 0 && x + wordWidth > maxWidth) {
                x = 0;
                y += lineHeight;
            }

            for (int index = i; index < wordEnd; index++) {
                char letter = text.charAt(index);
                int glyphWidth = styledGlyphWidth(font, line, text, locale, index, letter);

                if (x > 0 && x + glyphWidth > maxWidth) {
                    x = 0;
                    y += lineHeight;
                }

                result.add(new Glyph(index, letter, x, y, glyphWidth));
                x += glyphWidth;
            }

            i = wordEnd;
        }

        return result;
    }


    private static int styledGlyphWidth(Font font, DialogueDefinition.Line line, String text, String locale, int index, char character) {
        DialogueRichTextUtil.ResolvedStyle rich = DialogueRichTextUtil.resolve(line, text, index, locale);

        return DialogueTextRenderUtil.width(font, character, effectiveGlyphStyle(line, index, rich));
    }


    private static DialogueTextRenderUtil.GlyphStyle effectiveGlyphStyle(DialogueDefinition.Line line, int index, DialogueRichTextUtil.ResolvedStyle rich) {
        DialogueMarkdown.CharStyle markdown = currentMarkdown.styleAt(index);

        boolean bold = rich.bold != null ? rich.bold : markdown.bold();
        boolean italic = rich.italic != null ? rich.italic : markdown.italic();
        boolean underline = rich.underline != null ? rich.underline : markdown.underline();
        boolean strikethrough = rich.strikethrough != null ? rich.strikethrough : markdown.strikethrough();

        String fontId = rich.font != null ? rich.font : line.text_font != null ? line.text_font : definition.text_font;

        return new DialogueTextRenderUtil.GlyphStyle(fontId, bold, italic, underline, strikethrough);
    }


    private static boolean markdownEnabled(DialogueDefinition.Line line) {
        return line != null && line.markdown != null ? line.markdown : definition != null && definition.markdown;
    }


    private static float outlineThickness(DialogueDefinition.Line line, DialogueRichTextUtil.ResolvedStyle rich) {
        if (rich.outlineThickness != null) {
            return Math.max(0.0F, rich.outlineThickness);
        }

        if (line.text_outline_thickness != null) {
            return Math.max(0.0F, line.text_outline_thickness);
        }

        return Math.max(0.0F, definition.text_outline_thickness);
    }


    private static int outlineColor(DialogueDefinition.Line line, Glyph glyph, int maxWidth, float time, DialogueRichTextUtil.ResolvedStyle rich) {
        if (rich.outlineGradient != null && rich.outlineGradient.size() >= 2) {
            int span = Math.max(1, rich.outlineGradientEnd - rich.outlineGradientStart - 1);
            float t = Mth.clamp((glyph.index - rich.outlineGradientStart) / (float) span, 0.0F, 1.0F);
            return gradientColor(rich.outlineGradient, t);
        }

        List<String> gradient = line.text_outline_gradient != null ? line.text_outline_gradient : definition.text_outline_gradient;

        if (rich.outlineGradient == null && gradient != null && gradient.size() >= 2) {
            float t = Mth.clamp(glyph.x / (float) Math.max(1, maxWidth), 0.0F, 1.0F);
            return gradientColor(gradient, t);
        }

        String value = rich.outlineColor != null ? rich.outlineColor : line.text_outline_color != null ? line.text_outline_color : definition.text_outline_color;

        if (value == null || value.isBlank()) {
            value = "black";
        }

        if ("rainbow".equalsIgnoreCase(value)) {
            float hue = (glyph.index * 0.095F + time * 0.055F) % 1.0F;
            return Color.HSBtoRGB(hue, 0.76F, 1.0F) & 0xFFFFFF;
        }

        return parseColor(value);
    }


    private record Glyph(int index, char character, int x, int y, int width) {
    }
}
