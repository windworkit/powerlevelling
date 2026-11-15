package com.powerlevelling.progression;

import com.powerlevelling.character.CharacterRegistries;
import com.powerlevelling.character.CharacterService;
import com.powerlevelling.character.Race;
import com.powerlevelling.config.ServerConfig;
import com.powerlevelling.data.CombatState;
import com.powerlevelling.data.KiProgression;
import com.powerlevelling.network.ModNetwork;
import com.powerlevelling.registry.ModAttachments;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Zenkai boost (Addendum A.2): near-death window, then a kill on one of the attackers pays a bonus. */
public final class Zenkai {
    public static final int WINDOW_TICKS = 30 * 20;
    public static final float HP_FRACTION = 0.20f;
    public static final int BONUS_POINTS = 2;
    public static final double BONUS_EXP_FRACTION = 0.25;

    private Zenkai() {
    }

    /** The race's zenkai perks (bonus_points, cooldown_mult, exp_bonus_mult); neutral when the race is missing. */
    public static Race.Zenkai perks(ServerPlayer player) {
        Race race = CharacterRegistries.getRace(player.level().registryAccess(), CharacterService.get(player).race);
        return race == null ? Race.Zenkai.DEFAULT : race.zenkai();
    }

    /** Call after a player took damage (LivingDamageEvent.Post): opens the window and records the attacker. */
    public static void onPlayerDamaged(ServerPlayer victim, DamageSource source) {
        if (!victim.isAlive()) return;
        CombatState cs = victim.getData(ModAttachments.COMBAT_STATE);
        long now = victim.level().getGameTime();

        if (victim.getHealth() < HP_FRACTION * victim.getMaxHealth()) {
            if (!cs.zenkaiWindowOpen(now)) cs.zenkaiAttackers.clear(); // fresh window
            cs.zenkaiWindowEnd = now + WINDOW_TICKS;                   // being hit while low keeps it open
        }

        Entity attacker = source.getEntity();
        if (attacker != null && attacker != victim && cs.zenkaiWindowOpen(now)) {
            cs.zenkaiAttackers.add(attacker.getUUID());
        }
    }

    /** Call when {@code killer} killed {@code victim}. Triggers ZENKAI if every condition holds. */
    public static void tryTrigger(ServerPlayer killer, LivingEntity victim, MobClass victimClass) {
        if (victimClass == MobClass.PASSIVE || !killer.isAlive()) return;

        CombatState cs = killer.getData(ModAttachments.COMBAT_STATE);
        KiProgression p = killer.getData(ModAttachments.KI_PROGRESSION);
        long now = killer.level().getGameTime();
        if (!cs.zenkaiWindowOpen(now) || !cs.zenkaiAttackers.contains(victim.getUUID())) return;
        if (now < p.zenkaiCooldownUntil) return;

        Race.Zenkai perks = perks(killer);

        // Consume the window and start the cooldown (race cooldown_mult scales the config value).
        double cooldownSeconds = ServerConfig.ZENKAI_COOLDOWN_SECONDS.get() * Math.max(0.0, perks.cooldownMult());
        p.zenkaiCooldownUntil = now + Math.round(cooldownSeconds * 20.0);
        cs.zenkaiWindowEnd = -1;
        cs.zenkaiAttackers.clear();

        int points = Math.max(0, BONUS_POINTS + perks.bonusPoints());
        p.unspentPoints += points;
        int baseExp = (int) Math.max(1, Math.round(KiProgression.expToNext(p.level) * BONUS_EXP_FRACTION * Math.max(0.0, perks.expBonusMult())));
        int granted = Progression.addExp(killer, baseExp, Progression.ExpSource.ZENKAI); // also gets exp_gain
        int shownExp = granted > 0 ? granted : baseExp; // at the level cap nothing is granted; show the nominal bonus

        // Feedback last so the ZENKAI! title replaces any level-up title.
        Progression.showTitle(killer,
                Component.translatable("title.powerlevelling.zenkai").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                Component.translatable("title.powerlevelling.zenkai.sub", points, shownExp).withStyle(ChatFormatting.YELLOW));
        Progression.playSound(killer, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        ((ServerLevel) killer.level()).sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                killer.getX(), killer.getY() + 1.0, killer.getZ(), 60, 0.5, 0.8, 0.5, 0.4);

        Advancements.zenkai(killer);
        ModNetwork.syncProgression(killer); // addExp may have returned early at level cap; make sure points/cooldown sync
    }
}
