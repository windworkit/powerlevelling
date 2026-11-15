package com.powerlevelling.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.powerlevelling.character.CharacterMods;
import com.powerlevelling.character.CharacterRegistries;
import com.powerlevelling.character.CharacterSnapshot;
import com.powerlevelling.character.KiNature;
import com.powerlevelling.character.Race;
import com.powerlevelling.character.TailState;
import com.powerlevelling.client.ClientCharacters;
import com.powerlevelling.client.ClientPlayerData;
import com.powerlevelling.client.ModKeys;
import com.powerlevelling.client.hud.HudSprites;
import com.powerlevelling.client.hud.HudSprites.Sprite;
import com.powerlevelling.data.KiProgression;
import com.powerlevelling.data.KiProgression.Stat;
import com.powerlevelling.data.KiProgression.Track;
import com.powerlevelling.network.SpendStatPayload;
import com.powerlevelling.network.TailTogglePayload;
import com.powerlevelling.progression.StatEffects;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Stats panel (Addendum A.6). Doesn't pause the game. Everything is read from {@link ClientPlayerData} every frame,
 * so it updates as soon as the server's sync arrives.
 */
public class StatsScreen extends Screen {
    // Panel geometry (all relative to the panel's top-left).
    private static final int W = 260;
    private static final int H = 190;
    private static final int ROW_X = 8;
    private static final int ROW_W = 148;
    private static final int ROW_Y0 = 58;
    private static final int ROW_H = 20;
    private static final int COL2_X = 166;
    private static final int COL2_W = 86;
    private static final int TRACK_H = 26;

    private static final int EFFECT_COLOR = 0xFF8FB4FF;
    /** Atlas icon per stat, in Stat order (strength, ki power, ki capacity, vitality, defense, speed). */
    private static final Sprite[] STAT_ICONS = {
            HudSprites.ICON_SWORD, HudSprites.ICON_SPARK, HudSprites.ICON_FLAME,
            HudSprites.ICON_HEART, HudSprites.ICON_SHIELD, HudSprites.ICON_BOLT};

    private int left;
    private int top;
    private final Button[] plusButtons = new Button[Stat.values().length];
    /** "Wrap tail" / "Unwrap tail", only visible while the character has a tail. */
    private Button tailButton;

    public StatsScreen() {
        super(Component.translatable("gui.powerlevelling.stats.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        left = (this.width - W) / 2;
        top = (this.height - H) / 2;
        for (Stat stat : Stat.values()) {
            int i = stat.ordinal();
            Button b = Button.builder(Component.literal("+"), btn -> spend(stat))
                    .bounds(left + ROW_X + ROW_W - 14, top + ROW_Y0 + i * ROW_H + 3, 12, 12)
                    .build();
            plusButtons[i] = this.addRenderableWidget(b);
        }
        // Next to the character line (top right of the header, above the mastery column).
        tailButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.powerlevelling.stats.wrap_tail"),
                        btn -> PacketDistributor.sendToServer(new TailTogglePayload()))
                .bounds(left + W - 8 - 68, top + 34, 68, 12).build());
        tailButton.visible = false;
    }

    /** Sends a spend request (+1, or +5 with Shift), clamped to what we can afford; the server re-validates. */
    private void spend(Stat stat) {
        int want = hasShiftDown() ? 5 : 1;
        int amount = Math.min(want, Math.min(ClientPlayerData.unspent, ClientPlayerData.statCap - ClientPlayerData.stat(stat)));
        if (amount > 0) PacketDistributor.sendToServer(new SpendStatPayload(stat.id, amount));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ModKeys.OPEN_STATS.matches(keyCode, scanCode)) { // same key closes it
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ---- rendering ------------------------------------------------------------------------------------------------

    /** Screen.render() calls renderBackground(), which in 1.21 blurs everything drawn so far (our panel included).
     *  Overriding it with a no-op keeps the panel sharp; we draw our own dim/panel in render(). */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No renderBackground(): the world stays visible and un-blurred behind the panel.
        PixelUi.frame(g, left, top, W, H);
        drawHeader(g);
        drawStatRows(g);
        drawMastery(g);

        for (Stat stat : Stat.values()) {
            plusButtons[stat.ordinal()].active = ClientPlayerData.unspent > 0
                    && ClientPlayerData.stat(stat) < ClientPlayerData.statCap;
        }
        CharacterSnapshot self = ClientCharacters.selfSnapshot();
        boolean hasTail = self != null && self.tail() == TailState.PRESENT;
        tailButton.visible = hasTail;
        tailButton.active = hasTail;
        if (hasTail) {
            tailButton.setMessage(Component.translatable(self.tailWrapped()
                    ? "gui.powerlevelling.stats.unwrap_tail" : "gui.powerlevelling.stats.wrap_tail"));
        }
        super.render(g, mouseX, mouseY, partialTick); // [+] buttons, tail button

        // Tooltip for the hovered stat row.
        for (Stat stat : Stat.values()) {
            int ry = top + ROW_Y0 + stat.ordinal() * ROW_H;
            if (mouseX >= left + ROW_X && mouseX < left + ROW_X + ROW_W && mouseY >= ry && mouseY < ry + ROW_H - 2) {
                g.renderComponentTooltip(this.font, tooltipFor(stat), mouseX, mouseY);
                break;
            }
        }
    }

    private void drawHeader(GuiGraphics g) {
        // Title + Power Level
        g.drawString(this.font, this.title, left + 8, top + 6, PixelUi.ORANGE, true);
        Component pl = Component.translatable("hud.powerlevelling.power_level", String.format(Locale.ROOT, "%,d", ClientPlayerData.powerLevel()));
        g.drawString(this.font, pl, left + W - 8 - this.font.width(pl), top + 6, PixelUi.TEXT, true);

        // Level + EXP bar
        g.drawString(this.font, Component.translatable("gui.powerlevelling.stats.level", ClientPlayerData.level),
                left + 8, top + 19, PixelUi.ORANGE_LIGHT, true);
        int bx = left + 40;
        int bw = W - 48;
        boolean maxed = ClientPlayerData.level >= ClientPlayerData.levelCap;
        float frac = maxed ? 1f : ClientPlayerData.exp / (float) Math.max(1, ClientPlayerData.expToNext);
        PixelUi.bar(g, bx, top + 18, bw, 10, frac, HudSprites.FILL_EXP);
        Component expText = maxed ? Component.translatable("gui.powerlevelling.stats.exp_max")
                : Component.translatable("gui.powerlevelling.stats.exp", ClientPlayerData.exp, ClientPlayerData.expToNext);
        PixelUi.smallCentered(g, this.font, expText, bx + bw / 2, top + 20, PixelUi.TEXT);

        // Divider, character line, unspent points, column header
        PixelUi.divider(g, left + 4, top + 32, W - 8);
        drawCharacterLine(g);
        boolean has = ClientPlayerData.unspent > 0;
        g.drawString(this.font, Component.translatable("gui.powerlevelling.stats.unspent", ClientPlayerData.unspent),
                left + 8, top + 48, has ? PixelUi.GOLD : PixelUi.TEXT_DIM, true);
        g.drawString(this.font, Component.translatable("gui.powerlevelling.stats.mastery"),
                left + COL2_X, top + 48, PixelUi.ORANGE, true);
        g.fill(left + 160, top + 48, left + 161, top + H - 6, PixelUi.BORDER_DARK); // column divider
    }

    /** "Race · Nature" (translated) or the not-created hint. Text only; the real routing to the character screen is C4. */
    private void drawCharacterLine(GuiGraphics g) {
        Component text;
        int color;
        if (ClientPlayerData.characterCreated) {
            RegistryAccess access = registryAccess();
            Race race = access == null ? null : CharacterRegistries.getRace(access, ClientPlayerData.characterRace);
            KiNature nature = access == null ? null : CharacterRegistries.getNature(access, ClientPlayerData.characterNature);
            Component raceName = race != null ? Component.translatable(race.name()) : Component.literal(ClientPlayerData.characterRace.getPath());
            Component natureName = nature != null ? Component.translatable(nature.name()) : Component.literal(ClientPlayerData.characterNature.getPath());
            text = Component.translatable("gui.powerlevelling.stats.character", raceName, natureName);
            color = PixelUi.ORANGE_LIGHT;
        } else {
            text = Component.translatable("gui.powerlevelling.stats.no_character");
            color = PixelUi.TEXT_DIM;
        }
        PixelUi.small(g, this.font, text, left + 8, top + 36, color);
    }

    private RegistryAccess registryAccess() {
        return this.minecraft != null && this.minecraft.level != null ? this.minecraft.level.registryAccess() : null;
    }

    /** Race + nature modifiers for previews (neutral if the registries are not available yet). */
    private CharacterMods mods() {
        RegistryAccess access = registryAccess();
        return access == null ? CharacterMods.NEUTRAL : ClientPlayerData.mods(access);
    }

    private void drawStatRows(GuiGraphics g) {
        for (Stat stat : Stat.values()) {
            int i = stat.ordinal();
            int x = left + ROW_X;
            int y = top + ROW_Y0 + i * ROW_H;
            int value = ClientPlayerData.stat(stat);

            PixelUi.inset(g, x, y, ROW_W, ROW_H - 2);
            PixelUi.icon(g, STAT_ICONS[i], x + 3, y + 4);

            g.drawString(this.font, Component.translatable("stat.powerlevelling." + stat.id), x + 15, y + 2, PixelUi.TEXT, true);
            String v = Integer.toString(value);
            int vColor = value >= ClientPlayerData.statCap ? PixelUi.GOLD : PixelUi.TEXT;
            g.drawString(this.font, v, x + ROW_W - 20 - this.font.width(v), y + 2, vColor, true);
            PixelUi.small(g, this.font, effect(stat, value, mods()), x + 15, y + 11, EFFECT_COLOR);
        }
    }

    private void drawMastery(GuiGraphics g) {
        int x = left + COL2_X;
        for (Track t : Track.values()) {
            int i = t.ordinal();
            int y = top + ROW_Y0 + i * TRACK_H;
            int lvl = ClientPlayerData.levels[i];
            boolean maxed = lvl >= KiProgression.MAX_LEVEL;

            g.drawString(this.font, Component.translatable("track.powerlevelling." + t.id), x, y, PixelUi.TEXT, true);
            Component lv = Component.translatable("gui.powerlevelling.stats.track_level", lvl);
            g.drawString(this.font, lv, x + COL2_W - this.font.width(lv), y, PixelUi.ORANGE_LIGHT, true);

            // Progress towards the next 5-level milestone (each milestone pays 1 stat point).
            float frac;
            Component next;
            if (maxed) {
                frac = 1f;
                next = Component.translatable("gui.powerlevelling.stats.track_max");
            } else {
                float within = lvl % KiProgression.MASTERY_MILESTONE_EVERY
                        + Math.min(1f, ClientPlayerData.xp[i] / (float) KiProgression.masteryXpToNext(lvl));
                frac = within / KiProgression.MASTERY_MILESTONE_EVERY;
                next = Component.translatable("gui.powerlevelling.stats.next_point",
                        (lvl / KiProgression.MASTERY_MILESTONE_EVERY + 1) * KiProgression.MASTERY_MILESTONE_EVERY);
            }
            PixelUi.bar(g, x, y + 10, COL2_W, 6, frac, HudSprites.FILL_KI);
            PixelUi.small(g, this.font, next, x, y + 18, PixelUi.TEXT_DIM);
        }

        // Zenkai indicator
        int zy = top + 166;
        g.fill(x, zy, x + COL2_W, zy + 1, PixelUi.BORDER_DARK);
        int secs = ClientPlayerData.zenkaiRemainingSeconds();
        if (secs <= 0) {
            g.drawString(this.font, Component.translatable("gui.powerlevelling.stats.zenkai_ready"), x, zy + 5, PixelUi.GOLD, true);
        } else {
            String time = String.format(Locale.ROOT, "%d:%02d", secs / 60, secs % 60);
            g.drawString(this.font, Component.translatable("gui.powerlevelling.stats.zenkai_cooldown", time), x, zy + 5, PixelUi.TEXT_DIM, true);
        }
    }

    // ---- text helpers ---------------------------------------------------------------------------------------------

    /** One decimal with an explicit sign ("+12.5", "-3.0"). */
    private static String signed(double d) {
        return String.format(Locale.ROOT, "%+.1f", d);
    }

    /**
     * Short effect preview for {@code v} points in {@code stat}, including race + nature (via StatEffects, the same
     * code the server uses). Percent values are the total change vs vanilla/base; ki and HP are the total change vs the
     * unmodified 1000 ki / 100 HP base, so a nature's flat penalties show up even at 0 points.
     */
    private static Component effect(Stat stat, int v, CharacterMods m) {
        String key = "gui.powerlevelling.effect." + stat.id;
        return switch (stat) {
            case STRENGTH -> Component.translatable(key, signed((StatEffects.meleeMultiplier(v, m) - 1.0) * 100.0));
            case KI_POWER -> Component.translatable(key, signed((StatEffects.kiDamageMultiplier(v, m) - 1.0) * 100.0));
            case KI_CAPACITY -> Component.translatable(key,
                    String.format(Locale.ROOT, "%+d", Math.round(StatEffects.maxKi(v, m) - StatEffects.BASE_KI)));
            case VITALITY -> {
                double base = StatEffects.VANILLA_HP + ClientPlayerData.baseBonusHealth;
                yield Component.translatable(key,
                        String.format(Locale.ROOT, "%+d", Math.round(StatEffects.maxHp(v, m, ClientPlayerData.baseBonusHealth) - base)));
            }
            case DEFENSE -> Component.translatable(key, signed((StatEffects.damageTakenMultiplier(v, m) - 1.0) * 100.0));
            case SPEED -> Component.translatable(key, signed(StatEffects.moveSpeedBonus(v, m) * 100.0));
        };
    }

    private List<Component> tooltipFor(Stat stat) {
        int v = ClientPlayerData.stat(stat);
        CharacterMods m = mods();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("stat.powerlevelling." + stat.id).withStyle(Style.EMPTY.withColor(0xF28C28).withBold(true)));
        lines.add(Component.translatable("gui.powerlevelling.tip." + stat.id));
        lines.add(Component.translatable("gui.powerlevelling.tip.current", effect(stat, v, m)));
        if (v < ClientPlayerData.statCap) {
            lines.add(Component.translatable("gui.powerlevelling.tip.next", effect(stat, v + 1, m)));
        }
        lines.add(Component.translatable("gui.powerlevelling.tip.cap", v, ClientPlayerData.statCap));
        lines.add(Component.translatable("gui.powerlevelling.tip.shift"));
        return lines;
    }
}
