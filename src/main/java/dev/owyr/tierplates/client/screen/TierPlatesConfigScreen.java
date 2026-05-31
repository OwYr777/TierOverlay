package dev.owyr.tierplates.client.screen;

import dev.owyr.tierplates.client.TierPlatesClient;
import dev.owyr.tierplates.client.config.TierPlatesConfig;
import dev.owyr.tierplates.client.data.GameMode;
import dev.owyr.tierplates.client.data.PlayerTierProfile;
import dev.owyr.tierplates.client.data.TierDataCache;
import dev.owyr.tierplates.client.data.TierEntry;
import dev.owyr.tierplates.client.data.TierSource;
import dev.owyr.tierplates.client.render.TierTextFormatter;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class TierPlatesConfigScreen extends Screen {
    private static final int OVERLAY = 0xCC090B0F;
    private static final int SURFACE = 0xEE11151B;
    private static final int SURFACE_2 = 0xEE171D25;
    private static final int SURFACE_3 = 0xEE202833;
    private static final int BORDER = 0xFF34404D;
    private static final int BORDER_HOT = 0xFF56C7E8;
    private static final int TEXT = 0xFFEAF2F7;
    private static final int MUTED = 0xFF91A2AE;
    private static final int ACCENT = 0xFF56C7E8;
    private static final int WARM = 0xFFECA461;
    private static final int GREEN = 0xFF8DFFB0;
    private static final int PIPE = 0xFFDADADA;
    private static final String DEFAULT_PREVIEW_PLAYER = "ItzRealMe";
    private static final String[] TIER_ORDER = {"HT1", "LT1", "HT2", "LT2", "HT3", "LT3", "HT4", "LT4", "HT5", "LT5"};
    private static final int[] EDIT_COLORS = {
            0xE1BB4A, 0xD0B45E, 0xC7D3E6, 0xA1A7B1, 0xECA461,
            0xBC7F48, 0x7F7598, 0x635C78, 0x8D83A6, 0x635C78
    };
    private static final Map<String, Supplier<SkinTextures>> SKIN_SUPPLIERS = new ConcurrentHashMap<>();

    private final Screen parent;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private boolean compactLayout;

    public TierPlatesConfigScreen(Screen parent) {
        super(Text.literal("TierPlates"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        TierPlatesConfig config = TierPlatesClient.config();
        compactLayout = width < 720 || height < 430;
        panelWidth = compactLayout ? Math.max(320, width - 20) : Math.min(900, width - 44);
        panelHeight = compactLayout ? Math.max(230, height - 20) : Math.min(500, height - 38);
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;

        if (compactLayout) {
            initCompact(config);
            return;
        }

        int rightX = panelLeft + 364;
        int y = panelTop + 72;
        int rightW = panelLeft + panelWidth - rightX - 28;
        int gap = 12;
        int colW = (rightW - gap) / 2;
        int row = 34;

        addOption(rightX, y, colW, "Enabled", () -> enabled(config), () -> {
            config.enabled = !config.enabled;
            saveAndRefresh();
        });
        addOption(rightX + colW + gap, y, colW, "Icons", () -> onOff(config.showIcons), () -> {
            config.showIcons = !config.showIcons;
            saveAndRefresh();
        });

        y += row;
        addOption(rightX, y, colW, "Nametag", () -> onOff(config.showNametags), () -> {
            config.showNametags = !config.showNametags;
            saveAndRefresh();
        });
        addOption(rightX + colW + gap, y, colW, "Tab", () -> onOff(config.showTab), () -> {
            config.showTab = !config.showTab;
            saveAndRefresh();
        });

        y += row;
        addOption(rightX, y, colW, "Chat", () -> onOff(config.showChat), () -> {
            config.showChat = !config.showChat;
            saveAndRefresh();
        });
        addOption(rightX + colW + gap, y, colW, "Background", () -> onOff(config.drawBackground), () -> {
            config.drawBackground = !config.drawBackground;
            saveAndRefresh();
        });

        y += row + 12;
        addWideOption(rightX, y, colW * 2 + gap, "Displayed Tier", () -> config.displayMode.name(), () -> {
            config.displayMode = config.displayMode.next();
            saveAndRefresh();
        });

        y += row;
        addOption(rightX, y, colW, "MCTiers", () -> config.mctiersSide.name(), () -> {
            config.mctiersSide = config.mctiersSide.next();
            fixDuplicateSides(config);
            saveAndRefresh();
        });
        addOption(rightX + colW + gap, y, colW, "PvPTiers", () -> config.pvptiersSide.name(), () -> {
            config.pvptiersSide = config.pvptiersSide.next();
            fixDuplicateSides(config);
            saveAndRefresh();
        });

        y += row + 12;
        addOption(rightX, y, colW, "Icon Size", () -> config.iconSize.name(), () -> {
            config.iconSize = config.iconSize.next();
            saveAndRefresh();
        });
        addOption(rightX + colW + gap, y, colW, "Tag Mode", () -> nameplateModeLabel(config), () -> {
            config.nameplateMode = config.nameplateMode.next();
            config.forceNameplates = config.nameplateMode == TierPlatesConfig.NameplateMode.FORCE_ALL;
            saveAndRefresh();
        });

        y += row;
        addWideOption(rightX, y, colW * 2 + gap, "Name In Tag", () -> onOff(config.showNameInNametag), () -> {
            config.showNameInNametag = !config.showNameInNametag;
            saveAndRefresh();
        });

        y += row + 12;
        addOption(rightX, y, colW, "Demo Preview", () -> onOff(config.useDemoData), () -> {
            config.useDemoData = !config.useDemoData;
            saveAndRefresh();
        });
        addOption(rightX + colW + gap, y, colW, "Preview Player", () -> config.previewOwnPlayer ? "ME" : previewDisplayName(config), () -> {
            config.previewOwnPlayer = !config.previewOwnPlayer;
            saveAndRefresh();
        });

        addColorEditors();

        int actionY = panelTop + panelHeight - 42;
        addWideOption(rightX, actionY - 36, colW * 2 + gap, "Reset", () -> "DEFAULTS", () -> {
            TierPlatesClient.resetConfig();
            TierDataCache.clear();
            clearAndInit();
        });
        addOption(rightX, actionY, colW, "Reload", () -> "CACHE", () -> {
            TierDataCache.clear();
            clearAndInit();
        });
        addOption(rightX + colW + gap, actionY, colW, "Done", () -> "CLOSE", this::close);
    }

    private void initCompact(TierPlatesConfig config) {
        int leftW = 150;
        int rightX = panelLeft + leftW + 24;
        int rightW = Math.max(210, panelLeft + panelWidth - rightX - 12);
        int gap = 6;
        int colW = Math.max(56, (rightW - gap * 2) / 3);
        int row = 25;
        int y = panelTop + 62;

        addOption(rightX, y, colW, "Enable", () -> enabled(config), () -> {
            config.enabled = !config.enabled;
            saveAndRefresh();
        });
        addOption(rightX + colW + gap, y, colW, "Icons", () -> onOff(config.showIcons), () -> {
            config.showIcons = !config.showIcons;
            saveAndRefresh();
        });
        addOption(rightX + (colW + gap) * 2, y, colW, "Name", () -> onOff(config.showNametags), () -> {
            config.showNametags = !config.showNametags;
            saveAndRefresh();
        });

        y += row;
        addOption(rightX, y, colW, "Tab", () -> onOff(config.showTab), () -> {
            config.showTab = !config.showTab;
            saveAndRefresh();
        });
        addOption(rightX + colW + gap, y, colW, "Chat", () -> onOff(config.showChat), () -> {
            config.showChat = !config.showChat;
            saveAndRefresh();
        });
        addOption(rightX + (colW + gap) * 2, y, colW, "BG", () -> onOff(config.drawBackground), () -> {
            config.drawBackground = !config.drawBackground;
            saveAndRefresh();
        });

        y += row;
        addOption(rightX, y, colW, "Tier", () -> config.displayMode.name(), () -> {
            config.displayMode = config.displayMode.next();
            saveAndRefresh();
        });
        addOption(rightX + colW + gap, y, colW, "MC", () -> config.mctiersSide.name(), () -> {
            config.mctiersSide = config.mctiersSide.next();
            fixDuplicateSides(config);
            saveAndRefresh();
        });
        addOption(rightX + (colW + gap) * 2, y, colW, "PvP", () -> config.pvptiersSide.name(), () -> {
            config.pvptiersSide = config.pvptiersSide.next();
            fixDuplicateSides(config);
            saveAndRefresh();
        });

        y += row;
        addOption(rightX, y, colW, "Icon", () -> config.iconSize.name(), () -> {
            config.iconSize = config.iconSize.next();
            saveAndRefresh();
        });
        addOption(rightX + colW + gap, y, colW, "Tag", () -> nameplateModeLabel(config), () -> {
            config.nameplateMode = config.nameplateMode.next();
            config.forceNameplates = config.nameplateMode == TierPlatesConfig.NameplateMode.FORCE_ALL;
            saveAndRefresh();
        });
        addOption(rightX + (colW + gap) * 2, y, colW, "In Tag", () -> onOff(config.showNameInNametag), () -> {
            config.showNameInNametag = !config.showNameInNametag;
            saveAndRefresh();
        });

        y += row;
        addOption(rightX, y, colW, "Demo", () -> onOff(config.useDemoData), () -> {
            config.useDemoData = !config.useDemoData;
            saveAndRefresh();
        });
        addOption(rightX + colW + gap, y, colW, "Preview", () -> config.previewOwnPlayer ? "ME" : previewDisplayName(config), () -> {
            config.previewOwnPlayer = !config.previewOwnPlayer;
            saveAndRefresh();
        });
        addOption(rightX + (colW + gap) * 2, y, colW, "Reload", () -> "CACHE", () -> {
            TierDataCache.clear();
            clearAndInit();
        });

        y += row;
        addOption(rightX, y, colW * 2 + gap, "Reset", () -> "DEFAULTS", () -> {
            TierPlatesClient.resetConfig();
            TierDataCache.clear();
            clearAndInit();
        });
        addOption(rightX + (colW + gap) * 2, y, colW, "Done", () -> "CLOSE", this::close);

        addColorEditors();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackdrop(context);
        renderChrome(context);
        renderPreview(context);
        renderSettingsLabels(context);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    private void addOption(int x, int y, int width, String label, ValueSupplier value, Runnable action) {
        addDrawableChild(new ModernOptionButton(x, y, width, compactLayout ? 22 : 30, label, value, action));
    }

    private void addWideOption(int x, int y, int width, String label, ValueSupplier value, Runnable action) {
        addDrawableChild(new ModernOptionButton(x, y, width, compactLayout ? 22 : 30, label, value, action));
    }

    private void addColorEditors() {
        int startX = compactLayout ? panelLeft + 20 : panelLeft + 38;
        int startY = compactLayout ? panelTop + 150 : panelTop + panelHeight - 116;
        int size = 22;
        int gap = 7;
        for (int i = 0; i < TIER_ORDER.length; i++) {
            int x = compactLayout ? startX + (i % 5) * (size + gap) : startX + i * (size + gap);
            int y = compactLayout ? startY + (i / 5) * 40 : startY;
            addDrawableChild(new ColorSwatchWidget(x, y, size, 32, TIER_ORDER[i]));
        }
    }

    private void saveAndRefresh() {
        TierPlatesClient.saveConfig();
        clearAndInit();
    }

    private static String enabled(TierPlatesConfig config) {
        return config.enabled ? "ON" : "OFF";
    }

    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }

    private static String nameplateModeLabel(TierPlatesConfig config) {
        TierPlatesConfig.NameplateMode mode = config.nameplateMode == null
                ? TierPlatesConfig.NameplateMode.OWN_F5
                : config.nameplateMode;
        return switch (mode) {
            case VANILLA_ONLY -> "VANILLA";
            case OWN_F5 -> "ME F5";
            case FORCE_ALL -> "FORCE";
        };
    }

    private static void fixDuplicateSides(TierPlatesConfig config) {
        if (config.mctiersSide != TierPlatesConfig.Side.OFF && config.mctiersSide == config.pvptiersSide) {
            config.pvptiersSide = config.mctiersSide == TierPlatesConfig.Side.LEFT ? TierPlatesConfig.Side.RIGHT : TierPlatesConfig.Side.LEFT;
        }
    }

    private void renderBackdrop(DrawContext context) {
        context.fill(0, 0, width, height, OVERLAY);
        context.fillGradient(0, 0, width, height, 0x221F3A4A, 0x22000000);
    }

    private void renderChrome(DrawContext context) {
        drawPanel(context, panelLeft, panelTop, panelWidth, panelHeight, SURFACE, BORDER);
        context.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + 2, ACCENT);

        int titleX = compactLayout ? panelLeft + 12 : panelLeft + 22;
        context.drawTextWithShadow(textRenderer, Text.literal("TierPlates").setStyle(Style.EMPTY.withColor(TEXT)), titleX, panelTop + 16, TEXT);
        context.drawTextWithShadow(textRenderer, Text.literal("1.0.0  made by OwYr7").setStyle(Style.EMPTY.withColor(WARM)), titleX, panelTop + 29, WARM);

        int rightX = compactLayout ? panelLeft + 174 : panelLeft + 364;
        context.drawTextWithShadow(textRenderer, Text.literal("Settings"), rightX, panelTop + 16, TEXT);
        context.drawTextWithShadow(textRenderer, Text.literal("/tiersbyowyr7"), rightX, panelTop + 29, MUTED);
    }

    private void renderSettingsLabels(DrawContext context) {
        if (compactLayout) {
            return;
        }
        int x = panelLeft + 364;
        drawSectionLabel(context, x, panelTop + 60, "General");
        drawSectionLabel(context, x, panelTop + 188, "Display");
        drawSectionLabel(context, x, panelTop + 304, "Preview");
    }

    private void renderPreview(DrawContext context) {
        TierPlatesConfig config = TierPlatesClient.config();
        int x = compactLayout ? panelLeft + 12 : panelLeft + 20;
        int y = compactLayout ? panelTop + 54 : panelTop + 58;
        int w = compactLayout ? 150 : 320;
        int h = panelHeight - 86;
        drawPanel(context, x, y, w, h, SURFACE_2, BORDER);

        String previewName = previewName(config);
        Optional<PlayerTierProfile> previewProfile = previewProfile(config, previewName);
        context.drawTextWithShadow(textRenderer, Text.literal("Preview"), x + 10, y + 10, TEXT);
        context.drawTextWithShadow(textRenderer, Text.literal(previewName), x + 10, y + 22, MUTED);

        int centerX = x + w / 2;
        int skinSize = compactLayout ? 52 : 74;
        int skinY = compactLayout ? y + 42 : y + 86;
        drawNameplatePreview(context, centerX, skinY - 34, previewName, previewProfile, config);
        drawPlayerPreview(context, centerX - skinSize / 4, skinY, skinSize, previewName, config.previewOwnPlayer);
        if (!compactLayout) {
            drawIconRow(context, x + 16, y + h - 152);
            drawPaletteTitle(context, x + 16, y + h - 132);
            drawStatus(context, x + 16, y + h - 70, previewProfile);
        }
    }

    private void drawNameplatePreview(DrawContext context, int centerX, int y, String previewName,
                                      Optional<PlayerTierProfile> previewProfile, TierPlatesConfig config) {
        Text name = Text.literal(previewName);
        int nameWidth = textRenderer.getWidth(name);
        int nameX = centerX - nameWidth / 2;
        int leftPipeX = nameX - 9;
        int rightPipeX = nameX + nameWidth + 9;

        Optional<TierEntry> leftEntry = previewProfile.flatMap(profile -> entryFor(profile, sourceFor(config, TierPlatesConfig.Side.LEFT), config.displayMode));
        Optional<TierEntry> rightEntry = previewProfile.flatMap(profile -> entryFor(profile, sourceFor(config, TierPlatesConfig.Side.RIGHT), config.displayMode));
        Optional<String> sub = previewProfile.flatMap(PlayerTierProfile::subtier);

        if (previewProfile.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Loading tiers..."), centerX, y - 14, MUTED);
        }

        sub.ifPresent(value -> {
            Text subText = Text.literal(value).setStyle(Style.EMPTY.withColor(WARM));
            context.drawTextWithShadow(textRenderer, Text.literal("|"), leftPipeX, y - 12, PIPE);
            context.drawTextWithShadow(textRenderer, Text.literal("|"), rightPipeX, y - 12, PIPE);
            context.drawTextWithShadow(textRenderer, subText, centerX - textRenderer.getWidth(subText) / 2, y - 12, WARM);
        });

        leftEntry.ifPresent(entry -> {
            Text left = TierTextFormatter.badge(entry, config.showIcons);
            context.drawTextWithShadow(textRenderer, left, leftPipeX - textRenderer.getWidth(left) - 7, y, TEXT);
        });
        context.drawTextWithShadow(textRenderer, Text.literal("|"), leftPipeX, y, PIPE);
        context.drawTextWithShadow(textRenderer, name, nameX, y, TEXT);
        context.drawTextWithShadow(textRenderer, Text.literal("|"), rightPipeX, y, PIPE);
        rightEntry.ifPresent(entry -> context.drawTextWithShadow(textRenderer, TierTextFormatter.badge(entry, config.showIcons), rightPipeX + 11, y, TEXT));
    }

    private void drawPlayerPreview(DrawContext context, int x, int y, int height, String name, boolean ownPlayer) {
        int unit = Math.max(2, height / 32);
        int bodyWidth = 16 * unit;
        context.fill(x - 8, y - 8, x + bodyWidth + 8, y + height + 8, 0x6610151B);
        context.fill(x - 8, y + height + 7, x + bodyWidth + 8, y + height + 8, 0x6634404D);

        Optional<SkinTextures> skin = skinTextures(name, ownPlayer);
        if (skin.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Loading skin"), x + bodyWidth / 2, y + height / 2 - 4, MUTED);
            return;
        }
        drawFullBodySkin(context, skin.get().body().texturePath(), x, y, unit);
    }

    private void drawFullBodySkin(DrawContext context, Identifier texture, int x, int y, int unit) {
        int head = 8 * unit;
        int limb = 4 * unit;
        int bodyW = 8 * unit;
        int bodyH = 12 * unit;
        int headX = x + limb;
        int bodyX = x + limb;
        int armY = y + head;
        int bodyY = y + head;
        int legY = y + head + bodyH;

        drawSkinPart(context, texture, headX, y, 8, 8, 8, 8, head, head);
        drawSkinPart(context, texture, headX, y, 40, 8, 8, 8, head, head);

        drawSkinPart(context, texture, bodyX, bodyY, 20, 20, 8, 12, bodyW, bodyH);
        drawSkinPart(context, texture, bodyX, bodyY, 20, 36, 8, 12, bodyW, bodyH);

        drawSkinPart(context, texture, x, armY, 44, 20, 4, 12, limb, bodyH);
        drawSkinPart(context, texture, x, armY, 44, 36, 4, 12, limb, bodyH);
        drawSkinPart(context, texture, x + limb + bodyW, armY, 36, 52, 4, 12, limb, bodyH);
        drawSkinPart(context, texture, x + limb + bodyW, armY, 52, 52, 4, 12, limb, bodyH);

        drawSkinPart(context, texture, x + limb, legY, 4, 20, 4, 12, limb, bodyH);
        drawSkinPart(context, texture, x + limb, legY, 4, 36, 4, 12, limb, bodyH);
        drawSkinPart(context, texture, x + limb + limb, legY, 20, 52, 4, 12, limb, bodyH);
        drawSkinPart(context, texture, x + limb + limb, legY, 4, 52, 4, 12, limb, bodyH);
    }

    private void drawSkinPart(DrawContext context, Identifier texture, int x, int y, int u, int v, int regionW, int regionH, int width, int height) {
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, regionW, regionH, 64, 64);
    }

    private void drawIconRow(DrawContext context, int x, int y) {
        TierPlatesConfig config = TierPlatesClient.config();
        for (GameMode mode : GameMode.values()) {
            MutableText icon = Text.literal(mode.icon).setStyle(Style.EMPTY.withFont(new StyleSpriteSource.Font(TierTextFormatter.iconFont(config))).withColor(0xFFFFFF));
            context.drawTextWithShadow(textRenderer, icon, x, y, 0xFFFFFF);
            x += 16;
        }
    }

    private void drawPaletteTitle(DrawContext context, int x, int y) {
        context.drawTextWithShadow(textRenderer, Text.literal("Tier Colors"), x, y, MUTED);
    }

    private void drawStatus(DrawContext context, int x, int y, Optional<PlayerTierProfile> profile) {
        String mctiers = profile.map(value -> value.hasEntries(TierSource.MCTIERS) ? "loaded" : "none").orElse("loading");
        String pvptiers = profile.map(value -> value.hasEntries(TierSource.PVPTIERS) ? "loaded" : "none").orElse("loading");
        String subtiers = profile.map(value -> value.subtier().isPresent() ? "loaded" : "none").orElse("loading");
        context.drawTextWithShadow(textRenderer, Text.literal("MCTiers  " + mctiers), x, y, statusColor(mctiers));
        context.drawTextWithShadow(textRenderer, Text.literal("PvPTiers " + pvptiers), x, y + 11, statusColor(pvptiers));
        context.drawTextWithShadow(textRenderer, Text.literal("Subtiers " + subtiers), x, y + 22, statusColor(subtiers));
    }

    private static int statusColor(String status) {
        return switch (status) {
            case "loaded" -> GREEN;
            case "none" -> MUTED;
            default -> WARM;
        };
    }

    private void drawPanel(DrawContext context, int x, int y, int w, int h, int fill, int border) {
        context.fill(x, y, x + w, y + h, fill);
        context.fill(x, y, x + w, y + 1, border);
        context.fill(x, y + h - 1, x + w, y + h, border);
        context.fill(x, y, x + 1, y + h, border);
        context.fill(x + w - 1, y, x + w, y + h, border);
    }

    private void drawSectionLabel(DrawContext context, int x, int y, String label) {
        context.drawTextWithShadow(textRenderer, Text.literal(label).setStyle(Style.EMPTY.withColor(MUTED)), x, y - 12, MUTED);
    }

    private String previewName(TierPlatesConfig config) {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (config.previewOwnPlayer && minecraft.player != null) {
            return minecraft.player.getNameForScoreboard();
        }
        return previewDisplayName(config);
    }

    private Optional<PlayerTierProfile> previewProfile(TierPlatesConfig config, String previewName) {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (config.previewOwnPlayer && minecraft.player != null) {
            return TierDataCache.get(minecraft.player.getUuid(), previewName, config.useDemoData);
        }
        return TierDataCache.getByName(previewName, config.useDemoData);
    }

    private static String previewDisplayName(TierPlatesConfig config) {
        if (config.previewPlayerName != null && !config.previewPlayerName.isBlank()) {
            return config.previewPlayerName;
        }
        return DEFAULT_PREVIEW_PLAYER;
    }

    private Optional<SkinTextures> skinTextures(String name, boolean ownPlayer) {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (ownPlayer && minecraft.player != null) {
            if (minecraft.getNetworkHandler() != null) {
                for (PlayerListEntry entry : minecraft.getNetworkHandler().getPlayerList()) {
                    if (entry.getProfile().id().equals(minecraft.player.getUuid())) {
                        return Optional.of(entry.getSkinTextures());
                    }
                }
            }
            return Optional.of(minecraft.getSkinProvider().supplySkinTextures(minecraft.player.getGameProfile(), true).get());
        }

        String previewName = name == null || name.isBlank() ? DEFAULT_PREVIEW_PLAYER : name;
        UUID fallbackUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + previewName).getBytes(StandardCharsets.UTF_8));
        Optional<UUID> resolvedUuid = TierDataCache.uuidForName(previewName);
        if (resolvedUuid.isEmpty()) {
            return Optional.empty();
        }
        UUID skinUuid = resolvedUuid.orElse(fallbackUuid);
        String key = previewName.toLowerCase() + "-" + skinUuid;
        Supplier<SkinTextures> supplier = SKIN_SUPPLIERS.computeIfAbsent(key, ignored ->
                minecraft.getSkinProvider().supplySkinTextures(new GameProfile(skinUuid, previewName), true));
        return Optional.of(supplier.get());
    }

    private static Optional<TierSource> sourceFor(TierPlatesConfig config, TierPlatesConfig.Side side) {
        if (config.mctiersSide == side) {
            return Optional.of(TierSource.MCTIERS);
        }
        if (config.pvptiersSide == side) {
            return Optional.of(TierSource.PVPTIERS);
        }
        return Optional.empty();
    }

    private static Optional<TierEntry> entryFor(PlayerTierProfile profile, Optional<TierSource> source, TierPlatesConfig.DisplayMode mode) {
        if (source.isEmpty()) {
            return Optional.empty();
        }
        if (mode == TierPlatesConfig.DisplayMode.BEST) {
            return profile.best(source.get());
        }
        return profile.get(source.get(), GameMode.valueOf(mode.name()));
    }

    private static int tierColor(String tier) {
        TierPlatesConfig.TierColors colors = TierPlatesClient.config().tierColors;
        return colors == null ? 0xFFFFFF : colors.get(tier);
    }

    private static int nextPaletteColor(int currentColor) {
        int normalized = currentColor & 0xFFFFFF;
        for (int i = 0; i < EDIT_COLORS.length; i++) {
            if (EDIT_COLORS[i] == normalized) {
                return EDIT_COLORS[(i + 1) % EDIT_COLORS.length];
            }
        }
        return EDIT_COLORS[0];
    }

    @FunctionalInterface
    private interface ValueSupplier {
        String get();
    }

    private static final class ModernOptionButton extends ClickableWidget {
        private final String label;
        private final ValueSupplier value;
        private final Runnable action;

        private ModernOptionButton(int x, int y, int width, int height, String label, ValueSupplier value, Runnable action) {
            super(x, y, width, height, Text.literal(label));
            this.label = label;
            this.value = value;
            this.action = action;
            setTooltip(Tooltip.of(Text.literal(label)));
        }

        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
            int fill = isHovered() ? SURFACE_3 : SURFACE_2;
            int border = isHovered() ? BORDER_HOT : BORDER;
            context.fill(getX(), getY(), getRight(), getBottom(), fill);
            context.fill(getX(), getY(), getRight(), getY() + 1, border);
            context.fill(getX(), getBottom() - 1, getRight(), getBottom(), border);
            context.fill(getX(), getY(), getX() + 1, getBottom(), border);
            context.fill(getRight() - 1, getY(), getRight(), getBottom(), border);

            if (getHeight() < 28) {
                context.drawTextWithShadow(renderer, Text.literal(label), getX() + 6, getY() + 7, MUTED);
                String valueText = value.get();
                int valueColor = switch (valueText) {
                    case "ON", "ME", "ME F5", "CACHE", "CLOSE" -> ACCENT;
                    case "DEFAULTS", "OFF" -> WARM;
                    default -> TEXT;
                };
                int valueX = getRight() - renderer.getWidth(valueText) - 6;
                context.drawTextWithShadow(renderer, Text.literal(valueText), Math.max(getX() + 6, valueX), getY() + 7, valueColor);
                return;
            }

            context.drawTextWithShadow(renderer, Text.literal(label), getX() + 8, getY() + 5, MUTED);
            String valueText = value.get();
            int valueColor = switch (valueText) {
                case "ON", "ME", "ME F5", "VANILLA", "CACHE", "CLOSE" -> ACCENT;
                case "DEFAULTS" -> WARM;
                case "OFF" -> WARM;
                default -> TEXT;
            };
            int valueX = getRight() - renderer.getWidth(valueText) - 8;
            if (valueX < getX() + 8) {
                valueX = getX() + 8;
            }
            context.drawTextWithShadow(renderer, Text.literal(valueText), valueX, getY() + 17, valueColor);
        }

        @Override
        public void onClick(Click click, boolean doubleClick) {
            action.run();
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            appendDefaultNarrations(builder);
        }
    }

    private static final class ColorSwatchWidget extends ClickableWidget {
        private final String tier;

        private ColorSwatchWidget(int x, int y, int width, int height, String tier) {
            super(x, y, width, height, Text.literal(tier));
            this.tier = tier;
            setTooltip(Tooltip.of(Text.literal(tier + " color")));
        }

        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
            int color = tierColor(tier);
            int border = isHovered() ? BORDER_HOT : BORDER;
            context.fill(getX(), getY(), getRight(), getY() + 18, 0xFF000000 | color);
            context.fill(getX(), getY(), getRight(), getY() + 1, border);
            context.fill(getX(), getY() + 17, getRight(), getY() + 18, border);
            context.fill(getX(), getY(), getX() + 1, getY() + 18, border);
            context.fill(getRight() - 1, getY(), getRight(), getY() + 18, border);
            int labelX = getX() + (getWidth() - renderer.getWidth(tier)) / 2;
            context.drawTextWithShadow(renderer, Text.literal(tier), labelX, getY() + 22, TEXT);
        }

        @Override
        public void onClick(Click click, boolean doubleClick) {
            TierPlatesConfig config = TierPlatesClient.config();
            if (config.tierColors == null) {
                config.tierColors = new TierPlatesConfig.TierColors();
            }
            config.tierColors.set(tier, nextPaletteColor(config.tierColors.get(tier)));
            TierPlatesClient.saveConfig();
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            appendDefaultNarrations(builder);
        }
    }
}
