package com.powerlevelling.progression;

import com.powerlevelling.PowerLevelling;
import com.powerlevelling.character.Channel;
import com.powerlevelling.character.CharacterData;
import com.powerlevelling.character.CharacterMods;
import com.powerlevelling.character.CharacterService;
import com.powerlevelling.client.ClientPlayerData;
import com.powerlevelling.config.ServerConfig;
import com.powerlevelling.data.CombatResources;
import com.powerlevelling.data.KiProgression;
import com.powerlevelling.data.KiProgression.Stat;
import com.powerlevelling.registry.ModAttachments;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Single source of truth for what stat points + race + ki nature do (Addendum A.4 + B.1).
 *
 * <p>Per-stat effects: {@code effect = (1 + perPoint * points * scaling[stat]) * mult[channel] + flat[channel]}
 * (additive-base effects such as max ki / max HP / max stamina put their base inside the first bracket instead).
 * Caps (defense DR 0.60, speed +0.40, each raised by the matching flat channel) are clamped after.
 *
 * <p>Three layers of overloads, all pure except the Player ones and {@link #apply}:
 * <ul>
 *   <li>{@code (int points, CharacterMods)}: one stat's points in, effect out (used by previews and the balance command);</li>
 *   <li>{@code (KiProgression, CharacterMods)}: reads the stat from the progression;</li>
 *   <li>{@code (Player)}: server players use the attachments + CharacterService mods, client players use
 *       ClientPlayerData + mods built from the synced registries.</li>
 * </ul>
 */
public final class StatEffects {
    public static final ResourceLocation VITALITY_HP_ID = ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "vitality_hp");
    public static final ResourceLocation SPEED_MOVE_ID = ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "speed_move");
    /** Attributes.SCALE modifier (ADD_MULTIPLIED_BASE, avg(height, build) - 1); only present while sizeAffectsHitbox is on. */
    public static final ResourceLocation BODY_SCALE_ID = ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "body_scale");

    // Per-point values (Addendum A.4).
    public static final double STRENGTH_PER_POINT = 0.015;
    public static final double KI_POWER_PER_POINT = 0.015;
    public static final int KI_PER_POINT = 25;
    public static final int HP_PER_POINT = 5;
    public static final double DEFENSE_PER_POINT = 0.006;
    public static final double DEFENSE_CAP = 0.6;
    public static final double SPEED_PER_POINT = 0.004;
    public static final double SPEED_CAP = 0.4;
    public static final double STAMINA_PER_SPEED = 0.5;
    public static final double STAMINA_REGEN_PER_SPEED = 0.005;

    public static final float BASE_KI = 1000f;
    /** Vanilla player max health (the attribute base the vitality modifier is added to). */
    public static final double VANILLA_HP = 20.0;
    /** Default of the baseBonusHealth server config; used only where the real value is unknown. */
    public static final double DEFAULT_BASE_BONUS_HEALTH = 80.0;

    private StatEffects() {
    }

    // ---- pure helpers: points in, effect out ---------------------------------------------------------------------

    /** (1 + perPoint * points * scaling) * mult + flat. */
    private static double stat(double perPoint, int points, Stat stat, Channel channel, CharacterMods m) {
        return (1.0 + perPoint * points * m.scaling(stat)) * m.mult(channel) + m.flat(channel);
    }

    /** Channel without a stat: mult + flat (never negative). */
    private static double channel(Channel c, CharacterMods m) {
        return Math.max(0.0, (double) m.mult(c) + m.flat(c));
    }

    /** Melee damage multiplier from Strength points. */
    public static double meleeMultiplier(int strength, CharacterMods m) {
        return Math.max(0.0, stat(STRENGTH_PER_POINT, strength, Stat.STRENGTH, Channel.MELEE_DAMAGE, m));
    }

    /** Ki attack damage multiplier from Ki Power points. */
    public static double kiDamageMultiplier(int kiPower, CharacterMods m) {
        return Math.max(0.0, stat(KI_POWER_PER_POINT, kiPower, Stat.KI_POWER, Channel.KI_DAMAGE, m));
    }

    /** Max ki: (1000 + 25 * points * scaling) * mult + flat. */
    public static float maxKi(int kiCapacity, CharacterMods m) {
        double base = BASE_KI + KI_PER_POINT * kiCapacity * m.scaling(Stat.KI_CAPACITY);
        return (float) Math.max(1.0, base * m.mult(Channel.MAX_KI) + m.flat(Channel.MAX_KI));
    }

    /** Total max HP: ((20 + baseBonusHealth) + 5 * points * scaling) * mult + flat. */
    public static double maxHp(int vitality, CharacterMods m, double baseBonusHealth) {
        double base = VANILLA_HP + baseBonusHealth + HP_PER_POINT * vitality * m.scaling(Stat.VITALITY);
        return Math.max(1.0, base * m.mult(Channel.MAX_HP) + m.flat(Channel.MAX_HP));
    }

    /** The ADD_VALUE modifier for the MAX_HEALTH attribute (total HP minus vanilla's 20). */
    public static double maxHpBonus(int vitality, CharacterMods m, double baseBonusHealth) {
        return maxHp(vitality, m, baseBonusHealth) - VANILLA_HP;
    }

    /**
     * Multiply incoming damage by this. Defense gives DR = min(0.60 + flat defense_cap, 0.006 * points * scaling);
     * then the result is multiplied by damage_taken (+ its flat). Never below 0.
     */
    public static double damageTakenMultiplier(int defense, CharacterMods m) {
        double cap = Math.min(0.95, Math.max(0.0, DEFENSE_CAP + m.flat(Channel.DEFENSE_CAP)));
        double dr = Math.min(cap, DEFENSE_PER_POINT * defense * m.scaling(Stat.DEFENSE));
        return Math.max(0.0, (1.0 - dr) * m.mult(Channel.DAMAGE_TAKEN) + m.flat(Channel.DAMAGE_TAKEN));
    }

    /** Fraction added to base movement speed. Capped at 0.40 + flat speed_cap; may be negative (nature penalties). */
    public static double moveSpeedBonus(int speed, CharacterMods m) {
        double bonus = stat(SPEED_PER_POINT, speed, Stat.SPEED, Channel.MOVE_SPEED, m) - 1.0;
        double cap = Math.max(0.0, SPEED_CAP + m.flat(Channel.SPEED_CAP));
        return Math.max(-0.9, Math.min(cap, bonus));
    }

    /** Max stamina: ((50 + 50 * endurance/100) + 0.5 * points * scaling) * mult + flat. */
    public static float maxStamina(int speed, float endurance, CharacterMods m) {
        double base = 50.0 + 50.0 * endurance / CombatResources.MAX_ENDURANCE
                + STAMINA_PER_SPEED * speed * m.scaling(Stat.SPEED);
        return (float) Math.max(1.0, base * m.mult(Channel.MAX_STAMINA) + m.flat(Channel.MAX_STAMINA));
    }

    /** Stamina regen multiplier: (1 + 0.005 * points * scaling) * mult + flat. */
    public static double staminaRegenMult(int speed, CharacterMods m) {
        return Math.max(0.0, stat(STAMINA_REGEN_PER_SPEED, speed, Stat.SPEED, Channel.STAMINA_REGEN, m));
    }

    // Channels that no stat feeds: only race + nature.
    public static double kiRegenMult(CharacterMods m) {
        return channel(Channel.KI_REGEN, m);
    }

    public static double kiChargeRateMult(CharacterMods m) {
        return channel(Channel.KI_CHARGE_RATE, m);
    }

    public static double kiCostMult(CharacterMods m) {
        return channel(Channel.KI_COST, m);
    }

    public static double expGainMult(CharacterMods m) {
        return channel(Channel.EXP_GAIN, m);
    }

    public static double masteryXpMult(CharacterMods m) {
        return channel(Channel.MASTERY_XP, m);
    }

    // ---- (KiProgression, CharacterMods) overloads -----------------------------------------------------------------

    public static double meleeMultiplier(KiProgression p, CharacterMods m) {
        return meleeMultiplier(p.stat(Stat.STRENGTH), m);
    }

    public static double kiDamageMultiplier(KiProgression p, CharacterMods m) {
        return kiDamageMultiplier(p.stat(Stat.KI_POWER), m);
    }

    public static float maxKi(KiProgression p, CharacterMods m) {
        return maxKi(p.stat(Stat.KI_CAPACITY), m);
    }

    public static double maxHp(KiProgression p, CharacterMods m, double baseBonusHealth) {
        return maxHp(p.stat(Stat.VITALITY), m, baseBonusHealth);
    }

    public static double maxHpBonus(KiProgression p, CharacterMods m, double baseBonusHealth) {
        return maxHpBonus(p.stat(Stat.VITALITY), m, baseBonusHealth);
    }

    public static double damageTakenMultiplier(KiProgression p, CharacterMods m) {
        return damageTakenMultiplier(p.stat(Stat.DEFENSE), m);
    }

    public static double moveSpeedBonus(KiProgression p, CharacterMods m) {
        return moveSpeedBonus(p.stat(Stat.SPEED), m);
    }

    public static float maxStamina(KiProgression p, CharacterMods m, float endurance) {
        return maxStamina(p.stat(Stat.SPEED), endurance, m);
    }

    /** Max stamina with full endurance (what the HUD bar is sized to). */
    public static float maxStamina(KiProgression p, CharacterMods m) {
        return maxStamina(p.stat(Stat.SPEED), CombatResources.MAX_ENDURANCE, m);
    }

    public static double staminaRegenMult(KiProgression p, CharacterMods m) {
        return staminaRegenMult(p.stat(Stat.SPEED), m);
    }

    // The five stat-less channels, in the same (prog, mods) shape for call-site symmetry.
    public static double kiRegenMult(KiProgression p, CharacterMods m) {
        return kiRegenMult(m);
    }

    public static double kiChargeRateMult(KiProgression p, CharacterMods m) {
        return kiChargeRateMult(m);
    }

    public static double kiCostMult(KiProgression p, CharacterMods m) {
        return kiCostMult(m);
    }

    public static double expGainMult(KiProgression p, CharacterMods m) {
        return expGainMult(m);
    }

    public static double masteryXpMult(KiProgression p, CharacterMods m) {
        return masteryXpMult(m);
    }

    // ---- Player overloads (server or client side) -----------------------------------------------------------------

    /** The player's combined race + nature modifiers: server = CharacterData.mods, client = built from the synced registries. */
    public static CharacterMods mods(Player p) {
        if (p instanceof ServerPlayer sp) return CharacterService.get(sp).mods;
        return ClientPlayerData.mods(p.level().registryAccess());
    }

    private static int points(Player p, Stat s) {
        if (p instanceof ServerPlayer) return p.getData(ModAttachments.KI_PROGRESSION).stat(s);
        return ClientPlayerData.stat(s);
    }

    private static float endurance(Player p) {
        if (p instanceof ServerPlayer) return p.getData(ModAttachments.RESOURCES).endurance;
        return ClientPlayerData.endurance;
    }

    /** Server: the baseBonusHealth config. Client: the value the server synced. */
    private static double baseBonusHealth(Player p) {
        if (p instanceof ServerPlayer) return ServerConfig.BASE_BONUS_HEALTH.get();
        return ClientPlayerData.baseBonusHealth;
    }

    public static float meleeMultiplier(Player p) {
        return (float) (meleeMultiplier(points(p, Stat.STRENGTH), mods(p)) * formDamageMultiplier(p));
    }

    public static float kiDamageMultiplier(Player p) {
        return (float) (kiDamageMultiplier(points(p, Stat.KI_POWER), mods(p)) * formDamageMultiplier(p));
    }

    /**
     * Damage multiplier from the active transformation (Super Saiyan). 1.0 when not transformed.
     * The form is server-side transient state, so this only reads it for {@link ServerPlayer}; clients stay at 1.0.
     */
    public static double formDamageMultiplier(Player p) {
        if (p instanceof ServerPlayer sp
                && com.powerlevelling.combat.Transform.isSuperSaiyan(sp.getData(ModAttachments.COMBAT_STATE))) {
            return 1.0 + ServerConfig.TRANSFORM_DAMAGE_BONUS.get();
        }
        return 1.0;
    }

    public static float maxKi(Player p) {
        return maxKi(points(p, Stat.KI_CAPACITY), mods(p));
    }

    public static float maxHp(Player p) {
        return (float) maxHp(points(p, Stat.VITALITY), mods(p), baseBonusHealth(p));
    }

    /** Multiply incoming damage by this (see the pure overload). */
    public static float damageTakenMultiplier(Player p) {
        return (float) damageTakenMultiplier(points(p, Stat.DEFENSE), mods(p));
    }

    public static float moveSpeedBonus(Player p) {
        return (float) moveSpeedBonus(points(p, Stat.SPEED), mods(p));
    }

    /** Max stamina at the player's current endurance. */
    public static float maxStamina(Player p) {
        return maxStamina(points(p, Stat.SPEED), endurance(p), mods(p));
    }

    public static float staminaRegenMult(Player p) {
        return (float) staminaRegenMult(points(p, Stat.SPEED), mods(p));
    }

    public static float kiRegenMult(Player p) {
        return (float) kiRegenMult(mods(p));
    }

    public static float kiChargeRateMult(Player p) {
        return (float) kiChargeRateMult(mods(p));
    }

    public static float kiCostMult(Player p) {
        return (float) kiCostMult(mods(p));
    }

    public static float expGainMult(Player p) {
        return (float) expGainMult(mods(p));
    }

    public static float masteryXpMult(Player p) {
        return (float) masteryXpMult(mods(p));
    }

    // ---- attribute application -----------------------------------------------------------------------------------

    /**
     * (Re)applies the HP and speed modifiers from stats + race + nature. Call on login, respawn, dimension change,
     * after any stat change and after any character change (CharacterService.onChanged), always AFTER
     * CharacterService.recomputeMods. Heals to full on the very first application (new characters start at full)
     * or when {@code healToFull} is set; otherwise a max-HP increase heals by the gained amount and a decrease
     * clamps current health.
     */
    public static void apply(ServerPlayer player, boolean healToFull) {
        KiProgression p = player.getData(ModAttachments.KI_PROGRESSION);
        CharacterMods m = CharacterService.get(player).mods;

        AttributeInstance hp = player.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            float oldMax = player.getMaxHealth();
            boolean firstApply = !hp.hasModifier(VITALITY_HP_ID);
            double bonus = maxHpBonus(p, m, ServerConfig.BASE_BONUS_HEALTH.get());
            hp.addOrReplacePermanentModifier(new AttributeModifier(VITALITY_HP_ID, bonus, AttributeModifier.Operation.ADD_VALUE));

            float newMax = player.getMaxHealth();
            if (firstApply || healToFull) {
                player.setHealth(newMax);
            } else if (newMax > oldMax) {
                player.heal(newMax - oldMax);
            } else if (player.getHealth() > newMax) {
                player.setHealth(newMax);
            }
        }

        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            double bonus = moveSpeedBonus(p, m);
            if (Math.abs(bonus) > 1.0E-6) { // negative is possible now (nature penalty at low Speed)
                speed.addOrReplacePermanentModifier(
                        new AttributeModifier(SPEED_MOVE_ID, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            } else {
                speed.removeModifier(SPEED_MOVE_ID);
            }
        }

        applyBodyScale(player);
    }

    /**
     * Body size (Addendum B.4 / B.10): cosmetic only by default. With the sizeAffectsHitbox server config on, the character's
     * average of height and build also scales the hitbox through Attributes.SCALE; with it off the modifier is removed again.
     * The client render (PlayerRenderTransforms) subtracts this uniform part so the size is never applied twice.
     */
    private static void applyBodyScale(ServerPlayer player) {
        AttributeInstance scale = player.getAttribute(Attributes.SCALE);
        if (scale == null) return;
        CharacterData data = CharacterService.get(player);
        double bonus = (data.appearance.height() + data.appearance.build()) / 2.0 - 1.0;
        if (ServerConfig.SIZE_AFFECTS_HITBOX.get() && data.created && Math.abs(bonus) > 1.0E-4) {
            scale.addOrReplacePermanentModifier(
                    new AttributeModifier(BODY_SCALE_ID, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        } else {
            scale.removeModifier(BODY_SCALE_ID);
        }
    }
}
