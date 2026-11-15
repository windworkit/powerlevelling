package com.powerlevelling.client.screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.IntConsumer;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import com.powerlevelling.character.Appearance;
import com.powerlevelling.character.CharacterChoice;
import com.powerlevelling.character.CharacterData;
import com.powerlevelling.character.CharacterRegistries;
import com.powerlevelling.character.CharacterRegistries.Entry;
import com.powerlevelling.character.CharacterService;
import com.powerlevelling.character.CharacterSnapshot;
import com.powerlevelling.character.CosmeticPart;
import com.powerlevelling.character.KiNature;
import com.powerlevelling.character.Modifiers;
import com.powerlevelling.character.Race;
import com.powerlevelling.character.TailState;
import com.powerlevelling.client.CharacterPrompt;
import com.powerlevelling.client.ClientCharacters;
import com.powerlevelling.client.ClientScreens;
import com.powerlevelling.client.ModKeys;
import com.powerlevelling.config.ServerConfig;
import com.powerlevelling.network.CharacterSubmitPayload;
import com.powerlevelling.network.CharacterSubmitPayload.Mode;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The character screen (Addendum B.6, milestone C8): live 3D preview on the left, tabs RACE / NATURE / LOOK / KI &amp; AURA on
 * the right, Back / Next / Create footer, confirm modal and a custom color picker. One screen for all modes: CREATE, and the
 * item-driven edits STYLE, NATURE, FULL (see {@link #tabEditable}).
 * <p>
 * Drawing and input go through {@link UiCanvas} (immediate mode, virtual panel pixels, uniformly scaled down on small
 * windows). While open, {@link ClientCharacters#previewOverride} holds a snapshot of the unconfirmed choices, so the real
 * hair / tail / eye / body-size renderers draw the preview; it is cleared in {@link #removed()}.
 * renderBackground stays a no-op on purpose (vanilla would blur the panel).
 */
public class CharacterScreen extends Screen {
    private static final String P = "gui.powerlevelling.character.";

    // geometry (virtual px)
    private static final int H = 220;
    private static final int PREVIEW_W_DEFAULT = 128;
    private static final int PREVIEW_W_MIN = 72;
    private static final int RIGHT_W = 248;
    private static final int PREVIEW_Y = 29;
    private static final int PREVIEW_H = 142;
    private static final int TAB_Y = 29;
    private static final int TAB_H = 15;
    private static final int BODY_Y = 48;
    private static final int BODY_H = 146;
    private static final int FOOT_Y = 199;
    private static final int FOOT_H = 15;
    private static final int COL_W = 116;
    private static final int COL2_X = 122;
    private static final int RESULT_TIMEOUT_TICKS = 100;

    private static final int[] HAIR_PRESETS = {
            0x1A1A1A, 0x3B2A1E, 0x5A3A1E, 0x8B5A2B, 0xC8A165, 0xE8D44D, 0xF5EBC0, 0xE8E8E8,
            0x8A8A92, 0xC0392B, 0xFF7A2F, 0xE75480, 0xB06CE0, 0x5B3FC0, 0x3B6FB6, 0x2FA84F};
    private static final int[] EYE_PRESETS = {
            0x2B2B2B, 0x5A3A1E, 0x8B5A2B, 0xC8A165, 0x2E6FD8, 0x3FA9F5, 0x20B2AA, 0x2FA84F,
            0x9ACD32, 0xE8B923, 0xFF7A2F, 0xD33F3F, 0xE75480, 0x7B4BC9, 0xCFCFD8, 0xFFFFFF};
    /** Ki and aura share one palette; every entry has HSV value >= 0.35. */
    private static final int[] KI_PRESETS = {
            0x4FC3FF, 0xFFE24A, 0xFF7A2F, 0xFF4F4F, 0x6CFF7A, 0xC77DFF, 0xFF8AD8, 0xFFFFFF,
            0x2F7CFF, 0x00E5C3, 0xB4FF3C, 0xFF3CAC, 0xFFB347, 0x9A7BFF, 0xBFEFFF, 0xFF2D2D};

    private enum Tab {
        RACE, NATURE, LOOK, KI;

        String key() {
            return P + "tab." + name().toLowerCase(Locale.ROOT);
        }
    }

    private record RaceOpt(ResourceLocation id, Race race, boolean playable) {
    }

    private final Mode mode;
    private final UiCanvas canvas = new UiCanvas();
    private ColorPicker picker;

    // layout (computed each frame)
    private int previewW = PREVIEW_W_DEFAULT;
    private int panelW = 400;
    private int rightX = 16 + PREVIEW_W_DEFAULT;
    private float scale = 1f;
    private float offX;
    private float offY;

    // options
    private boolean loaded;
    private List<RaceOpt> raceOpts = List.of();
    private List<Entry<KiNature>> natures = List.of();
    private Modifiers balanced;
    private final Map<ResourceLocation, Boolean> iconExists = new HashMap<>();
    private List<ResourceLocation> hairStyles = new ArrayList<>();
    private int[] hairPalette = HAIR_PRESETS;
    private boolean hairAny = true;
    private int[] eyePalette = EYE_PRESETS;
    private boolean eyeAny = true;

    // choices
    private ResourceLocation raceId = CharacterData.DEFAULT_RACE;
    private ResourceLocation natureId = CharacterData.DEFAULT_NATURE;
    private ResourceLocation hairStyle = Appearance.NO_HAIR;
    private int hairColor = HAIR_PRESETS[0];
    private int eyeColor = EYE_PRESETS[0];
    private int kiColor = KI_PRESETS[0];
    private int auraColor = -1; // -1 = match ki
    private int skinColor = -1;
    private int eyeStyle;
    private float bodyHeight = 1f; // not "height": Screen.height is the window height
    private float bodyBuild = 1f;
    private boolean tailOn;
    private boolean tailWrapped;
    private boolean lostTail;
    private List<ResourceLocation> parts = List.of();

    // ui state
    private Tab tab = Tab.RACE;
    private final int[] scroll = new int[Tab.values().length];
    private final int[] contentH = new int[Tab.values().length];
    private int hairListScroll;
    private boolean compareBalanced;
    private boolean confirmOpen;
    private float yaw = -20f;
    private float pitch;
    private float zoom = 1f;
    private boolean autoSpin;
    private boolean auraOn;
    private boolean previewDragging;
    private double lastDragX;
    private double lastDragY;
    private long lastFrameNanos = System.nanoTime();

    // submit state
    private boolean waiting;
    private int awaitSerial;
    private int waitTicks;
    private Component error;

    public CharacterScreen() {
        this(Mode.CREATE);
    }

    public CharacterScreen(Mode mode) {
        super(Component.translatable(mode == Mode.CREATE ? P + "title" : P + "title." + mode.name().toLowerCase(Locale.ROOT)));
        this.mode = mode;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static net.minecraft.network.chat.MutableComponent tr(String suffix, Object... args) {
        return Component.translatable(P + suffix, args);
    }

    // ---- mode rules -----------------------------------------------------------------------------------------------

    private boolean tabEditable(Tab t) {
        return switch (t) {
            case RACE -> mode == Mode.CREATE || mode == Mode.FULL;
            case NATURE -> mode != Mode.STYLE;
            case LOOK, KI -> mode != Mode.NATURE;
        };
    }

    private Tab nextEditable(int dir) {
        for (int i = tab.ordinal() + dir; i >= 0 && i < Tab.values().length; i += dir) {
            if (tabEditable(Tab.values()[i])) return Tab.values()[i];
        }
        return null;
    }

    // ---- options --------------------------------------------------------------------------------------------------

    private RegistryAccess access() {
        return this.minecraft != null && this.minecraft.level != null ? this.minecraft.level.registryAccess() : null;
    }

    private Race race() {
        RegistryAccess a = access();
        return a == null ? null : CharacterRegistries.getRace(a, raceId);
    }

    private KiNature nature() {
        RegistryAccess a = access();
        return a == null ? null : CharacterRegistries.getNature(a, natureId);
    }

    private RaceOpt raceOpt(ResourceLocation id) {
        for (RaceOpt o : raceOpts) {
            if (o.id().equals(id)) return o;
        }
        return null;
    }

    private static boolean playable(RegistryAccess access, Entry<Race> e) {
        try {
            return CharacterService.isPlayable(access, e.id());
        } catch (IllegalStateException notLoaded) { // server config not readable (yet): trust the race file
            return e.value().playable();
        }
    }

    private static boolean hideLocked() {
        try {
            return ServerConfig.HIDE_LOCKED_RACES.get();
        } catch (IllegalStateException notLoaded) {
            return false;
        }
    }

    private void loadOptions() {
        RegistryAccess access = access();
        if (access == null) return;
        List<RaceOpt> opts = new ArrayList<>();
        boolean hide = hideLocked();
        CharacterSnapshot self = ClientCharacters.selfSnapshot();
        for (Entry<Race> e : CharacterRegistries.allRaces(access)) {
            if (e.value().hidden()) continue;
            boolean ok = playable(access, e);
            boolean isCurrent = self != null && e.id().equals(self.race()) && mode != Mode.CREATE;
            if (!ok && hide && !isCurrent) continue;
            opts.add(new RaceOpt(e.id(), e.value(), ok));
        }
        raceOpts = opts;
        List<Entry<KiNature>> ns = new ArrayList<>();
        for (Entry<KiNature> e : CharacterRegistries.allNatures(access)) {
            if (e.value().enabled() || (mode != Mode.CREATE && self != null && e.id().equals(self.nature()))) ns.add(e);
            if (e.id().equals(CharacterData.DEFAULT_NATURE)) balanced = e.value().modifiers();
        }
        natures = ns;

        if (mode != Mode.CREATE && self != null) {
            loadFromSnapshot(self);
        } else {
            raceId = CharacterData.DEFAULT_RACE;
            RaceOpt def = raceOpt(raceId);
            if (def == null || !def.playable()) {
                for (RaceOpt o : raceOpts) {
                    if (o.playable()) {
                        raceId = o.id();
                        break;
                    }
                }
            }
            boolean hasDefault = natures.stream().anyMatch(n -> n.id().equals(CharacterData.DEFAULT_NATURE));
            natureId = hasDefault || natures.isEmpty() ? CharacterData.DEFAULT_NATURE : natures.get(0).id();
            selectRace(raceId, true);
        }
        Tab first = null;
        for (Tab t : Tab.values()) {
            if (first == null && tabEditable(t)) first = t;
        }
        tab = first == null ? Tab.RACE : first;
    }

    private void loadFromSnapshot(CharacterSnapshot s) {
        raceId = s.race();
        natureId = s.nature();
        rebuildRaceLists();
        hairStyle = s.hairStyle();
        if (!hairStyles.contains(hairStyle)) hairStyle = Appearance.NO_HAIR;
        hairColor = s.hairColor() & 0xFFFFFF;
        eyeColor = s.eyeColor() & 0xFFFFFF;
        kiColor = s.kiColor() & 0xFFFFFF;
        auraColor = s.auraColor() < 0 ? -1 : s.auraColor() & 0xFFFFFF;
        skinColor = s.skinColor();
        eyeStyle = s.eyeStyle();
        bodyHeight = s.height();
        bodyBuild = s.build();
        tailOn = s.tail() == TailState.PRESENT;
        tailWrapped = s.tailWrapped();
        parts = s.parts();
        lostTail = s.tail() == TailState.LOST && (mode == Mode.STYLE || mode == Mode.NATURE);
        ensureHairVisible();
    }

    /** Hair style list and restricted color palettes of the selected race. "None (skin hair)" is always first. */
    private void rebuildRaceLists() {
        Race r = race();
        RegistryAccess access = access();
        hairStyles = new ArrayList<>();
        hairStyles.add(Appearance.NO_HAIR);
        hairAny = true;
        hairPalette = HAIR_PRESETS;
        eyeAny = true;
        eyePalette = EYE_PRESETS;
        if (r == null) return;
        Race.Hair hair = r.appearance().hair();
        if (hair.allowed() && access != null) {
            for (Entry<CosmeticPart> e : CharacterRegistries.allParts(access)) {
                if ("hair".equals(e.value().slot()) && hair.styles().allows(e.id())) hairStyles.add(e.id());
            }
            if (!hair.colors().any() && !hair.colors().values().isEmpty()) {
                hairAny = false;
                hairPalette = hair.colors().values().stream().mapToInt(Integer::intValue).toArray();
            }
        }
        var eyes = r.appearance().eyes().colors();
        if (!eyes.any() && !eyes.values().isEmpty()) {
            eyeAny = false;
            eyePalette = eyes.values().stream().mapToInt(Integer::intValue).toArray();
        }
    }

    /** A race was picked (or the first one at open): reset what the race decides, keep what still fits. */
    private void selectRace(ResourceLocation id, boolean initial) {
        raceId = id;
        skinColor = -1;
        parts = List.of();
        lostTail = false;
        rebuildRaceLists();
        Race r = race();
        if (r == null) return;
        Race.RaceAppearance ra = r.appearance();
        Race.Hair hair = ra.hair();
        hairStyle = hair.defaultStyle().filter(hairStyles::contains)
                .orElse(hairStyles.size() > 1 ? hairStyles.get(1) : Appearance.NO_HAIR);
        if (initial) {
            hairColor = !hairAny ? hairPalette[0] : (hair.suggestedColors().isEmpty() ? HAIR_PRESETS[0] : hair.suggestedColors().get(0));
        } else if (!hairAny && !contains(hairPalette, hairColor)) {
            hairColor = hairPalette[0];
        }
        if (!eyeAny && !contains(eyePalette, eyeColor)) eyeColor = eyePalette[0];
        bodyHeight = Mth.clamp(bodyHeight, ra.height().min(), ra.height().max());
        bodyBuild = Mth.clamp(bodyBuild, ra.build().min(), ra.build().max());
        tailOn = ra.tail().initialState() == TailState.PRESENT;
        tailWrapped = false;
        hairListScroll = 0;
        ensureHairVisible();
    }

    private static boolean contains(int[] arr, int v) {
        for (int a : arr) {
            if (a == (v & 0xFFFFFF)) return true;
        }
        return false;
    }

    private void ensureHairVisible() {
        int i = Math.max(0, hairStyles.indexOf(hairStyle));
        if (i < hairListScroll) hairListScroll = i;
        if (i >= hairListScroll + 4) hairListScroll = i - 3;
    }

    private void setHair(ResourceLocation id) {
        hairStyle = id;
        error = null;
        ensureHairVisible();
    }

    private void cycleHair(int d) {
        if (hairStyles.isEmpty()) return;
        int i = Math.max(0, hairStyles.indexOf(hairStyle));
        setHair(hairStyles.get(Math.floorMod(i + d, hairStyles.size())));
    }

    private Race.TailMode tailMode() {
        Race r = race();
        return r == null ? Race.TailMode.NONE : r.appearance().tail().mode();
    }

    private boolean tailChoosable() {
        Race.TailMode m = tailMode();
        return !lostTail && (m == Race.TailMode.OPTIONAL || m == Race.TailMode.DEFAULT_ON);
    }

    private TailState tailState() {
        if (lostTail) return TailState.LOST;
        return switch (tailMode()) {
            case NONE -> TailState.NONE;
            case FORCED -> TailState.PRESENT;
            default -> tailOn ? TailState.PRESENT : TailState.NONE;
        };
    }

    private Component hairName(ResourceLocation id) {
        if (Appearance.NO_HAIR.equals(id)) return tr("hair_none");
        RegistryAccess a = access();
        CosmeticPart part = a == null ? null : CharacterRegistries.getPart(a, id);
        return part == null ? Component.literal(id.getPath()) : Component.translatable(part.name());
    }

    private static String hex(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    private int auraEffective() {
        return auraColor < 0 ? kiColor : auraColor;
    }

    // ---- snapshot / choice ----------------------------------------------------------------------------------------

    private CharacterSnapshot buildSnapshot() {
        TailState ts = tailState();
        return new CharacterSnapshot(true, raceId, natureId, hairStyle, hairColor, eyeColor, kiColor, auraColor, skinColor,
                bodyHeight, bodyBuild, eyeStyle, ts, ts == TailState.PRESENT && tailWrapped, parts, false);
    }

    private CharacterChoice buildChoice() {
        Race.RaceAppearance ra = race().appearance();
        float h = Mth.clamp(bodyHeight, ra.height().min(), ra.height().max());
        float b = Mth.clamp(bodyBuild, ra.build().min(), ra.build().max());
        Appearance look = new Appearance(hairStyle, hairColor & 0xFFFFFF, eyeColor & 0xFFFFFF, kiColor & 0xFFFFFF,
                auraColor < 0 ? -1 : auraColor & 0xFFFFFF, skinColor, h, b, parts, eyeStyle);
        TailState ts = tailState();
        return new CharacterChoice(raceId, natureId, look, ts == TailState.PRESENT, ts == TailState.PRESENT && tailWrapped);
    }

    private boolean unchanged() {
        CharacterSnapshot cur = ClientCharacters.selfSnapshot();
        if (mode == Mode.CREATE || cur == null) return false;
        CharacterSnapshot now = buildSnapshot();
        return cur.race().equals(now.race()) && cur.nature().equals(now.nature())
                && cur.appearance().equals(now.appearance()) && cur.tail() == now.tail() && cur.tailWrapped() == now.tailWrapped();
    }

    /** Why the final button is disabled, or null. */
    private Component blockReason() {
        Race r = race();
        KiNature n = nature();
        if (r == null || n == null) return tr("none_available");
        if (tabEditable(Tab.RACE)) {
            RaceOpt o = raceOpt(raceId);
            if (o == null || !o.playable()) return tr("race.locked");
        }
        if (CharacterService.colorValue(kiColor) < CharacterService.MIN_COLOR_VALUE) return tr("reason.ki_too_dark");
        if (auraColor >= 0 && CharacterService.colorValue(auraColor) < CharacterService.MIN_COLOR_VALUE) return tr("reason.aura_too_dark");
        if (unchanged()) return tr("unchanged");
        return null;
    }

    // ---- submit ---------------------------------------------------------------------------------------------------

    private void submit() {
        confirmOpen = false;
        if (waiting || blockReason() != null) return;
        waiting = true;
        waitTicks = 0;
        awaitSerial = ClientCharacters.resultSerial;
        error = null;
        PacketDistributor.sendToServer(new CharacterSubmitPayload(mode, buildChoice()));
    }

    @Override
    public void tick() {
        if (!waiting) return;
        if (ClientCharacters.resultSerial != awaitSerial) {
            waiting = false;
            if (ClientCharacters.resultOk) {
                Race r = race();
                KiNature n = nature();
                Component rn = r == null ? Component.empty() : Component.translatable(r.name());
                Component nn = n == null ? Component.empty() : Component.translatable(n.name());
                if (mode == Mode.CREATE) CharacterPrompt.toastCreated(this.minecraft, rn, nn);
                else CharacterPrompt.toastChanged(this.minecraft, rn, nn);
                this.onClose();
                return;
            }
            error = Component.translatable(P + "reason." + ClientCharacters.resultReason);
        } else if (++waitTicks > RESULT_TIMEOUT_TICKS) {
            waiting = false;
            error = tr("reason.timeout");
        }
    }

    @Override
    public void removed() {
        ClientCharacters.previewOverride = null;
        super.removed();
    }

    @Override
    protected void init() {
        if (picker == null) picker = new ColorPicker(this.font);
        if (!loaded) {
            loaded = true;
            loadOptions();
        }
        updatePreview();
    }

    private void updatePreview() {
        if (!raceOpts.isEmpty() && race() != null) ClientCharacters.previewOverride = buildSnapshot();
    }

    // ---- layout + frame -------------------------------------------------------------------------------------------

    private void computeLayout() {
        int pw = PREVIEW_W_DEFAULT;
        int need = 24 + pw + RIGHT_W;
        int avail = this.width - 4;
        if (avail < need) pw = Math.max(PREVIEW_W_MIN, pw - (need - avail)); // shrink the preview first
        previewW = pw;
        panelW = 24 + pw + RIGHT_W;
        rightX = 16 + pw;
        scale = Math.min(1f, Math.min((this.width - 2f) / panelW, (this.height - 2f) / H)); // then shrink everything
        offX = (float) Math.floor((this.width - panelW * scale) / 2f);
        offY = (float) Math.floor((this.height - H * scale) / 2f);
    }

    /** Screen.render() would call renderBackground(), which blurs everything drawn so far; keep it a no-op. */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        computeLayout();
        UiCanvas c = canvas;
        c.begin(g, this.font, (mouseX - offX) / scale, (mouseY - offY) / scale, scale, offX, offY);
        updatePreview();
        boolean modal = picker != null && picker.isOpen() || confirmOpen;

        g.pose().pushPose();
        g.pose().translate(offX, offY, 0f);
        g.pose().scale(scale, scale, 1f);
        PixelUi.frame(g, 0, 0, panelW, H);
        c.blocked = modal;
        drawHeader(c);
        if (raceOpts.isEmpty() || natures.isEmpty()) {
            g.drawCenteredString(this.font, tr("none_available"), panelW / 2, H / 2, PixelUi.BAD);
        } else {
            drawPreview(c);
            drawTabs(c);
            drawBody(c);
            drawFooter(c);
        }
        c.blocked = false;
        if (modal) {
            g.pose().pushPose();
            g.pose().translate(0f, 0f, 300f); // above the 3D preview's depth
            if (picker.isOpen()) picker.render(c, panelW, H);
            else drawConfirm(c);
            g.pose().popPose();
        }
        g.pose().popPose();
        if (c.tip != null) g.renderComponentTooltip(this.font, c.tip, mouseX, mouseY);
    }

    private void drawHeader(UiCanvas c) {
        GuiGraphics g = c.g;
        g.drawString(this.font, this.title, 8, 5, PixelUi.ORANGE, true);
        Component sub = null;
        int color = PixelUi.TEXT_DIM;
        switch (mode) {
            case CREATE -> {
                if (ClientCharacters.selfFreeRecreate()) {
                    sub = tr("free");
                    color = PixelUi.GOLD;
                }
            }
            case STYLE -> sub = tr("sub.style");
            case NATURE -> sub = tr("sub.nature");
            case FULL -> {
                sub = tr("sub.full");
                color = PixelUi.BAD;
            }
        }
        if (sub != null) PixelUi.small(g, this.font, sub, 8, 15, color);
        c.button(panelW - 46, 5, 38, 14, tr("stats"), true, false, null, ClientScreens::openStats);
        PixelUi.divider(g, 4, 24, panelW - 8);
    }

    // ---- left: 3D preview -----------------------------------------------------------------------------------------

    private void drawPreview(UiCanvas c) {
        GuiGraphics g = c.g;
        int px = 8;
        int py = PREVIEW_Y;
        int pw = previewW;
        int ph = PREVIEW_H;
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrameNanos) / 1.0e9f);
        lastFrameNanos = now;
        if (autoSpin && !previewDragging && !confirmOpen && !picker.isOpen()) yaw += 40f * dt;

        g.fillGradient(px, py, px + pw, py + ph, 0xFF0E1226, 0xFF262C5E);
        PixelUi.outline(g, px - 1, py - 1, pw + 2, ph + 2, PixelUi.BORDER_DARK);

        LocalPlayer player = this.minecraft == null ? null : this.minecraft.player;
        if (player != null) {
            float sc = ph * 0.38f * zoom;
            float cx = px + pw / 2f;
            float cy = py + ph / 2f + ph * 0.03f;
            c.pushClip(px, py, pw, ph);
            if (auraOn) {
                float foot = cy + 0.95f * sc;
                float top = cy - 0.95f * sc - 0.25f * sc;
                PreviewFx.aura(g, cx, foot, top, 0.6f * sc, auraEffective(), PreviewFx.time());
            }
            renderModel(g, player, cx, cy, sc);
            c.popClip();
        }
        // rotate by dragging, zoom by scrolling
        c.drag(px, py, pw, ph, (x, y) -> {
            if (!previewDragging) {
                previewDragging = true;
            } else {
                yaw -= (float) (x - lastDragX) * 1.2f;
                pitch = Mth.clamp(pitch + (float) (y - lastDragY) * 0.4f, -20f, 20f);
            }
            lastDragX = x;
            lastDragY = y;
        });
        c.scrollArea(px, py, pw, ph, d -> zoom = Mth.clamp(zoom + 0.1f * (float) Math.signum(d), 0.7f, 1.5f));
        c.tooltip(px, py, pw, ph, tr("preview.hint"));

        int by = PREVIEW_Y + PREVIEW_H + 4;
        int bw = (pw - 4) / 3;
        boolean lookEd = tabEditable(Tab.LOOK);
        c.button(px, by, bw, 14, tr("preview.spin"), true, autoSpin, tr("preview.spin.tip"), () -> autoSpin = !autoSpin);
        c.button(px + bw + 2, by, bw, 14, tr("preview.dice"), lookEd, false, tr("preview.dice.tip"), this::randomizeLook);
        c.button(px + 2 * (bw + 2), by, bw, 14, tr("preview.aura"), true, auraOn, tr("preview.aura.tip"), () -> auraOn = !auraOn);
        drawSmallWrapped(c, tr("preview.hint"), px, by + 18, pw, PixelUi.TEXT_DIM, 2);
    }

    /** The player with the vanilla inventory-preview call; body/head turned by yaw, small pitch. */
    private void renderModel(GuiGraphics g, LocalPlayer p, float cx, float cy, float sc) {
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf cam = new Quaternionf().rotateX(pitch * ((float) Math.PI / 180f));
        pose.mul(cam);
        Vector3f translate = new Vector3f(0f, p.getBbHeight() / 2f + 0.0625f, 0f);
        float bodyRot = p.yBodyRot;
        float yRot = p.getYRot();
        float xRot = p.getXRot();
        float headRot = p.yHeadRot;
        float headRotO = p.yHeadRotO;
        p.yBodyRot = 180f + yaw;
        p.setYRot(180f + yaw);
        p.setXRot(0f);
        p.yHeadRot = p.getYRot();
        p.yHeadRotO = p.getYRot();
        try {
            InventoryScreen.renderEntityInInventory(g, cx, cy, sc, translate, pose, cam, p);
        } finally {
            p.yBodyRot = bodyRot;
            p.setYRot(yRot);
            p.setXRot(xRot);
            p.yHeadRot = headRot;
            p.yHeadRotO = headRotO;
        }
    }

    /** Dice: random Look tab within the race's rules (ki and aura are left alone). */
    private void randomizeLook() {
        Race r = race();
        if (r == null) return;
        Random rnd = new Random();
        Race.RaceAppearance ra = r.appearance();
        if (hairStyles.size() > 1 && rnd.nextInt(10) > 0) hairStyle = hairStyles.get(1 + rnd.nextInt(hairStyles.size() - 1));
        else hairStyle = hairStyles.get(rnd.nextInt(hairStyles.size()));
        hairColor = randomColor(rnd, hairAny, hairPalette, HAIR_PRESETS);
        eyeStyle = rnd.nextInt(4);
        eyeColor = randomColor(rnd, eyeAny, eyePalette, EYE_PRESETS);
        bodyHeight = ra.height().min() + rnd.nextFloat() * (ra.height().max() - ra.height().min());
        bodyBuild = ra.build().min() + rnd.nextFloat() * (ra.build().max() - ra.build().min());
        if (tailChoosable()) {
            tailOn = rnd.nextBoolean();
            tailWrapped = tailOn && rnd.nextInt(4) == 0;
        }
        error = null;
        ensureHairVisible();
    }

    private static int randomColor(Random rnd, boolean any, int[] palette, int[] presets) {
        if (!any) return palette[rnd.nextInt(palette.length)];
        if (rnd.nextInt(10) < 7) return presets[rnd.nextInt(presets.length)];
        return PixelUi.hsvToRgb(rnd.nextFloat(), 0.4f + rnd.nextFloat() * 0.6f, 0.45f + rnd.nextFloat() * 0.55f);
    }

    // ---- right: tabs, body, footer --------------------------------------------------------------------------------

    private void drawTabs(UiCanvas c) {
        GuiGraphics g = c.g;
        int tabW = (RIGHT_W - 9) / 4;
        for (Tab t : Tab.values()) {
            int x = rightX + t.ordinal() * (tabW + 3);
            boolean ed = tabEditable(t);
            boolean sel = t == tab;
            boolean hov = c.hover(x, TAB_Y, tabW, TAB_H);
            PixelUi.box(g, x, TAB_Y, tabW, TAB_H, sel ? PixelUi.ORANGE : hov ? PixelUi.BTN_HOVER : PixelUi.ROW_BG,
                    sel ? PixelUi.BORDER_LIGHT : hov ? PixelUi.ORANGE_LIGHT : PixelUi.BORDER_DARK);
            int pad = ed ? 0 : 9;
            PixelUi.fitCentered(g, this.font, Component.translatable(t.key()), x + (tabW - pad) / 2, TAB_Y + (TAB_H - 8) / 2,
                    tabW - 6 - pad, sel ? PixelUi.BODY : ed ? PixelUi.TEXT : PixelUi.TEXT_DIM, !sel);
            if (!ed) PixelUi.lock(g, x + tabW - 10, TAB_Y + 3, sel ? PixelUi.BODY : PixelUi.TEXT_DIM);
            c.hit(x, TAB_Y, tabW, TAB_H, () -> tab = t);
            if (!ed) c.tooltip(x, TAB_Y, tabW, TAB_H, tr(t == Tab.RACE ? "locked.race" : t == Tab.NATURE ? "locked.nature" : "locked.look"));
        }
    }

    private void drawBody(UiCanvas c) {
        GuiGraphics g = c.g;
        PixelUi.inset(g, rightX - 2, BODY_Y - 2, RIGHT_W + 4, BODY_H + 4);
        int t = tab.ordinal();
        int maxScroll = Math.max(0, contentH[t] - BODY_H);
        scroll[t] = Mth.clamp(scroll[t], 0, maxScroll);
        c.scrollArea(rightX, BODY_Y, RIGHT_W, BODY_H, d -> scroll[t] = Mth.clamp(scroll[t] - (int) Math.signum(d) * 14, 0, maxScroll));
        int x0 = rightX + 3;
        int y0 = BODY_Y + 2 - scroll[t];
        c.pushClip(rightX, BODY_Y, RIGHT_W - 5, BODY_H);
        int used = switch (tab) {
            case RACE -> drawRaceTab(c, x0, y0);
            case NATURE -> drawNatureTab(c, x0, y0);
            case LOOK -> drawLookTab(c, x0, y0);
            case KI -> drawKiTab(c, x0, y0);
        };
        c.popClip();
        contentH[t] = used + 4;
        if (maxScroll > 0) {
            int tx = rightX + RIGHT_W - 4;
            g.fill(tx, BODY_Y, tx + 3, BODY_Y + BODY_H, PixelUi.BAR_BG);
            int th = Math.max(10, BODY_H * BODY_H / contentH[t]);
            int ty = BODY_Y + (BODY_H - th) * scroll[t] / maxScroll;
            g.fill(tx, ty, tx + 3, ty + th, PixelUi.ORANGE_LIGHT);
        }
    }

    private void drawFooter(UiCanvas c) {
        Tab prev = nextEditable(-1);
        Tab next = nextEditable(1);
        c.button(rightX, FOOT_Y, 56, FOOT_H, tr("back"), prev != null && !waiting, false, null, () -> tab = prev);
        int bx = rightX + RIGHT_W - 72;
        if (next != null) {
            c.button(bx, FOOT_Y, 72, FOOT_H, tr("next"), !waiting, false, null, () -> tab = next);
        } else {
            Component reason = blockReason();
            boolean can = reason == null && !waiting;
            Component label = waiting ? Component.literal("...") : tr(mode == Mode.CREATE ? "create" : "apply");
            c.button(bx, FOOT_Y, 72, FOOT_H, label, can, true, reason, () -> confirmOpen = true);
        }
        Component msg = error;
        int color = PixelUi.BAD;
        if (msg == null && waiting) {
            msg = tr("waiting");
            color = PixelUi.TEXT_DIM;
        }
        if (msg == null && next == null) {
            Component reason = blockReason();
            if (reason != null) {
                msg = reason;
                color = PixelUi.TEXT_DIM;
            }
        }
        if (msg != null) drawSmallWrapped(c, msg, rightX + 62, FOOT_Y + 1, RIGHT_W - 62 - 76, color, 2);
    }

    // ---- shared drawing helpers -----------------------------------------------------------------------------------

    /** Wrapped text at 0.75 scale (maxLines 0 = unlimited); returns the height used. */
    private int drawSmallWrapped(UiCanvas c, Component text, int x, int y, int w, int color, int maxLines) {
        GuiGraphics g = c.g;
        List<FormattedCharSequence> ls = this.font.split(text, (int) (w / 0.75f));
        int n = maxLines <= 0 ? ls.size() : Math.min(ls.size(), maxLines);
        g.pose().pushPose();
        g.pose().translate(x, y, 0f);
        g.pose().scale(0.75f, 0.75f, 1f);
        for (int i = 0; i < n; i++) g.drawString(this.font, ls.get(i), 0, i * 10, color, false);
        g.pose().popPose();
        return n * 8;
    }

    private int smallHeight(Component text, int w, int maxLines) {
        int n = this.font.split(text, (int) (w / 0.75f)).size();
        return (maxLines <= 0 ? n : Math.min(n, maxLines)) * 8;
    }

    /** Padlock + gold note at the top of a locked tab; returns its height. */
    private int banner(UiCanvas c, int x, int y, String key) {
        PixelUi.lock(c.g, x, y + 1, PixelUi.GOLD);
        int h = drawSmallWrapped(c, tr(key), x + 11, y + 1, 224, PixelUi.GOLD, 0);
        return Math.max(11, h) + 5;
    }

    private void label(UiCanvas c, String key, int x, int y) {
        c.g.drawString(this.font, tr(key), x, y, PixelUi.ORANGE_LIGHT, true);
    }

    /** Swatches, 8 per row; returns the y below the last row. */
    private int swatchGrid(UiCanvas c, int x, int y, int[] palette, int current, boolean enabled, String tipKey, IntConsumer pick) {
        for (int i = 0; i < palette.length; i++) {
            final int rgb = palette[i];
            c.swatch(x + (i % 8) * 13, y + (i / 8) * 13, 11, rgb, current >= 0 && (current & 0xFFFFFF) == rgb, enabled,
                    List.of(tr(tipKey), Component.literal(hex(rgb)).withStyle(net.minecraft.ChatFormatting.GRAY)),
                    () -> {
                        pick.accept(rgb);
                        error = null;
                    });
        }
        return y + ((palette.length + 7) / 8) * 13;
    }

    /** "Custom..." button; shows the current color when it is not one of the presets. */
    private void customButton(UiCanvas c, int x, int y, int w, int current, int[] palette, boolean enabled, Runnable open) {
        boolean custom = current >= 0 && !contains(palette, current);
        c.button(x, y, w, 14, tr("custom"), enabled, custom, null, open);
        if (custom) {
            c.g.fill(x + w - 15, y + 3, x + w - 3, y + 11, PixelUi.BODY);
            c.g.fill(x + w - 14, y + 4, x + w - 4, y + 10, 0xFF000000 | (current & 0xFFFFFF));
        }
    }

    private void openPicker(String titleKey, int current, boolean bright, IntConsumer ok) {
        picker.open(tr(titleKey), current < 0 ? 0xFFFFFF : current & 0xFFFFFF, bright ? CharacterService.MIN_COLOR_VALUE : 0f, ok);
    }

    // ---- RACE tab -------------------------------------------------------------------------------------------------

    private int drawRaceTab(UiCanvas c, int x0, int y0) {
        GuiGraphics g = c.g;
        boolean ed = tabEditable(Tab.RACE);
        int y = y0;
        if (!ed) y += banner(c, x0, y, "locked.race");
        final int tile = 26;
        final int gap = 4;
        final int cols = 8;
        int n = 0;
        for (RaceOpt o : raceOpts) {
            boolean selected = o.id().equals(raceId);
            if (!ed && !selected) continue;
            drawRaceTile(c, o, x0 + (n % cols) * (tile + gap), y + (n / cols) * (tile + gap), tile, selected, ed);
            n++;
        }
        y += ((n + cols - 1) / cols) * (tile + gap) + 2;
        Race r = race();
        if (r == null) return y - y0;

        g.drawString(this.font, Component.translatable(r.name()), x0, y, PixelUi.ORANGE, true);
        y += 11;
        String rule = switch (r.appearance().tail().mode()) {
            case NONE -> "none";
            case FORCED -> "always";
            default -> "optional";
        };
        PixelUi.small(g, this.font, tr("tail_rule", tr("tail_rule." + rule)), x0, y, PixelUi.TEXT_DIM);
        y += 10;
        if (!r.lore().isEmpty()) y += drawSmallWrapped(c, Component.translatable(r.lore()), x0, y, 232, PixelUi.TEXT, 0) + 5;

        List<CharacterModText.Line> lines = new ArrayList<>(CharacterModText.lines(r.modifiers(), null));
        lines.addAll(CharacterModText.zenkai(r.zenkai()));
        if (lines.isEmpty()) {
            y += drawSmallWrapped(c, tr("no_modifiers"), x0, y, 232, PixelUi.TEXT_DIM, 0);
        } else {
            int goodY = y;
            int badY = y;
            for (CharacterModText.Line l : lines) {
                if (l.good()) goodY += drawSmallWrapped(c, l.text(), x0, goodY, COL_W, PixelUi.GOOD, 0);
            }
            for (CharacterModText.Line l : lines) {
                if (!l.good()) badY += drawSmallWrapped(c, l.text(), x0 + COL2_X, badY, COL_W, PixelUi.BAD, 0);
            }
            y = Math.max(goodY, badY);
        }
        return y - y0 + 2;
    }

    private boolean hasIcon(ResourceLocation icon) {
        return iconExists.computeIfAbsent(icon,
                id -> this.minecraft != null && this.minecraft.getResourceManager().getResource(id).isPresent());
    }

    private void drawRaceTile(UiCanvas c, RaceOpt o, int x, int y, int s, boolean selected, boolean editable) {
        GuiGraphics g = c.g;
        boolean locked = !o.playable();
        boolean hov = editable && !locked && c.hover(x, y, s, s);
        g.fill(x, y, x + s, y + s, selected ? PixelUi.ORANGE : hov ? PixelUi.ORANGE_LIGHT : PixelUi.BORDER_DARK);
        int bw = selected ? 2 : 1;
        int hue = Math.abs(o.id().getPath().hashCode()) % 360;
        g.fill(x + bw, y + bw, x + s - bw, y + s - bw, 0xFF000000 | PixelUi.hsvToRgb(hue / 360f, 0.5f, 0.55f));
        ResourceLocation icon = o.race().icon().orElse(null);
        if (icon != null && hasIcon(icon)) {
            g.blit(icon, x + 2, y + 2, s - 4, s - 4, 0f, 0f, 1, 1, 1, 1); // whole texture, whatever its size
        } else {
            String name = Component.translatable(o.race().name()).getString();
            String letter = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
            g.pose().pushPose();
            g.pose().translate(x + s / 2f, y + s / 2f, 0f);
            g.pose().scale(2f, 2f, 1f);
            g.drawString(this.font, letter, -this.font.width(letter) / 2, -4, PixelUi.TEXT, true);
            g.pose().popPose();
        }
        if (locked) {
            g.fill(x + bw, y + bw, x + s - bw, y + s - bw, 0xB0101428);
            PixelUi.lock(g, x + s - 10, y + s - 12, PixelUi.TEXT);
        }
        List<Component> tip = new ArrayList<>();
        tip.add(Component.translatable(o.race().name()).withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xF28C28).withBold(true)));
        if (locked) tip.add(tr("race.locked").withStyle(net.minecraft.ChatFormatting.GRAY));
        c.tooltip(x, y, s, s, tip);
        if (editable && !locked && !selected) c.hit(x, y, s, s, () -> {
            selectRace(o.id(), false);
            error = null;
        });
    }

    // ---- NATURE tab -----------------------------------------------------------------------------------------------

    private record Card(Entry<KiNature> e, List<CharacterModText.Line> lines, Component desc, int height) {
    }

    private int drawNatureTab(UiCanvas c, int x0, int y0) {
        GuiGraphics g = c.g;
        boolean ed = tabEditable(Tab.NATURE);
        int y = y0;
        if (!ed) y += banner(c, x0, y, "locked.nature");
        boolean canCompare = balanced != null;
        c.checkbox(x0, y, 150, 14, tr("nature.compare"), compareBalanced && canCompare, canCompare, tr("nature.compare.tip"),
                () -> compareBalanced = !compareBalanced);
        y += 18;
        final int cw = 117;
        final int innerW = cw - 10;
        boolean cmp = compareBalanced && canCompare;
        for (int row = 0; row * 2 < natures.size(); row++) {
            List<Card> cards = new ArrayList<>();
            int rowH = 56;
            for (int col = 0; col < 2 && row * 2 + col < natures.size(); col++) {
                Entry<KiNature> e = natures.get(row * 2 + col);
                List<CharacterModText.Line> ls = CharacterModText.lines(e.value().modifiers(), cmp ? balanced : null);
                Component desc = Component.translatable(e.value().description());
                int h = 5 + 10 + smallHeight(desc, innerW, 2) + 4 + (cmp ? 8 : 0);
                if (ls.isEmpty()) h += 8;
                for (CharacterModText.Line l : ls) h += smallHeight(l.text(), innerW, 0);
                h += 5;
                cards.add(new Card(e, ls, desc, h));
                rowH = Math.max(rowH, h);
            }
            for (int col = 0; col < cards.size(); col++) {
                drawNatureCard(c, cards.get(col), x0 + col * (cw + 4), y, cw, rowH, innerW, cmp, ed);
            }
            y += rowH + 4;
        }
        return y - y0;
    }

    private void drawNatureCard(UiCanvas c, Card card, int x, int y, int w, int h, int innerW, boolean cmp, boolean ed) {
        GuiGraphics g = c.g;
        KiNature n = card.e().value();
        boolean selected = card.e().id().equals(natureId);
        boolean hov = ed && c.hover(x, y, w, h);
        PixelUi.box(g, x, y, w, h, selected ? 0xFF2A2F5E : hov ? PixelUi.BTN_HOVER : PixelUi.ROW_BG,
                selected ? PixelUi.ORANGE : hov ? PixelUi.ORANGE_LIGHT : PixelUi.BORDER_DARK);
        if (selected) PixelUi.outline(g, x + 1, y + 1, w - 2, h - 2, PixelUi.ORANGE);
        int ty = y + 5;
        g.drawString(this.font, Component.translatable(n.name()), x + 5, ty, selected ? PixelUi.TEXT : PixelUi.ORANGE_LIGHT, true);
        ty += 10;
        ty += drawSmallWrapped(c, card.desc(), x + 5, ty, innerW, PixelUi.TEXT_DIM, 2) + 4;
        if (cmp) {
            PixelUi.small(g, this.font, tr(card.e().id().equals(CharacterData.DEFAULT_NATURE) ? "nature.baseline" : "nature.vs"),
                    x + 5, ty, PixelUi.TEXT_DIM);
            ty += 8;
        }
        if (card.lines().isEmpty()) {
            drawSmallWrapped(c, tr(cmp && !card.e().id().equals(CharacterData.DEFAULT_NATURE) ? "nature.same" : "no_modifiers"),
                    x + 5, ty, innerW, PixelUi.TEXT_DIM, 1);
        }
        for (CharacterModText.Line l : card.lines()) {
            if (l.good()) ty += drawSmallWrapped(c, l.text(), x + 5, ty, innerW, PixelUi.GOOD, 0);
        }
        for (CharacterModText.Line l : card.lines()) {
            if (!l.good()) ty += drawSmallWrapped(c, l.text(), x + 5, ty, innerW, PixelUi.BAD, 0);
        }
        if (!ed && !selected) g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0x80101428);
        c.tooltip(x, y, w, h, List.of(Component.translatable(n.name()).withStyle(net.minecraft.ChatFormatting.GOLD), card.desc()));
        if (ed && !selected) c.hit(x, y, w, h, () -> {
            natureId = card.e().id();
            error = null;
        });
    }

    // ---- LOOK tab -------------------------------------------------------------------------------------------------

    private int drawLookTab(UiCanvas c, int x0, int y0) {
        GuiGraphics g = c.g;
        boolean ed = tabEditable(Tab.LOOK);
        int y = y0;
        if (!ed) y += banner(c, x0, y, "locked.look");
        int lx = x0;
        int rx = x0 + COL2_X;

        // ---- hair (left column)
        label(c, "hair", lx, y);
        boolean choosable = ed && hairStyles.size() > 1;
        c.button(lx, y + 11, 14, 14, Component.literal("<"), choosable, false, null, () -> cycleHair(-1));
        PixelUi.box(g, lx + 16, y + 11, COL_W - 32, 14, PixelUi.BAR_BG, PixelUi.BORDER_DARK);
        PixelUi.fitCentered(g, this.font, hairName(hairStyle), lx + COL_W / 2, y + 14, COL_W - 38, PixelUi.TEXT, true);
        c.button(lx + COL_W - 14, y + 11, 14, 14, Component.literal(">"), choosable, false, null, () -> cycleHair(1));

        int listY = y + 28;
        final int rows = 4;
        final int rowH = 11;
        PixelUi.box(g, lx, listY, COL_W, rows * rowH + 2, PixelUi.BAR_BG, PixelUi.BORDER_DARK);
        final int maxList = Math.max(0, hairStyles.size() - rows);
        hairListScroll = Mth.clamp(hairListScroll, 0, maxList);
        c.pushClip(lx + 1, listY + 1, COL_W - 2, rows * rowH);
        for (int i = 0; i < rows && hairListScroll + i < hairStyles.size(); i++) {
            final ResourceLocation id = hairStyles.get(hairListScroll + i);
            int ry = listY + 1 + i * rowH;
            boolean sel = id.equals(hairStyle);
            boolean hov = ed && c.hover(lx + 1, ry, COL_W - 6, rowH);
            if (sel) g.fill(lx + 1, ry, lx + COL_W - 5, ry + rowH, PixelUi.SEL_BG);
            else if (hov) g.fill(lx + 1, ry, lx + COL_W - 5, ry + rowH, PixelUi.BTN_HOVER);
            g.drawString(this.font, hairName(id), lx + 4, ry + 2, sel ? PixelUi.TEXT : ed ? PixelUi.TEXT_DIM : PixelUi.TEXT_OFF, false);
            if (ed) c.hit(lx + 1, ry, COL_W - 6, rowH, () -> setHair(id));
        }
        c.popClip();
        if (maxList > 0) {
            int th = Math.max(6, rows * rowH * rows / hairStyles.size());
            int ty = listY + 1 + (rows * rowH - th) * hairListScroll / maxList;
            g.fill(lx + COL_W - 4, listY + 1, lx + COL_W - 1, listY + 1 + rows * rowH, PixelUi.ROW_BG);
            g.fill(lx + COL_W - 4, ty, lx + COL_W - 1, ty + th, PixelUi.ORANGE_LIGHT);
        }
        c.scrollArea(lx, listY, COL_W, rows * rowH + 2,
                d -> hairListScroll = Mth.clamp(hairListScroll - (int) Math.signum(d), 0, maxList));

        int cy = y + 78;
        label(c, "hair_color", lx, cy);
        boolean colorOn = ed && (!Appearance.NO_HAIR.equals(hairStyle) || tailState() == TailState.PRESENT);
        int gy = swatchGrid(c, lx, cy + 11, hairPalette, hairColor, colorOn, "hair_color", col -> hairColor = col);
        if (hairAny) {
            customButton(c, lx, gy + 2, COL_W, hairColor, hairPalette, colorOn,
                    () -> openPicker("picker.title", hairColor, false, col -> hairColor = col));
            gy += 16;
        }
        int leftEnd = gy;

        // ---- eyes, body, tail (right column)
        label(c, "eyes", rx, y);
        for (int i = 0; i < 4; i++) {
            final int style = i;
            c.button(rx + i * 29, y + 11, 27, 14, tr("eye_style." + i), ed, eyeStyle == i, null, () -> eyeStyle = style);
        }
        boolean eyeOn = ed && eyeStyle > 0;
        int ey = swatchGrid(c, rx, y + 29, eyePalette, eyeColor, eyeOn, "eye_color", col -> eyeColor = col);
        if (eyeAny) {
            customButton(c, rx, ey + 2, COL_W, eyeColor, eyePalette, eyeOn,
                    () -> openPicker("picker.title", eyeColor, false, col -> eyeColor = col));
            ey += 16;
        }
        Race r = race();
        Race.RaceAppearance ra = r == null ? null : r.appearance();
        int sy = Math.max(ey, y + 56) + 4;
        slider(c, rx, sy, COL_W, "height", bodyHeight, ra == null ? 1f : ra.height().min(), ra == null ? 1f : ra.height().max(),
                ed, v -> bodyHeight = v);
        slider(c, rx, sy + 16, COL_W, "build", bodyBuild, ra == null ? 1f : ra.build().min(), ra == null ? 1f : ra.build().max(),
                ed, v -> bodyBuild = v);
        Race.TailMode tm = tailMode();
        Component tailLabel;
        if (lostTail) tailLabel = tr("tail.lost");
        else if (tm == Race.TailMode.NONE) tailLabel = tr("no_tail");
        else if (tm == Race.TailMode.FORCED) tailLabel = tr("tail.always");
        else tailLabel = tr("tail", tr(tailOn ? "on" : "off"));
        c.button(rx, sy + 36, COL_W, 14, tailLabel, ed && tailChoosable(), tailState() == TailState.PRESENT,
                tr("tail_rule", tr("tail_rule." + (tm == Race.TailMode.NONE ? "none" : tm == Race.TailMode.FORCED ? "always" : "optional"))),
                () -> {
                    tailOn = !tailOn;
                    if (!tailOn) tailWrapped = false;
                });
        c.button(rx, sy + 52, COL_W, 14, tr("wrapped", tr(tailWrapped ? "on" : "off")), ed && tailState() == TailState.PRESENT,
                tailWrapped && tailState() == TailState.PRESENT, null, () -> tailWrapped = !tailWrapped);
        return Math.max(leftEnd, sy + 66) - y0;
    }

    private void slider(UiCanvas c, int x, int y, int w, String labelKey, float value, float min, float max, boolean enabled,
                        java.util.function.Consumer<Float> set) {
        GuiGraphics g = c.g;
        boolean on = enabled && max > min + 1.0E-4f;
        float frac = max > min ? Mth.clamp((value - min) / (max - min), 0f, 1f) : 0.5f;
        PixelUi.box(g, x, y, w, 14, PixelUi.BAR_BG, on && c.hover(x, y, w, 14) ? PixelUi.ORANGE_LIGHT : PixelUi.BORDER_DARK);
        int fw = (int) ((w - 2) * frac);
        g.fill(x + 1, y + 1, x + 1 + fw, y + 13, on ? 0xFF8A4A12 : 0xFF2A2F55);
        int kx = Mth.clamp(x + 1 + fw - 1, x + 1, x + w - 3);
        g.fill(kx, y, kx + 2, y + 14, on ? PixelUi.ORANGE_LIGHT : PixelUi.BORDER_DARK);
        Component text = tr("size_value", tr(labelKey), String.format(Locale.ROOT, "%d", Math.round(value * 100f)));
        PixelUi.fitCentered(g, this.font, text, x + w / 2, y + 3, w - 6, on ? PixelUi.TEXT : PixelUi.TEXT_OFF, true);
        if (on) {
            c.drag(x, y, w, 14, (mx, my) -> {
                float f = Mth.clamp((float) ((mx - x - 1) / (w - 2)), 0f, 1f);
                set.accept(Mth.clamp(Math.round((min + (max - min) * f) * 100f) / 100f, min, max));
                error = null;
            });
        }
        c.tooltip(x, y, w, 14, tr("size_range", Math.round(min * 100f), Math.round(max * 100f)));
    }

    // ---- KI & AURA tab --------------------------------------------------------------------------------------------

    private int drawKiTab(UiCanvas c, int x0, int y0) {
        GuiGraphics g = c.g;
        boolean ed = tabEditable(Tab.KI);
        int y = y0;
        if (!ed) y += banner(c, x0, y, "locked.look");
        label(c, "ki_color", x0, y);
        int gy = swatchGrid(c, x0, y + 11, KI_PRESETS, kiColor, ed, "ki_color", col -> kiColor = col);
        customButton(c, x0, gy + 2, COL_W, kiColor, KI_PRESETS, ed,
                () -> openPicker("picker.title.ki", kiColor, true, col -> kiColor = col));
        PixelUi.small(g, this.font, tr("min_brightness"), x0, gy + 19, PixelUi.TEXT_DIM);

        // live ki orb
        int ox = x0 + COL2_X;
        PixelUi.inset(g, ox, y, COL_W, 56);
        c.pushClip(ox + 2, y + 2, COL_W - 4, 52);
        PreviewFx.orb(g, ox + COL_W / 2f, y + 28f, 16f, kiColor, PreviewFx.time());
        c.popClip();

        int ay = y + 66;
        label(c, "aura_color", x0, ay);
        boolean match = auraColor < 0;
        int ag = swatchGrid(c, x0, ay + 11, KI_PRESETS, match ? -1 : auraColor, ed && !match, "aura_color", col -> auraColor = col);
        customButton(c, x0, ag + 2, COL_W, match ? -1 : auraColor, KI_PRESETS, ed && !match,
                () -> openPicker("picker.title.aura", auraColor, true, col -> auraColor = col));
        c.checkbox(ox, ay + 11, COL_W, 14, tr("match_ki"), match, ed, tr("match_ki.tip"), () -> auraColor = match ? kiColor : -1);
        drawSmallWrapped(c, tr("aura_hint"), ox, ay + 30, COL_W, PixelUi.TEXT_DIM, 4);
        return ag + 16 - y0;
    }

    // ---- confirm modal --------------------------------------------------------------------------------------------

    private Component confirmText() {
        Race r = race();
        KiNature n = nature();
        Component rn = r == null ? Component.empty() : Component.translatable(r.name());
        Component nn = n == null ? Component.empty() : Component.translatable(n.name());
        return tr("confirm." + mode.name().toLowerCase(Locale.ROOT), rn, nn);
    }

    private void drawConfirm(UiCanvas c) {
        GuiGraphics g = c.g;
        int mw = 250;
        int mh = 104;
        int x0 = (panelW - mw) / 2;
        int y0 = (H - mh) / 2;
        g.fill(0, 0, panelW, H, 0xB0000000);
        PixelUi.frame(g, x0, y0, mw, mh);
        g.drawString(this.font, tr("confirm.title"), x0 + 10, y0 + 8, PixelUi.ORANGE, true);
        int ty = y0 + 24;
        for (FormattedCharSequence l : this.font.split(confirmText(), mw - 20)) {
            g.drawString(this.font, l, x0 + 10, ty, PixelUi.TEXT, false);
            ty += 10;
        }
        int by = y0 + mh - 24;
        c.button(x0 + 10, by, 100, 16, tr("confirm.yes"), true, true, null, this::submit);
        c.button(x0 + mw - 110, by, 100, 16, tr("confirm.no"), true, false, null, () -> confirmOpen = false);
    }

    // ---- input ----------------------------------------------------------------------------------------------------

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (picker.isOpen()) return picker.keyPressed(keyCode, scanCode, modifiers);
        if (confirmOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) confirmOpen = false;
            return true;
        }
        if (ModKeys.OPEN_STATS.matches(keyCode, scanCode)) { // same key closes it
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (picker.isOpen()) return picker.charTyped(codePoint, modifiers);
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double vx = (mouseX - offX) / scale;
        double vy = (mouseY - offY) / scale;
        if (picker.isOpen()) {
            picker.preClick();
            canvas.mouseClicked(vx, vy, button);
            return true;
        }
        if (canvas.mouseClicked(vx, vy, button)) return true;
        return confirmOpen || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (canvas.mouseDragged((mouseX - offX) / scale, (mouseY - offY) / scale)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        canvas.mouseReleased();
        previewDragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (picker.isOpen() || confirmOpen) return true;
        if (canvas.mouseScrolled((mouseX - offX) / scale, (mouseY - offY) / scale, scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
