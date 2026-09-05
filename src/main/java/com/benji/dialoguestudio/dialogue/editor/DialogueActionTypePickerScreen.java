package com.benji.dialoguestudio.dialogue.editor;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class DialogueActionTypePickerScreen extends DialogueRetroScreen {

    private record Entry(String id, String title, String help, int color) {
    }

    private static final List<Entry> ENTRIES = List.of(new Entry("give_item", "Give item", "Give an item stack to the player.", 0xFF668F48), new Entry("take_item", "Take item", "Remove items from the player's inventory.", 0xFF668F48),

            new Entry("quest_start", "Start quest", "Mark a Dialogue Engine quest as ACTIVE.", 0xFFB88A37), new Entry("quest_complete", "Complete quest", "Mark a quest as COMPLETED.", 0xFFB88A37), new Entry("quest_fail", "Fail quest", "Mark a quest as FAILED.", 0xFFB88A37), new Entry("quest_reset", "Reset quest", "Return a quest to NOT STARTED.", 0xFFB88A37),

            new Entry("add_player_tag", "Add tag", "Add a vanilla entity tag to player or dialogue source.", 0xFF6F8C56), new Entry("remove_player_tag", "Remove tag", "Remove a vanilla entity tag.", 0xFF6F8C56), new Entry("set_score", "Set score", "Set a scoreboard objective to an exact value.", 0xFF6F8C56), new Entry("add_score", "Add score", "Add or subtract from a scoreboard objective.", 0xFF6F8C56), new Entry("teleport", "Teleport", "Move player or dialogue source.", 0xFF6F8C56), new Entry("kill", "Kill entity/player", "Kill player or dialogue source.", 0xFFB14E55),

            new Entry("play_sound", "Play sound", "Play a sound for the dialogue player.", 0xFF566B3E), new Entry("particle", "Particle", "Spawn particles around player or dialogue source.", 0xFF566B3E),

            new Entry("fire_external", "Fire external event", "Notify Java/another mod with DialogueNodeExternalEvent.", 0xFF5D8D49), new Entry("run_command", "Run command", "Run a trusted server command from the dialogue graph.", 0xFF8C5F2F));

    private static final int CONTENT_TOP = 62;
    private static final int FOOTER_HEIGHT = 42;
    private static final int ROW_HEIGHT = 42;

    private final Screen parent;
    private final Consumer<String> callback;

    private final List<AbstractWidget> contentWidgets = new ArrayList<>();

    private int panelW;
    private int left;
    private int innerW;
    private int colW;

    private int contentBottom;
    private int contentHeight;
    private int scrollOffset;

    public DialogueActionTypePickerScreen(Screen parent, Consumer<String> callback) {
        super(Component.literal("Dialogue Studio - Action Type"));

        this.parent = parent;
        this.callback = callback;
    }

    @Override
    protected void init() {
        contentWidgets.clear();

        panelW = Math.min(760, width - 24);

        left = (width - panelW) / 2;

        innerW = panelW - 32;

        colW = (innerW - 8) / 2;

        contentBottom = height - FOOTER_HEIGHT - 4;

        int rows = (ENTRIES.size() + 1) / 2;

        contentHeight = rows * ROW_HEIGHT + 8;

        int maxScroll = maxScroll();

        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        for (int i = 0; i < ENTRIES.size(); i++) {

            Entry entry = ENTRIES.get(i);

            int col = i % 2;

            int row = i / 2;

            int x = left + 16 + col * (colW + 8);

            int y = CONTENT_TOP + 4 + row * ROW_HEIGHT - scrollOffset;

            addContentWidget(DialogueRetroButton.retroBuilder(Component.literal(entry.title), button -> {
                callback.accept(entry.id);

                minecraft.setScreen(parent);
            }).bounds(x, y, colW, 20).build());
        }

        updateContentVisibility();

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Cancel"), button -> minecraft.setScreen(parent)).bounds(left + 16, height - 30, innerW, 20).build());
    }

    private <T extends AbstractWidget> T addContentWidget(T widget) {
        contentWidgets.add(widget);
        return addRenderableWidget(widget);
    }

    private void updateContentVisibility() {
        for (AbstractWidget widget : contentWidgets) {

            widget.visible = widget.getY() >= CONTENT_TOP && widget.getY() + widget.getHeight() <= contentBottom;
        }
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - (contentBottom - CONTENT_TOP));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= left && mouseX <= left + panelW && mouseY >= CONTENT_TOP && mouseY <= contentBottom) {

            int max = maxScroll();

            int old = scrollOffset;

            if (delta > 0) {
                scrollOffset = Math.max(0, scrollOffset - ROW_HEIGHT);
            } else if (delta < 0) {
                scrollOffset = Math.min(max, scrollOffset + ROW_HEIGHT);
            }

            if (old != scrollOffset) {
                minecraft.setScreen(this);
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        graphics.fill(left, 12, left + panelW, height - 8, 0xF0121710);

        graphics.drawString(font, "CHOOSE WHAT THIS ACTION DOES", left + 16, 22, 0xFFB8FF72, false);

        graphics.drawString(font, "Items / quests / entity state / world effects / integration", left + 16, 36, DialogueRetroTheme.TEXT_HINT, false);

        if (maxScroll() > 0) {
            graphics.drawString(font, "mouse wheel: scroll", left + panelW - 118, 49, DialogueRetroTheme.TEXT_HINT, false);
        }

        graphics.enableScissor(left, CONTENT_TOP, left + panelW, contentBottom);

        for (int i = 0; i < ENTRIES.size(); i++) {

            Entry entry = ENTRIES.get(i);

            int col = i % 2;

            int row = i / 2;

            int x = left + 16 + col * (colW + 8);

            int y = CONTENT_TOP + 4 + row * ROW_HEIGHT - scrollOffset;

            if (y + 35 < CONTENT_TOP || y > contentBottom) {
                continue;
            }

            graphics.fill(x, y + 21, x + colW, y + 34, 0xA0151B11);

            graphics.fill(x, y + 21, x + 3, y + 34, entry.color);

            graphics.drawString(font, trim(entry.help, colW - 10), x + 6, y + 24, 0xFFBDB497, false);
        }

        int max = maxScroll();

        if (max > 0) {
            int trackX = left + panelW - 7;

            int trackTop = CONTENT_TOP + 2;

            int trackBottom = contentBottom - 2;

            int trackH = Math.max(1, trackBottom - trackTop);

            int viewportH = Math.max(1, contentBottom - CONTENT_TOP);

            int thumbH = Math.max(18, Math.round(trackH * (viewportH / (float) contentHeight)));

            int travel = Math.max(1, trackH - thumbH);

            int thumbY = trackTop + Math.round(travel * (scrollOffset / (float) max));

            graphics.fill(trackX, trackTop, trackX + 2, trackBottom, 0x555B664C);

            graphics.fill(trackX - 1, thumbY, trackX + 3, thumbY + thumbH, 0xFFB8FF72);
        }

        graphics.disableScissor();
        graphics.fill(left, contentBottom + 1, left + panelW, height - 8, 0xF0090C08);

        graphics.fill(left, contentBottom, left + panelW, contentBottom + 1, 0xFF445438);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private String trim(String value, int width) {
        if (font.width(value) <= width) {
            return value;
        }

        return font.plainSubstrByWidth(value, Math.max(0, width - font.width("..."))) + "...";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
