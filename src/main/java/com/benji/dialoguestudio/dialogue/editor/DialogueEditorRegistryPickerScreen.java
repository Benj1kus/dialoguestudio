package com.benji.dialoguestudio.dialogue.editor;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

public final class DialogueEditorRegistryPickerScreen extends DialogueRetroScreen {

    public enum Kind {ENTITY, BLOCK, ITEM}

    private static final String ALL_NAMESPACES = "*";

    private final Screen parent;
    private final Kind kind;
    private final Consumer<String> callback;

    private String search;
    private int page;
    private String selected;
    private String namespaceFilter;

    private LivingEntity previewEntity;

    private static final int ROW_HEIGHT = 24;
    private static final int LIST_TOP = 70;
    private static final int FOOTER_HEIGHT = 38;

    public DialogueEditorRegistryPickerScreen(Screen parent, Kind kind, String current, Consumer<String> callback) {
        this(parent, kind, current, callback, "", 0, current, ALL_NAMESPACES);
    }

    private DialogueEditorRegistryPickerScreen(Screen parent, Kind kind, String current, Consumer<String> callback, String search, int page, String selected, String namespaceFilter) {
        super(Component.literal("Dialogue Studio - Registry Browser"));

        this.parent = parent;
        this.kind = kind;
        this.callback = callback;
        this.search = search != null ? search : "";
        this.page = page;
        this.selected = selected;
        this.namespaceFilter = namespaceFilter != null && !namespaceFilter.isBlank() ? namespaceFilter : ALL_NAMESPACES;
    }

    @Override
    protected void init() {
        int listWidth = Math.min(420, width / 2);

        EditBox searchBox = new DialogueRetroEditBox(font, 12, 10, listWidth - 94, 20, Component.literal("Search registry"));

        searchBox.setValue(search);
        searchBox.setResponder(value -> search = value != null ? value : "");
        addRenderableWidget(searchBox);

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Search"), button -> rebuild(0)).bounds(listWidth - 76, 10, 64, 20).build());
        int filterY = 36;

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("<"), button -> cycleNamespace(-1)).bounds(12, filterY, 28, 20).build());

        String filterLabel = ALL_NAMESPACES.equals(namespaceFilter) ? "Mod: ALL" : "Mod: " + modDisplayName(namespaceFilter) + " [" + namespaceFilter + "]";

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal(trimToWidth(filterLabel, listWidth - 150)), button -> {
            namespaceFilter = ALL_NAMESPACES;
            rebuild(0);
        }).bounds(44, filterY, listWidth - 128, 20).build());

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal(">"), button -> cycleNamespace(1)).bounds(listWidth - 80, filterY, 28, 20).build());

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("ALL"), button -> {
            namespaceFilter = ALL_NAMESPACES;
            rebuild(0);
        }).bounds(listWidth - 48, filterY, 36, 20).build());
        List<ResourceLocation> ids = ids();

        int rows = rowsPerPage();
        int pages = Math.max(1, (ids.size() + rows - 1) / rows);

        page = Math.max(0, Math.min(page, pages - 1));

        int start = page * rows;
        int end = Math.min(ids.size(), start + rows);

        for (int i = start; i < end; i++) {
            ResourceLocation id = ids.get(i);

            int y = LIST_TOP + (i - start) * ROW_HEIGHT;

            boolean current = id.toString().equals(selected);

            String display = (current ? "> " : "") + id;

            addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal(display), button -> {
                selected = id.toString();
                previewEntity = null;
            }).bounds(12, y, listWidth - 24, 20).build());
        }

        int footerY = height - 29;

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("<"), button -> rebuild(Math.max(0, page - 1))).bounds(12, footerY, 30, 20).build());

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal((page + 1) + "/" + pages + "  (" + ids.size() + ")"), button -> {
        }).bounds(46, footerY, 112, 20).build());

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal(">"), button -> rebuild(Math.min(pages - 1, page + 1))).bounds(162, footerY, 30, 20).build());

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Use selected"), button -> {
            minecraft.setScreen(parent);

            if (selected != null && !selected.isBlank()) {
                callback.accept(selected);
            }
        }).bounds(width - 230, footerY, 104, 20).build());

        addRenderableWidget(DialogueRetroButton.retroBuilder(Component.literal("Cancel"), button -> minecraft.setScreen(parent)).bounds(width - 118, footerY, 104, 20).build());
    }

    private int rowsPerPage() {
        int listBottom = height - FOOTER_HEIGHT - 4;

        return Math.max(1, (listBottom - LIST_TOP) / ROW_HEIGHT);
    }

    private List<ResourceLocation> allRegistryIds() {
        Set<ResourceLocation> result = new LinkedHashSet<>();

        if (kind == Kind.ENTITY) {
            result.addAll(ForgeRegistries.ENTITY_TYPES.getKeys());

            result.addAll(BuiltInRegistries.ENTITY_TYPE.keySet());
        } else if (kind == Kind.ITEM) {
            result.addAll(ForgeRegistries.ITEMS.getKeys());

            result.addAll(BuiltInRegistries.ITEM.keySet());
        } else {
            result.addAll(ForgeRegistries.BLOCKS.getKeys());

            result.addAll(BuiltInRegistries.BLOCK.keySet());

            for (ResourceLocation itemId : ForgeRegistries.ITEMS.getKeys()) {

                Item item = ForgeRegistries.ITEMS.getValue(itemId);

                if (!(item instanceof BlockItem blockItem)) {
                    continue;
                }

                ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(blockItem.getBlock());

                if (blockId != null) {
                    result.add(blockId);
                }
            }
        }

        List<ResourceLocation> ids = new ArrayList<>(result);

        ids.sort(Comparator.naturalOrder());

        return ids;
    }

    private List<String> namespaces() {
        Set<String> values = new LinkedHashSet<>();

        for (ResourceLocation id : allRegistryIds()) {
            values.add(id.getNamespace());
        }

        List<String> result = new ArrayList<>(values);

        result.sort(Comparator.comparing(namespace -> {
            if ("minecraft".equals(namespace)) {
                return "0_" + namespace;
            }

            if ("dlgstd".equals(namespace)) {
                return "1_" + namespace;
            }

            return "2_" + namespace;
        }));

        result.add(0, ALL_NAMESPACES);

        return result;
    }

    private void cycleNamespace(int direction) {
        List<String> namespaces = namespaces();

        int index = namespaces.indexOf(namespaceFilter);

        if (index < 0) {
            index = 0;
        }

        index = Math.floorMod(index + direction, namespaces.size());

        namespaceFilter = namespaces.get(index);

        rebuild(0);
    }

    private List<ResourceLocation> ids() {
        String query = search != null ? search.trim().toLowerCase(Locale.ROOT) : "";

        List<ResourceLocation> result = new ArrayList<>();

        for (ResourceLocation id : allRegistryIds()) {
            if (!ALL_NAMESPACES.equals(namespaceFilter) && !namespaceFilter.equals(id.getNamespace())) {
                continue;
            }

            if (!query.isBlank()) {
                String registryId = id.toString().toLowerCase(Locale.ROOT);

                String namespace = id.getNamespace().toLowerCase(Locale.ROOT);

                String displayName = displayName(id).toLowerCase(Locale.ROOT);

                String modName = modDisplayName(id.getNamespace()).toLowerCase(Locale.ROOT);

                boolean matches = registryId.contains(query) || namespace.contains(query) || displayName.contains(query) || modName.contains(query);

                if (!matches) {
                    continue;
                }
            }

            result.add(id);
        }

        result.sort(Comparator.naturalOrder());

        return result;
    }

    private String displayName(ResourceLocation id) {
        try {
            if (kind == Kind.ENTITY) {
                EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);

                if (type == null) {
                    type = BuiltInRegistries.ENTITY_TYPE.get(id);
                }

                if (type != null) {
                    String value = type.getDescription().getString();

                    if (value != null && !value.isBlank()) {
                        return value;
                    }
                }
            } else if (kind == Kind.ITEM) {
                Item item = ForgeRegistries.ITEMS.getValue(id);

                if (item == null) {
                    item = BuiltInRegistries.ITEM.get(id);
                }

                if (item != null) {
                    String value = item.getDescription().getString();

                    if (value != null && !value.isBlank()) {
                        return value;
                    }
                }
            } else {
                Block block = ForgeRegistries.BLOCKS.getValue(id);

                if (block == null) {
                    block = BuiltInRegistries.BLOCK.get(id);
                }

                if (block != null) {
                    String value = block.getName().getString();

                    if (value != null && !value.isBlank()) {
                        return value;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return id.getPath();
    }


    private String modDisplayName(String namespace) {
        if (namespace == null || namespace.isBlank() || ALL_NAMESPACES.equals(namespace)) {
            return "ALL";
        }

        try {
            return ModList.get().getModContainerById(namespace).map(container -> container.getModInfo().getDisplayName()).orElse(namespace);
        } catch (Exception ignored) {
            return namespace;
        }
    }

    private void rebuild(int newPage) {
        minecraft.setScreen(new DialogueEditorRegistryPickerScreen(parent, kind, selected, callback, search, newPage, selected, namespaceFilter));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        List<ResourceLocation> ids = ids();

        int rows = rowsPerPage();

        int pages = Math.max(1, (ids.size() + rows - 1) / rows);

        int old = page;

        if (delta > 0) {
            page = Math.max(0, page - 1);
        } else if (delta < 0) {
            page = Math.min(pages - 1, page + 1);
        }

        if (page != old) {
            rebuild(page);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        int listWidth = Math.min(420, width / 2);

        int previewX = listWidth + 16;

        int previewW = width - previewX - 16;

        int footerTop = height - FOOTER_HEIGHT;

        graphics.fill(6, 62, listWidth + 2, footerTop - 2, 0x6811170E);
        graphics.fill(0, footerTop, width, height, 0xD40C100A);
        graphics.fill(0, footerTop, width, footerTop + 1, 0xFF445438);
        graphics.fill(previewX, 42, width - 16, footerTop - 4, 0xA010150D);

        String selectedName = selected != null ? selected : "Nothing selected";

        graphics.drawString(font, kind == Kind.ENTITY ? "Entity preview" : kind == Kind.ITEM ? "Item preview" : "Block preview", previewX + 8, 50, 0xFFFFFFFF, false);

        graphics.drawString(font, trimToWidth(selectedName, Math.max(40, previewW - 16)), previewX + 8, 66, 0xFFAAAAAA, false);

        if (selected != null) {
            ResourceLocation id = ResourceLocation.tryParse(selected);

            if (id != null) {
                String name = displayName(id);

                String mod = modDisplayName(id.getNamespace());

                graphics.drawString(font, trimToWidth("Name: " + name, Math.max(40, previewW - 16)), previewX + 8, 80, 0xFFECE4CB, false);

                graphics.drawString(font, trimToWidth("Mod: " + mod + " [" + id.getNamespace() + "]", Math.max(40, previewW - 16)), previewX + 8, 94, 0xFFB8FF72, false);

                if (kind == Kind.ENTITY) {
                    renderEntityPreview(graphics, id, previewX, previewW, mouseX, mouseY);
                } else if (kind == Kind.ITEM) {
                    renderItemPreview(graphics, id, previewX, previewW);
                } else {
                    renderBlockPreview(graphics, id, previewX, previewW);
                }
            }
        }
        graphics.drawString(font, "Loaded namespaces: " + Math.max(0, namespaces().size() - 1), 12, 60, DialogueRetroTheme.TEXT_HINT, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderEntityPreview(GuiGraphics graphics, ResourceLocation id, int previewX, int previewW, int mouseX, int mouseY) {
        if (minecraft.level == null) {
            graphics.drawString(font, "Join a world to preview entities.", previewX + 8, 116, 0xFFFFAA55, false);

            return;
        }

        if (previewEntity == null) {
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);

            if (type == null) {
                type = BuiltInRegistries.ENTITY_TYPE.get(id);
            }

            if (type != null) {
                Entity entity = type.create(minecraft.level);

                if (entity instanceof LivingEntity living) {
                    previewEntity = living;
                }
            }
        }

        if (previewEntity == null) {
            graphics.drawString(font, "This entity cannot use the LivingEntity GUI preview.", previewX + 8, 116, 0xFFFFAA55, false);

            return;
        }

        int x1 = previewX + 12;

        int x2 = previewX + previewW - 12;

        int y2 = height - FOOTER_HEIGHT - 10;

        int size = Math.max(24, Math.min(80, Math.min(previewW, height - 170) / 2));

        int renderX = (x1 + x2) / 2;

        int renderY = y2 - 8;

        float lookX = renderX - mouseX;

        float lookY = (renderY - size * 1.6F) - mouseY;

        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, renderX, renderY, size, lookX, lookY, previewEntity);
    }

    private void renderItemPreview(GuiGraphics graphics, ResourceLocation id, int previewX, int previewW) {
        Item item = ForgeRegistries.ITEMS.getValue(id);

        if (item == null) {
            item = BuiltInRegistries.ITEM.get(id);
        }

        if (item == null || item == Items.AIR) {
            graphics.drawString(font, "This item has no preview.", previewX + 8, 116, 0xFFFFAA55, false);
            return;
        }

        ItemStack stack = new ItemStack(item);

        var pose = graphics.pose();

        pose.pushPose();

        pose.translate(previewX + previewW * 0.5F, (108 + (height - FOOTER_HEIGHT - 4)) * 0.5F, 100.0F);

        pose.scale(5.0F, 5.0F, 5.0F);

        graphics.renderItem(stack, -8, -8);

        pose.popPose();
    }


    private void renderBlockPreview(GuiGraphics graphics, ResourceLocation id, int previewX, int previewW) {
        Block block = ForgeRegistries.BLOCKS.getValue(id);

        if (block == null) {
            block = BuiltInRegistries.BLOCK.get(id);
        }

        if (block == null || block.asItem() == Items.AIR) {

            graphics.drawString(font, "This block has no item model preview.", previewX + 8, 116, 0xFFFFAA55, false);

            return;
        }

        ItemStack stack = new ItemStack(block);

        var pose = graphics.pose();

        pose.pushPose();

        pose.translate(previewX + previewW * 0.5F, (108 + (height - FOOTER_HEIGHT - 4)) * 0.5F, 100.0F);

        pose.scale(4.0F, 4.0F, 4.0F);

        graphics.renderItem(stack, -8, -8);

        pose.popPose();
    }

    private String trimToWidth(String value, int maxWidth) {
        if (value == null) {
            return "";
        }

        if (font.width(value) <= maxWidth) {
            return value;
        }

        String suffix = "...";

        int suffixWidth = font.width(suffix);

        int allowed = Math.max(0, maxWidth - suffixWidth);

        return font.plainSubstrByWidth(value, allowed) + suffix;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
