package dev.owyr.tierplates.client.screen;

import dev.owyr.tierplates.client.TierPlatesClient;
import dev.owyr.tierplates.client.config.TierPlatesConfig;
import dev.owyr.tierplates.client.data.GameMode;
import dev.owyr.tierplates.client.data.PlayerTierProfile;
import dev.owyr.tierplates.client.data.SubtierEntry;
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
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.AssetInfo.TextureAsset;
import net.minecraft.util.Identifier;

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
    private static final String DEFAULT_PREVIEW_PLAYER = "Swight";
    private static final UUID DEFAULT_PREVIEW_UUID = UUID.fromString("ebd7af32-759e-41e2-b227-9eeb8576d609");
    private static final Identifier DEFAULT_PREVIEW_SKIN = Identifier.of("tierplates", "textures/gui/swight_skin.png");
    private static final SkinTextures DEFAULT_PREVIEW_SKIN_TEXTURES = new SkinTextures(
            new LocalTextureAsset(Identifier.of("tierplates", "swight_preview_skin"), DEFAULT_PREVIEW_SKIN),
            null,
            null,
            PlayerSkinType.WIDE,
            true
    );
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
    private SettingsPage activePage = SettingsPage.GENERAL;

    public TierPlatesConfigScreen(Screen parent) {
        super(Text.literal("TierPlates"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        TierPlatesConfig config = TierPlatesClient.config();
        compactLayout = width < 760 || height < 430;
        panelWidth = compactLayout ? Math.max(320, width - 18) : Math.min(940, width - 44);
        panelHeight = compactLayout ? Math.max(230, height - 18) : Math.min(520, height - 38);
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;

        int controlsX = controlsX();
        int controlsW = controlsWidth();
        int tabY = panelTop + (compactLayout ? 48 : 54);
        int tabGap = compactLayout ? 5 : 8;
        int tabW = Math.max(64, (controlsW - tabGap * 2) / 3);
        int tabH = compactLayout ? 22 : 24;

        int tabX = controlsX;
        for (SettingsPage page : SettingsPage.values()) {
            addDrawableChild(new PageTabButton(tabX, tabY, tabW, tabH, page, activePage == page, () -> {
                activePage = page;
                clearAndInit();
            }));
            tabX += tabW + tabGap;
        }

        int controlY = tabY + tabH + (compactLayout ? 28 : 32);
        int actionY = panelTop + panelHeight - (compactLayout ? 32 : 38);
        int gap = compactLayout ? 8 : 12;
        int colW = Math.max(94, (controlsW - gap) / 2);
        int row = compactLayout ? 32 : 36;

        switch (activePage) {
            case GENERAL -> addGeneralOptions(config, controlsX, controlY, colW, gap, row);
            case LAYOUT -> addLayoutOptions(config, controlsX, controlY, colW, gap, row);
            case COLORS -> addColorEditors(controlsX, controlY, controlsW);
        }

        int actionW = Math.max(70, (controlsW - gap * 2) / 3);
        addOption(controlsX, actionY, actionW, "Reload", () -> "CACHE", () -> {
            TierDataCache.clear();
            clearAndInit();
        });
        addOption(controlsX + actionW + gap, actionY, actionW, "Reset", () -> "DEFAULTS", () -> {
            TierPlatesClient.resetConfig();
            TierDataCache.clear();
            activePage = SettingsPage.GENERAL;
            clearAndInit();
        });
        addOption(controlsX + (actionW + gap) * 2, actionY, actionW, "Done", () -> "CLOSE", this::close);
    }

    private void addGeneralOptions(TierPlatesConfig config, int x, int y, int colW, int gap, int row) {
        addOption(x, y, colW, "Enable", () -> enabled(config), () -> {
            config.enabled = !config.enabled;
            saveAndRefresh();
        });
        addOption(x + colW + gap, y, colW, "Icons", () -> onOff(config.showIcons), () -> {
            config.showIcons = !config.showIcons;
            saveAndRefresh();
        });

        y += row;
        addOption(x, y, colW, "Nametag", () -> onOff(config.showNametags), () -> {
            config.showNametags = !config.showNametags;
            saveAndRefresh();
        });
        addOption(x + colW + gap, y, colW, "Tab", () -> onOff(config.showTab), () -> {
            config.showTab = !config.showTab;
            saveAndRefresh();
        });

        y += row;
        addOption(x, y, colW, "Chat", () -> onOff(config.showChat), () -> {
            config.showChat = !config.showChat;
            saveAndRefresh();
        });
        addOption(x + colW + gap, y, colW, "Background", () -> onOff(config.drawBackground), () -> {
            config.drawBackground = !config.drawBackground;
            saveAndRefresh();
        });

        y += row;
        addOption(x, y, colW, "Demo", () -> onOff(config.useDemoData), () -> {
            config.useDemoData = !config.useDemoData;
            saveAndRefresh();
        });
        addOption(x + colW + gap, y, colW, "Preview", () -> config.previewOwnPlayer ? "ME" : previewDisplayName(config), () -> {
            config.previewOwnPlayer = !config.previewOwnPlayer;
            saveAndRefresh();
        });
    }

    private void addLayoutOptions(TierPlatesConfig config, int x, int y, int colW, int gap, int row) {
        addWideOption(x, y, colW * 2 + gap, "Displayed Tier", () -> config.displayMode.name(), () -> {
            config.displayMode = config.displayMode.next();
            saveAndRefresh();
        });

        y += row;
        addOption(x, y, colW, "MCTiers", () -> config.mctiersSide.name(), () -> {
            config.mctiersSide = config.mctiersSide.next();
            fixDuplicateSides(config);
            saveAndRefresh();
        });
        addOption(x + colW + gap, y, colW, "PvPTiers", () -> config.pvptiersSide.name(), () -> {
            config.pvptiersSide = config.pvptiersSide.next();
            fixDuplicateSides(config);
            saveAndRefresh();
        });

        y += row;
        addOption(x, y, colW, "Icon Size", () -> config.iconSize.name(), () -> {
            config.iconSize = config.iconSize.next();
            saveAndRefresh();
        });
        addOption(x + colW + gap, y, colW, "Tag Mode", () -> nameplateModeLabel(config), () -> {
            config.nameplateMode = config.nameplateMode.next();
            config.forceNameplates = config.nameplateMode == TierPlatesConfig.NameplateMode.FORCE_ALL;
            saveAndRefresh();
        });

        y += row;
        addWideOption(x, y, colW * 2 + gap, "Name In Tag", () -> onOff(config.showNameInNametag), () -> {
            config.showNameInNametag = !config.showNameInNametag;
            saveAndRefresh();
        });

        y += row;
        addWideOption(x, y, colW * 2 + gap, "Extra Server Tags", () -> config.hideDuplicateServerTags ? "HIDE" : "SHOW", () -> {
            config.hideDuplicateServerTags = !config.hideDuplicateServerTags;
            saveAndRefresh();
        });
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
        addDrawableChild(new ModernOptionButton(x, y, width, compactLayout ? 26 : 30, label, value, action));
    }

    private void addWideOption(int x, int y, int width, String label, ValueSupplier value, Runnable action) {
        addDrawableChild(new ModernOptionButton(x, y, width, compactLayout ? 26 : 30, label, value, action));
    }

    private void addColorEditors(int x, int y, int width) {
        int columns = compactLayout ? 5 : 5;
        int gap = compactLayout ? 8 : 12;
        int cellW = (width - gap * (columns - 1)) / columns;
        int swatchW = Math.min(compactLayout ? 42 : 58, cellW);
        int swatchH = compactLayout ? 38 : 44;
        for (int i = 0; i < TIER_ORDER.length; i++) {
            int col = i % columns;
            int row = i / columns;
            int swatchX = x + col * (cellW + gap) + (cellW - swatchW) / 2;
            int swatchY = y + row * (swatchH + (compactLayout ? 10 : 14));
            addDrawableChild(new ColorSwatchWidget(swatchX, swatchY, swatchW, swatchH, TIER_ORDER[i]));
        }
    }

    private int previewWidth() {
        return compactLayout ? 166 : 330;
    }

    private int controlsX() {
        return panelLeft + previewWidth() + (compactLayout ? 28 : 42);
    }

    private int controlsWidth() {
        return Math.max(210, panelLeft + panelWidth - controlsX() - (compactLayout ? 14 : 24));
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

        int titleX = compactLayout ? panelLeft + 14 : panelLeft + 22;
        context.drawTextWithShadow(textRenderer, Text.literal("TierPlates").setStyle(Style.EMPTY.withColor(TEXT)), titleX, panelTop + 16, TEXT);
        context.drawTextWithShadow(textRenderer, Text.literal("1.0.0  made by OwYr7").setStyle(Style.EMPTY.withColor(WARM)), titleX, panelTop + 29, WARM);

        int rightX = controlsX();
        context.drawTextWithShadow(textRenderer, Text.literal("Settings").setStyle(Style.EMPTY.withColor(TEXT)), rightX, panelTop + 16, TEXT);
        context.drawTextWithShadow(textRenderer, Text.literal("/tiersbyowyr7"), rightX, panelTop + 29, MUTED);
    }

    private void renderSettingsLabels(DrawContext context) {
        int x = controlsX();
        int y = panelTop + (compactLayout ? 92 : 106);
        String subtitle = switch (activePage) {
            case GENERAL -> "Visibility and preview";
            case LAYOUT -> "Tier placement and nametag mode";
            case COLORS -> "Click a tier to cycle its color";
        };
        context.drawTextWithShadow(textRenderer, Text.literal(activePage.title()).setStyle(Style.EMPTY.withColor(TEXT)), x, y - 12, TEXT);
        context.drawTextWithShadow(textRenderer, Text.literal(subtitle).setStyle(Style.EMPTY.withColor(MUTED)), x, y, MUTED);
    }

    private void renderPreview(DrawContext context) {
        TierPlatesConfig config = TierPlatesClient.config();
        int x = compactLayout ? panelLeft + 14 : panelLeft + 20;
        int y = compactLayout ? panelTop + 54 : panelTop + 62;
        int w = previewWidth();
        int h = panelTop + panelHeight - y - (compactLayout ? 28 : 32);
        drawPanel(context, x, y, w, h, SURFACE_2, BORDER);

        String previewName = previewName(config);
        Optional<PlayerTierProfile> previewProfile = previewProfile(config, previewName);
        context.drawTextWithShadow(textRenderer, Text.literal("Preview"), x + 10, y + 10, TEXT);
        context.drawTextWithShadow(textRenderer, Text.literal(previewName), x + 10, y + 22, MUTED);

        int centerX = x + w / 2;
        int skinSize = compactLayout ? 54 : 78;
        int skinY = compactLayout ? y + 70 : y + 94;
        int nameplateY = compactLayout ? y + 42 : skinY - 36;
        if (compactLayout) {
            drawCompactNameplatePreview(context, x, w, nameplateY, previewName, previewProfile, config);
        } else {
            drawNameplatePreview(context, centerX, nameplateY, previewName, previewProfile, config);
        }
        drawPlayerPreview(context, centerX - skinSize / 4, skinY, skinSize, previewName, config.previewOwnPlayer);
        drawStatus(context, x + 12, y + h - (compactLayout ? 42 : 54), previewProfile);
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
        Optional<SubtierEntry> sub = previewProfile.flatMap(PlayerTierProfile::subtierEntry);

        if (previewProfile.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Loading tiers..."), centerX, y - 14, MUTED);
        }

        sub.ifPresent(value -> {
            Text subText = subtierPreviewBadge(value, config.showIcons);
            context.drawTextWithShadow(textRenderer, Text.literal("|"), leftPipeX, y - 12, PIPE);
            context.drawTextWithShadow(textRenderer, Text.literal("|"), rightPipeX, y - 12, PIPE);
            context.drawTextWithShadow(textRenderer, subText, centerX - textRenderer.getWidth(subText) / 2, y - 12, TEXT);
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

    private void drawCompactNameplatePreview(DrawContext context, int x, int width, int y, String previewName,
                                             Optional<PlayerTierProfile> previewProfile, TierPlatesConfig config) {
        int centerX = x + width / 2;
        Optional<TierEntry> leftEntry = previewProfile.flatMap(profile -> entryFor(profile, sourceFor(config, TierPlatesConfig.Side.LEFT), config.displayMode));
        Optional<TierEntry> rightEntry = previewProfile.flatMap(profile -> entryFor(profile, sourceFor(config, TierPlatesConfig.Side.RIGHT), config.displayMode));
        Optional<SubtierEntry> sub = previewProfile.flatMap(PlayerTierProfile::subtierEntry);

        if (previewProfile.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Loading tiers..."), centerX, y - 10, MUTED);
        }
        sub.ifPresent(value -> context.drawCenteredTextWithShadow(textRenderer, subtierPreviewBadge(value, config.showIcons), centerX, y - 10, TEXT));

        Text name = Text.literal(previewName);
        context.drawCenteredTextWithShadow(textRenderer, name, centerX, y + 2, TEXT);
        leftEntry.ifPresent(entry -> context.drawTextWithShadow(textRenderer, TierTextFormatter.badge(entry, config.showIcons), x + 14, y + 15, TEXT));
        rightEntry.ifPresent(entry -> {
            Text badge = TierTextFormatter.badge(entry, config.showIcons);
            context.drawTextWithShadow(textRenderer, badge, x + width - 14 - textRenderer.getWidth(badge), y + 15, TEXT);
        });
    }

    private Text subtierPreviewBadge(SubtierEntry entry, boolean showIcon) {
        MutableText text = Text.literal(entry.tier() + " ").setStyle(Style.EMPTY.withColor(TierTextFormatter.tierColor(entry.tier())));
        if (showIcon) {
            text.append(Text.literal(entry.mode().icon)
                    .setStyle(Style.EMPTY.withFont(new StyleSpriteSource.Font(TierTextFormatter.iconFont(TierPlatesClient.config()))).withColor(0xFFFFFF)));
        }
        return text;
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
            return TierDataCache.getBestEffort(minecraft.player.getUuid(), minecraft.player.getNameForScoreboard(), config.useDemoData);
        }
        if (DEFAULT_PREVIEW_PLAYER.equalsIgnoreCase(previewName)) {
            return TierDataCache.get(DEFAULT_PREVIEW_UUID, DEFAULT_PREVIEW_PLAYER, config.useDemoData);
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
        if (DEFAULT_PREVIEW_PLAYER.equalsIgnoreCase(previewName)) {
            return Optional.of(DEFAULT_PREVIEW_SKIN_TEXTURES);
        }
        Optional<UUID> resolvedUuid = DEFAULT_PREVIEW_PLAYER.equalsIgnoreCase(previewName)
                ? Optional.of(DEFAULT_PREVIEW_UUID)
                : TierDataCache.uuidForName(previewName);
        if (resolvedUuid.isEmpty()) {
            return Optional.empty();
        }
        UUID skinUuid = resolvedUuid.get();
        String key = previewName.toLowerCase() + "-" + skinUuid;
        Supplier<SkinTextures> supplier = SKIN_SUPPLIERS.computeIfAbsent(key, ignored ->
                minecraft.getSkinProvider().supplySkinTextures(new GameProfile(skinUuid, previewName), false));
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

    private enum SettingsPage {
        GENERAL("General"),
        LAYOUT("Layout"),
        COLORS("Colors");

        private final String title;

        SettingsPage(String title) {
            this.title = title;
        }

        private String title() {
            return title;
        }
    }

    @FunctionalInterface
    private interface ValueSupplier {
        String get();
    }

    private static final class PageTabButton extends ClickableWidget {
        private final SettingsPage page;
        private final boolean active;
        private final Runnable action;

        private PageTabButton(int x, int y, int width, int height, SettingsPage page, boolean active, Runnable action) {
            super(x, y, width, height, Text.literal(page.title()));
            this.page = page;
            this.active = active;
            this.action = action;
            setTooltip(Tooltip.of(Text.literal(page.title())));
        }

        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
            int fill = active ? SURFACE_3 : (isHovered() ? SURFACE_2 : SURFACE);
            int border = active ? BORDER_HOT : (isHovered() ? BORDER_HOT : BORDER);
            context.fill(getX(), getY(), getRight(), getBottom(), fill);
            context.fill(getX(), getY(), getRight(), getY() + 1, border);
            context.fill(getX(), getBottom() - 1, getRight(), getBottom(), active ? ACCENT : border);
            context.fill(getX(), getY(), getX() + 1, getBottom(), border);
            context.fill(getRight() - 1, getY(), getRight(), getBottom(), border);
            int color = active ? TEXT : MUTED;
            int textX = getX() + (getWidth() - renderer.getWidth(page.title())) / 2;
            context.drawTextWithShadow(renderer, Text.literal(page.title()), textX, getY() + (getHeight() - 8) / 2, color);
        }

        @Override
        public void onClick(Click click, boolean doubleClick) {
            if (!active) {
                action.run();
            }
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            appendDefaultNarrations(builder);
        }
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
                String valueText = value.get();
                int valueColor = switch (valueText) {
                    case "ON", "ME", "ME F5", "CACHE", "CLOSE" -> ACCENT;
                    case "DEFAULTS", "OFF" -> WARM;
                    default -> TEXT;
                };
                int valueX = getRight() - renderer.getWidth(valueText) - 6;
                int labelX = getX() + 6;
                int labelEnd = labelX + renderer.getWidth(label);
                if (valueX <= labelEnd + 5) {
                    context.drawCenteredTextWithShadow(renderer, Text.literal(valueText), getX() + getWidth() / 2, getY() + 9, valueColor);
                    return;
                }
                context.drawTextWithShadow(renderer, Text.literal(label), labelX, getY() + 9, MUTED);
                context.drawTextWithShadow(renderer, Text.literal(valueText), Math.max(getX() + 6, valueX), getY() + 9, valueColor);
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
            int swatchHeight = Math.min(18, getHeight());
            context.fill(getX(), getY(), getRight(), getY() + swatchHeight, 0xFF000000 | color);
            context.fill(getX(), getY(), getRight(), getY() + 1, border);
            context.fill(getX(), getY() + swatchHeight - 1, getRight(), getY() + swatchHeight, border);
            context.fill(getX(), getY(), getX() + 1, getY() + swatchHeight, border);
            context.fill(getRight() - 1, getY(), getRight(), getY() + swatchHeight, border);
            if (getHeight() <= 16) {
                return;
            }
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

    private record LocalTextureAsset(Identifier id, Identifier texturePath) implements TextureAsset {
    }
}
