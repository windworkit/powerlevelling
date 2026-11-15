package com.powerlevelling.progression;

import com.powerlevelling.character.CharacterGate;
import com.powerlevelling.config.ServerConfig;
import com.powerlevelling.data.CombatResources;
import com.powerlevelling.data.KiProgression;
import com.powerlevelling.data.KiProgression.MasteryTrack;
import com.powerlevelling.data.KiProgression.Stat;
import com.powerlevelling.data.KiProgression.Track;
import com.powerlevelling.network.ModNetwork;
import com.powerlevelling.registry.ModAttachments;
import com.powerlevelling.registry.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/** Server-side progression service: EXP, level-ups, mastery XP, stat spending and respec. */
public final class Progression {
    /**
     * Where EXP came from. {@code scaled} sources are multiplied by the expRate config; {@code modded} sources are
     * multiplied by the exp_gain race/nature channel (everything except the exact {@code /addexp} command).
     */
    public enum ExpSource {
        KILL(true, true),
        PVP_KILL(true, true),
        TRAINING(true, true),
        ZENKAI(false, true),
        COMMAND(false, false);

        public final boolean scaled;
        public final boolean modded;

        ExpSource(boolean scaled, boolean modded) {
            this.scaled = scaled;
            this.modded = modded;
        }
    }

    private Progression() {
    }

    /** Rounds {@code value} to an int without losing small bonuses: 1.4 gives 1 (60%) or 2 (40%). */
    private static int roundStochastic(ServerPlayer player, double value) {
        int floor = (int) Math.floor(value);
        double frac = value - floor;
        return frac > 0.0 && player.getRandom().nextDouble() < frac ? floor + 1 : floor;
    }

    // ---- Battle EXP ----------------------------------------------------------------------------------------------

    /**
     * Adds Battle EXP, handling several level-ups at once (+3 points each), feedback and sync.
     * Order: expRate config (scaled sources) then the character's exp_gain multiplier (modded sources).
     * Returns the EXP actually granted (0 when nothing was added, e.g. at the level cap).
     */
    public static int addExp(ServerPlayer player, int amount, ExpSource source) {
        KiProgression p = player.getData(ModAttachments.KI_PROGRESSION);
        int cap = ServerConfig.LEVEL_CAP.get();
        if (amount <= 0 || p.level >= cap) return 0;

        if (source.scaled) amount = (int) Math.round(amount * ServerConfig.EXP_RATE.get());
        if (source.modded) {
            double mult = StatEffects.expGainMult(StatEffects.mods(player));
            if (mult != 1.0) amount = roundStochastic(player, amount * mult);
        }
        if (amount <= 0) return 0;

        long exp = (long) p.exp + amount;
        int gained = 0;
        while (p.level < cap) {
            int need = KiProgression.expToNext(p.level);
            if (exp < need) break;
            exp -= need;
            p.level++;
            gained++;
        }
        if (p.level >= cap) exp = 0;
        p.exp = (int) Math.min(exp, Integer.MAX_VALUE);
        p.unspentPoints += gained * KiProgression.POINTS_PER_LEVEL;

        if (gained > 0) {
            levelUpFeedback(player, p, gained);
        } else {
            player.displayClientMessage(Component.translatable("message.powerlevelling.exp_gain", amount)
                    .withStyle(ChatFormatting.YELLOW), true); // action bar
        }
        Advancements.levelReached(player, p.level);
        ModNetwork.syncProgression(player);
        return amount;
    }

    private static void levelUpFeedback(ServerPlayer player, KiProgression p, int gained) {
        int points = gained * KiProgression.POINTS_PER_LEVEL;
        player.sendSystemMessage(Component.translatable("message.powerlevelling.level_up", p.level, points)
                .withStyle(ChatFormatting.GOLD));
        showTitle(player,
                Component.translatable("title.powerlevelling.level_up", p.level).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                Component.translatable("title.powerlevelling.level_up.sub", points).withStyle(ChatFormatting.YELLOW));
        playSound(player, SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);
    }

    // ---- Mastery -------------------------------------------------------------------------------------------------

    /** Adds mastery XP (scaled by progressionRate). Each time a track crosses a multiple of 5 it pays +1 stat point. */
    public static void addMasteryXp(ServerPlayer player, Track track, int xp) {
        KiProgression p = player.getData(ModAttachments.KI_PROGRESSION);
        MasteryTrack t = p.track(track);
        Advancements.masteryReached(player, t.level); // backfills a track that was already at a milestone
        double raw = xp * ServerConfig.PROGRESSION_RATE.get();
        double masteryMult = StatEffects.masteryXpMult(StatEffects.mods(player));
        // Neutral channel keeps the old rounding exactly; a real multiplier rounds stochastically so 1-XP ticks still benefit.
        int scaled = masteryMult == 1.0 ? (int) Math.round(raw) : roundStochastic(player, raw * masteryMult);
        if (scaled <= 0 || t.level >= KiProgression.MAX_LEVEL) return;

        long total = (long) t.xp + scaled;
        while (t.level < KiProgression.MAX_LEVEL) {
            int need = KiProgression.masteryXpToNext(t.level);
            if (total < need) break;
            total -= need;
            t.level++;
        }
        t.xp = t.level >= KiProgression.MAX_LEVEL ? 0 : (int) total;

        // Pay a point for every unpaid multiple of 5 (bookkeeping lives on the track).
        int due = t.level / KiProgression.MASTERY_MILESTONE_EVERY - t.milestones;
        if (due > 0) {
            t.milestones += due;
            int points = due * KiProgression.POINTS_PER_MASTERY_MILESTONE;
            p.unspentPoints += points;
            player.sendSystemMessage(Component.translatable("message.powerlevelling.mastery_milestone",
                    Component.translatable("track.powerlevelling." + track.id), t.level, points).withStyle(ChatFormatting.AQUA));
            playSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        }
        Advancements.masteryReached(player, t.level);
        ModNetwork.syncProgression(player);
    }

    // ---- Stats ---------------------------------------------------------------------------------------------------

    /** Server-authoritative stat spend (from SpendStatPayload). Silently ignores invalid requests. */
    public static void handleSpend(ServerPlayer player, String statId, int amount) {
        if (!CharacterGate.check(player)) return; // Addendum B.7: stat points can be earned before creation but not spent
        Stat stat = Stat.byId(statId);
        KiProgression p = player.getData(ModAttachments.KI_PROGRESSION);
        int cap = ServerConfig.STAT_CAP.get();
        if (stat == null || amount <= 0 || amount > cap) return;
        if (p.unspentPoints < amount) return;
        if (p.stat(stat) + amount > cap) return;

        p.setStat(stat, p.stat(stat) + amount);
        p.unspentPoints -= amount;
        onStatsChanged(player);
    }

    /**
     * Re-applies attributes, clamps resources to their new maximums and syncs everything. Used after stat changes
     * and (via CharacterService.onChanged) after race/nature changes.
     */
    public static void onStatsChanged(ServerPlayer player) {
        StatEffects.apply(player, false);
        clampResources(player);
        ModNetwork.syncAll(player);
    }

    /** Ki and stamina never exceed their current maximums (call after anything that can lower them). */
    public static void clampResources(ServerPlayer player) {
        CombatResources r = player.getData(ModAttachments.RESOURCES);
        r.ki = Math.min(r.ki, StatEffects.maxKi(player));
        r.stamina = Math.min(r.stamina, StatEffects.maxStamina(player));
    }

    // ---- Respec (Rebirth Orb) ------------------------------------------------------------------------------------

    /** Handles RespecConfirmPayload: needs an orb in either hand; refunds all spent points; consumes one orb. */
    public static void handleRespec(ServerPlayer player) {
        ItemStack orb = player.getMainHandItem();
        if (!orb.is(ModItems.REBIRTH_ORB.get())) orb = player.getOffhandItem();
        if (!orb.is(ModItems.REBIRTH_ORB.get())) {
            player.displayClientMessage(Component.translatable("message.powerlevelling.respec.no_orb").withStyle(ChatFormatting.RED), true);
            return;
        }

        KiProgression p = player.getData(ModAttachments.KI_PROGRESSION);
        int spent = p.spentPoints();
        if (spent <= 0) {
            player.displayClientMessage(Component.translatable("message.powerlevelling.respec.nothing").withStyle(ChatFormatting.YELLOW), true);
            return; // don't waste the orb
        }

        p.unspentPoints += spent;
        p.clearStats();
        if (!player.getAbilities().instabuild) orb.shrink(1);

        onStatsChanged(player);
        player.sendSystemMessage(Component.translatable("message.powerlevelling.respec.done", spent).withStyle(ChatFormatting.GOLD));
        playSound(player, SoundEvents.BEACON_ACTIVATE, 1.0f, 1.2f);
    }

    // ---- Feedback helpers (also used by Zenkai) ------------------------------------------------------------------

    public static void showTitle(ServerPlayer player, Component title, Component subtitle) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 15));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    public static void playSound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.level().playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, volume, pitch);
    }
}
