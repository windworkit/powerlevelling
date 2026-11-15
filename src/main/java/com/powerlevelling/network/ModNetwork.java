package com.powerlevelling.network;

import com.powerlevelling.PowerLevelling;
import com.powerlevelling.character.CharacterData;
import com.powerlevelling.character.CharacterService;
import com.powerlevelling.character.CharacterSnapshot;
import com.powerlevelling.client.ClientCharacters;
import com.powerlevelling.client.ClientScreens;
import com.powerlevelling.client.ClientCombat;
import com.powerlevelling.client.ClientCooldowns;
import com.powerlevelling.client.ClientOrbState;
import com.powerlevelling.client.ClientPlayerData;
import com.powerlevelling.client.ClientVisualState;
import com.powerlevelling.client.fx.KiFx;
import com.powerlevelling.client.fx.KiWorldFx;
import com.powerlevelling.combat.CombatInputHandler;
import com.powerlevelling.data.CombatResources;
import com.powerlevelling.data.CombatState;
import com.powerlevelling.config.ServerConfig;
import com.powerlevelling.data.KiProgression;
import com.powerlevelling.progression.Progression;
import com.powerlevelling.progression.StatEffects;
import com.powerlevelling.registry.ModAttachments;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Payload registration (mod bus) plus server-side send helpers. */
@EventBusSubscriber(modid = PowerLevelling.MODID)
public final class ModNetwork {
    private ModNetwork() {
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        // ClientPlayerData is a plain data holder (no client-only imports), so these lambdas are safe on a dedicated server.
        registrar.playToClient(ResourceSyncPayload.TYPE, ResourceSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPlayerData.apply(payload)));
        registrar.playToClient(ProgressionSyncPayload.TYPE, ProgressionSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPlayerData.apply(payload)));

        // ClientVisualState / ClientCombat are plain data holders too (no client-only imports).
        registrar.playToClient(VisualStatePayload.TYPE, VisualStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientVisualState.apply(payload)));
        // Forming ki orb of a player (plain data holder again).
        registrar.playToClient(OrbStatePayload.TYPE, OrbStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientOrbState.apply(payload)));
        // Character (C4): ClientCharacters is a plain data holder; ClientScreens is a client class (lambda only runs on a client).
        registrar.playToClient(CharacterSyncPayload.TYPE, CharacterSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientCharacters.apply(payload, context.player().getId())));
        registrar.playToClient(CharacterResultPayload.TYPE, CharacterResultPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientCharacters.onResult(payload)));
        registrar.playToClient(OpenCharacterScreenPayload.TYPE, OpenCharacterScreenPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientScreens.openCharacter()));
        registrar.playToClient(ActionAckPayload.TYPE, ActionAckPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientCombat.onAck(payload)));

        // Skill cooldowns (plain data holder) and impact FX (client class; the lambda only runs on a client).
        registrar.playToClient(CooldownSyncPayload.TYPE, CooldownSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientCooldowns.apply(payload)));
        registrar.playToClient(KiImpactPayload.TYPE, KiImpactPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> KiFx.impact(payload)));
        // Hitscan lines (needle): client class again, lambda only runs on a client.
        registrar.playToClient(KiTracePayload.TYPE, KiTracePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> KiWorldFx.trace(payload)));

        // Beams (K7): client class again, lambda only runs on a client.
        registrar.playToClient(BeamPayload.TYPE, BeamPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.powerlevelling.client.fx.BeamRenderer.apply(payload)));

        // Melee (M5): verdicts to the attacker / victim, swing poses to observers (ClientMelee: client class, lambda only runs on a client).
        registrar.playToClient(MeleeStatePayload.TYPE, MeleeStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.powerlevelling.client.ClientMelee.onState(payload)));
        registrar.playToClient(MeleeSwingPayload.TYPE, MeleeSwingPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.powerlevelling.client.ClientMelee.onSwing(payload)));

        // C2S: the server validates everything; handlers run on the main thread via enqueueWork.
        registrar.playToServer(MeleeInputPayload.TYPE, MeleeInputPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) com.powerlevelling.combat.MeleeEngine.handleInput(sp, payload);
                }));
        registrar.playToServer(SpendStatPayload.TYPE, SpendStatPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) Progression.handleSpend(sp, payload.statId(), payload.amount());
                }));
        registrar.playToServer(CombatInputPayload.TYPE, CombatInputPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) CombatInputHandler.handle(sp, payload);
                }));
        registrar.playToServer(CharacterSubmitPayload.TYPE, CharacterSubmitPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) CharacterService.handleSubmit(sp, payload.mode(), payload.choice());
                }));
        registrar.playToServer(TailTogglePayload.TYPE, TailTogglePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) CharacterService.handleTailToggle(sp);
                }));
        registrar.playToServer(RespecConfirmPayload.TYPE, RespecConfirmPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) Progression.handleRespec(sp);
                }));
    }

    public static void syncAll(ServerPlayer player) {
        syncResources(player);
        syncProgression(player);
    }

    public static void syncResources(ServerPlayer player) {
        CombatResources r = player.getData(ModAttachments.RESOURCES);
        KiProgression p = player.getData(ModAttachments.KI_PROGRESSION);
        float maxKi = StatEffects.maxKi(p, StatEffects.mods(player));
        float maxStamina = StatEffects.maxStamina(player);
        PacketDistributor.sendToPlayer(player,
                new ResourceSyncPayload(r.ki, maxKi, r.stamina, maxStamina, r.endurance, r.kiBreak));
        // Remember what we sent so the tick handler only resends on change.
        CombatState state = player.getData(ModAttachments.COMBAT_STATE);
        state.markSent(r, maxKi, maxStamina, player.level().getGameTime());
    }

    /** Flags describing how this player looks to others (charge aura, stance, ki break). */
    public static int visualFlags(ServerPlayer player) {
        CombatState st = player.getData(ModAttachments.COMBAT_STATE);
        CombatResources r = player.getData(ModAttachments.RESOURCES);
        int flags = 0;
        if (st.charging) flags |= VisualStatePayload.CHARGING;
        if (st.stance) flags |= VisualStatePayload.STANCE;
        if (r.kiBreak) flags |= VisualStatePayload.KI_BREAK;
        if (st.form != null && !st.form.equals(ResourceLocation.tryParse("powerlevelling:base"))) flags |= VisualStatePayload.SUPER_SAIYAN;
        if (st.transforming) flags |= VisualStatePayload.TRANSFORMING;
        if (st.turbo) flags |= VisualStatePayload.TURBO;
        if (st.isGuarding) flags |= VisualStatePayload.GUARDING;
        if (st.perfectWindowActive) flags |= VisualStatePayload.PERFECT_GUARD;
        if (st.lockHeld && st.lockTargetId > 0) flags |= VisualStatePayload.LOCKED;
        return flags;
    }

    /** Broadcasts the player's visual state to everyone tracking them (and themselves) if it changed. */
    public static void syncVisual(ServerPlayer player) {
        syncVisual(player, false);
    }

    public static void syncVisual(ServerPlayer player, boolean force) {
        CombatState st = player.getData(ModAttachments.COMBAT_STATE);
        int flags = visualFlags(player);
        if (!force && flags == st.sentVisualFlags) return;
        st.sentVisualFlags = flags;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new VisualStatePayload(player.getId(), flags,
                        st.transforming ? (float) st.transformTicks : st.chargeTicks));
    }

    /** Tells one observer about a player they just started tracking (always sent, so stale client state is overwritten). */
    public static void sendVisualTo(ServerPlayer observer, ServerPlayer target) {
        CombatState st = target.getData(ModAttachments.COMBAT_STATE);
        PacketDistributor.sendToPlayer(observer,
                new VisualStatePayload(target.getId(), visualFlags(target),
                        st.transforming ? (float) st.transformTicks : st.chargeTicks));
    }

    /** Broadcasts a player's forming-orb state (start or stop) to the player and everyone tracking them. */
    public static void sendOrb(ServerPlayer player, OrbStatePayload payload) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, payload);
    }

    /** Tells one observer about the orb of a player they just started tracking (nothing if there is none). */
    public static void sendOrbTo(ServerPlayer observer, ServerPlayer target) {
        OrbStatePayload orb = target.getData(ModAttachments.COMBAT_STATE).orb;
        if (orb != null) PacketDistributor.sendToPlayer(observer, orb);
    }

    /** Broadcasts the player's character (race, nature, look, tail) to themselves and everyone tracking them. */
    public static void syncCharacter(ServerPlayer player) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new CharacterSyncPayload(player.getId(), CharacterSnapshot.of(CharacterService.get(player))));
    }

    /** Tells one observer about the character of a player they just started tracking. */
    public static void sendCharacterTo(ServerPlayer observer, ServerPlayer target) {
        PacketDistributor.sendToPlayer(observer,
                new CharacterSyncPayload(target.getId(), CharacterSnapshot.of(CharacterService.get(target))));
    }

    public static void syncProgression(ServerPlayer player) {
        KiProgression p = player.getData(ModAttachments.KI_PROGRESSION);
        long remaining = Math.max(0L, p.zenkaiCooldownUntil - player.level().getGameTime());
        CharacterData cd = CharacterService.get(player);
        PacketDistributor.sendToPlayer(player, new ProgressionSyncPayload(p.race, p.form, p.levelArray(), p.xpArray(),
                p.level, p.exp, KiProgression.expToNext(p.level), p.unspentPoints, p.statArray(),
                (int) Math.min(remaining, Integer.MAX_VALUE),
                ServerConfig.LEVEL_CAP.get(), ServerConfig.STAT_CAP.get(), p.loadoutIds(),
                cd.race.toString(), cd.nature.toString(), cd.created, ServerConfig.BASE_BONUS_HEALTH.get().floatValue(),
                p.attackUsesCopy()));
    }

    /** Tells the owner a skill slot's cooldown (see CooldownSyncPayload). */
    public static void sendCooldown(ServerPlayer player, int slot, int remaining, int total) {
        PacketDistributor.sendToPlayer(player, new CooldownSyncPayload(slot, remaining, total));
    }

    /** Broadcasts a ki impact to everyone tracking the (still alive) projectile that caused it. */
    public static void sendImpact(Entity source, Vec3 at, ResourceLocation attack, float size, boolean explosion, int kiColor) {
        sendImpact(source, at, attack, size, explosion, kiColor, KiImpactPayload.STYLE_DEFAULT);
    }

    /** Same, with a KiImpactPayload.STYLE_* (ground ring, burst ring + dome, pierce sparks). */
    public static void sendImpact(Entity source, Vec3 at, ResourceLocation attack, float size, boolean explosion, int kiColor,
                                  int style) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(source,
                new KiImpactPayload((float) at.x, (float) at.y, (float) at.z, attack, size, explosion, kiColor, style));
    }

    /** Broadcasts a hitscan line (needle aim / shot) to the shooter and everyone tracking them. */
    public static void sendTrace(ServerPlayer shooter, KiTracePayload payload) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(shooter, payload);
    }

    /** Broadcasts a beam start / update / stop to the owner and everyone tracking them. */
    public static void sendBeam(ServerPlayer owner, BeamPayload payload) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(owner, payload);
    }
}
