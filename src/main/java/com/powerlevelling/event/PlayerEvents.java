package com.powerlevelling.event;

import com.powerlevelling.PowerLevelling;
import com.powerlevelling.character.CharacterMods;
import com.powerlevelling.character.CharacterService;
import com.powerlevelling.combat.Guard;
import com.powerlevelling.combat.KiCharge;
import com.powerlevelling.combat.LockOn;
import com.powerlevelling.combat.Transform;
import com.powerlevelling.data.CombatResources;
import com.powerlevelling.data.CombatState;
import com.powerlevelling.data.KiProgression;
import com.powerlevelling.ki.KiSkills;
import com.powerlevelling.network.ModNetwork;
import com.powerlevelling.progression.Advancements;
import com.powerlevelling.progression.Progression;
import com.powerlevelling.progression.StatEffects;
import com.powerlevelling.config.ServerConfig;
import com.powerlevelling.progression.Zenkai;
import com.powerlevelling.registry.ModAttachments;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Server-side sync triggers, combat tracking and passive regen. */
@EventBusSubscriber(modid = PowerLevelling.MODID)
public final class PlayerEvents {
    private static final int COMBAT_TIMEOUT_TICKS = 100;
    private static final int STAMINA_REGEN_DELAY_TICKS = 15;
    private static final int MIN_TICKS_BETWEEN_SYNCS = 2;
    private static final float KI_REGEN_OUT_OF_COMBAT = 0.02f; // fraction of max per second
    private static final float KI_REGEN_IN_COMBAT = 0.005f;
    private static final float STAMINA_REGEN_PER_SECOND = 30f;

    private PlayerEvents() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CharacterService.recomputeMods(player); // race + nature -> CharacterMods (before anything reads them)
        Transform.clearStaleModifiers(player); // a crash during Super Saiyan must not keep the speed bonus
        StatEffects.apply(player, false); // heals to full only the first time (new characters start at 100/100); after recompute
        backfillAdvancements(player); // levels/mastery earned before an advancement existed still get their toast
        Progression.clampResources(player); // saved ki/stamina may exceed a max that a datapack or nature change lowered
        KiCharge.resetAll(player); // strips a charge slowdown that may have been saved with the player
        ModNetwork.syncAll(player);
        ModNetwork.syncVisual(player, true);
        ModNetwork.syncCharacter(player);
        CharacterService.scheduleJoinPrompt(player);
    }

    /** Grants advancements the player already qualifies for (old saves, or levels earned before the criteria existed). */
    private static void backfillAdvancements(ServerPlayer player) {
        KiProgression prog = player.getData(ModAttachments.KI_PROGRESSION);
        if (CharacterService.get(player).created) {
            Advancements.characterCreated(player);
        }
        Advancements.levelReached(player, prog.level);
        for (KiProgression.Track track : KiProgression.Track.values()) {
            Advancements.masteryReached(player, prog.level(track));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            KiCharge.resetAll(player);
            CombatState st = player.getData(ModAttachments.COMBAT_STATE);
            if (Transform.isSuperSaiyan(st)) {
                Transform.revert(player);
            }
        }
    }

    /** Death ends stance and charge (the respawned player starts clean; observers are told here). */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            KiCharge.resetAll(player);
            CombatState st = player.getData(ModAttachments.COMBAT_STATE);
            st.kiKillStreak = 0;
            if (Transform.isSuperSaiyan(st)) {
                Transform.revert(player);
            }
            ModNetwork.syncVisual(player);
        }
    }

    /** A player we can now see: tell them our current charge/stance state. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer observer && event.getTarget() instanceof ServerPlayer target) {
            ModNetwork.sendVisualTo(observer, target);
            ModNetwork.sendOrbTo(observer, target); // a forming ki orb they would otherwise never see
            ModNetwork.sendCharacterTo(observer, target); // their race / look / tail
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CharacterService.recomputeMods(player); // CharacterData is copied on death but its transient mods are not
        // The new player entity has lost our attribute modifiers: re-apply and start at full health.
        StatEffects.apply(player, true);
        KiCharge.resetAll(player);
        if (!event.isEndConquered()) {
            // RESOURCES is not copied on death, so refill using the (copied) progression + freshly recomputed mods.
            CombatResources r = player.getData(ModAttachments.RESOURCES);
            r.ki = StatEffects.maxKi(player);
            r.stamina = StatEffects.maxStamina(player);
            r.kiBreak = false;
        }
        ModNetwork.syncAll(player);
        ModNetwork.syncVisual(player, true);
        ModNetwork.syncCharacter(player);
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        StatEffects.apply(player, false);
        KiCharge.resetAll(player); // auto-exit stance, stop charging
        CombatState st = player.getData(ModAttachments.COMBAT_STATE);
        if (Transform.isSuperSaiyan(st)) {
            Transform.revert(player);
        }
        ModNetwork.syncAll(player);
        ModNetwork.syncVisual(player, true);
        ModNetwork.syncCharacter(player);
    }

    /** Any damage dealt or taken by a player refreshes its "in combat" timer. */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer victim) {
            markCombat(victim);
            victim.getData(ModAttachments.COMBAT_STATE).lastHurtTick = victim.level().getGameTime(); // info only; real hitstun = MeleeEngine
            KiCharge.interrupt(victim); // a hit ends the charge and locks re-charging for 10 ticks
            Zenkai.onPlayerDamaged(victim, event.getSource()); // low-HP window + attacker tracking
        }
        if (event.getSource().getEntity() instanceof ServerPlayer attacker) markCombat(attacker);
    }

    private static void markCombat(ServerPlayer player) {
        player.getData(ModAttachments.COMBAT_STATE).lastCombatTick = player.level().getGameTime();
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        // PlayerTickEvent fires on both sides; only the server owns resources.
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        long now = player.level().getGameTime();
        CombatResources r = player.getData(ModAttachments.RESOURCES);
        CombatState state = player.getData(ModAttachments.COMBAT_STATE);
        KiProgression prog = player.getData(ModAttachments.KI_PROGRESSION);
        CharacterMods mods = CharacterService.get(player).mods;
        float maxKi = StatEffects.maxKi(prog, mods);

        CharacterService.tickJoinPrompt(player, state, now); // one-shot, ~40 ticks after login

        // Ki charging (hold C) replaces passive regen while it runs.
        KiCharge.tick(player, state, r, prog, now);
        KiSkills.tick(player, state, r); // held skill charge: per-tick ki cost, auto-fire when empty
        com.powerlevelling.combat.MeleeEngine.tick(player, state, now); // M5 melee state machine + hit detection
        LockOn.tickLock(player, state, now);
        // Guard tick: stamina drain, perfect window, guard break
        Guard.tickGuard(player, state, r, now);

        // Transform hold timer (hold G to charge, then transform)
        Transform.tickTransform(player);

        // Ki: passive regen, slower while in combat.
        if (!state.charging) {
            boolean inCombat = now - state.lastCombatTick < COMBAT_TIMEOUT_TICKS;
            float kiRate = (inCombat ? KI_REGEN_IN_COMBAT : KI_REGEN_OUT_OF_COMBAT) * (float) StatEffects.kiRegenMult(mods);
            r.ki = Mth.clamp(r.ki + maxKi * kiRate / 20f, 0f, maxKi);
        }

        // SSJ ki drain
        Transform.tickKiDrain(player);

        // Turbo ki drain
        if (state.turbo) {
            float drain = ServerConfig.TURBO_KI_DRAIN.get().floatValue();
            r.ki = Math.max(0f, r.ki - drain);
        }

        // Stamina: regen once nothing has been spent for a while (nothing spends it yet).
        float maxStamina = StatEffects.maxStamina(prog, mods, r.endurance); // Speed stat + race/nature
        if (now - state.lastStaminaSpendTick >= STAMINA_REGEN_DELAY_TICKS) {
            r.stamina += STAMINA_REGEN_PER_SECOND * (float) StatEffects.staminaRegenMult(prog, mods) / 20f;
        }
        r.stamina = Mth.clamp(r.stamina, 0f, maxStamina);

        // Dirty check: send only when something changed, at most every 2 ticks.
        if (now - state.lastSendTick >= MIN_TICKS_BETWEEN_SYNCS && state.isDirty(r, maxKi, maxStamina)) {
            ModNetwork.syncResources(player);
        }

        // Cheap int compare; only sends when stance / charge / ki break changed.
        ModNetwork.syncVisual(player);
    }
}
