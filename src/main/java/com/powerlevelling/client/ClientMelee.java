package com.powerlevelling.client;

import java.util.HashMap;
import java.util.Map;

import com.powerlevelling.client.fx.BeamRenderer;
import com.powerlevelling.client.pose.KiPoseTable;
import com.powerlevelling.combat.CombatMove;
import com.powerlevelling.combat.CombatMoves;
import com.powerlevelling.combat.MeleeInput;
import com.powerlevelling.combat.MeleeMachine;
import com.powerlevelling.ki.KiAttackParts;
import com.powerlevelling.network.MeleeInputPayload;
import com.powerlevelling.network.MeleeStatePayload;
import com.powerlevelling.network.MeleeSwingPayload;
import com.powerlevelling.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client melee (M5): prediction for the local player + swing poses for everyone.
 * <p>
 * Prediction contract (docs/combat_framework_notes.md): on a press the client runs its OWN {@link MeleeMachine} with the same
 * resolver logic as the server and, if that starts a move, immediately plays the pose, first-person arm swing and swing
 * sound, then sends MeleeInputPayload(seq). The server answers MeleeStatePayload: STARTED (kept, or adopted if the client
 * predicted something else), REJECTED (predicted move dropped, pose blends out over its 3 blend-out ticks), CANCELLED,
 * STUNNED. Hits, damage and hit FX are never predicted.
 */
public final class ClientMelee {
    /** A pose being played for an entity until game time {@code until}; {@code token} restarts the same pose. */
    public record Swing(KiPoseTable.Pose pose, long until, int token) {
    }

    private static final Map<Integer, Swing> SWINGS = new HashMap<>();
    private static final MeleeMachine SELF = new MeleeMachine();
    private static int tokens;
    private static long stunnedUntil = Long.MIN_VALUE / 2;

    private static final MeleeMachine.Listener LISTENER = new MeleeMachine.Listener() {
        @Override
        public void started(int seq) {
            LocalPlayer p = Minecraft.getInstance().player;
            if (p != null) startLocal(p);
        }

        @Override
        public void expired(int seq) {
            // The server sends REJECTED for it too; nothing was predicted for a buffered press.
        }
    };

    private ClientMelee() {
    }

    // ---- local player ------------------------------------------------------------------------------------------------

    /** LMB / RMB pressed in stance. */
    public static void press(LocalPlayer player, MeleeInput input, int clientTick) {
        int seq = ClientCombat.allocSeq();
        if (canPredict(player)) {
            if (SELF.press(input, seq, resolver(player)) == MeleeMachine.Outcome.STARTED) startLocal(player);
        }
        PacketDistributor.sendToServer(new MeleeInputPayload(input.ordinal(), seq, clientTick, player.getYRot(), player.getXRot()));
    }

    private static boolean canPredict(LocalPlayer p) {
        long now = p.level().getGameTime();
        int id = p.getId();
        return now >= stunnedUntil && ClientVisualState.selfStance() && !ClientVisualState.selfCharging()
                && ClientSkillState.heldSlot < 0 && ClientOrbState.get(id) == null && !BeamRenderer.isFiring(id);
    }

    private static MeleeMachine.Resolver resolver(LocalPlayer p) {
        return (input, from) -> {
            CombatMoves.Resolved r = CombatMoves.resolve(p.level().registryAccess(), input, from, p.onGround());
            if (r == null) return null;
            float cost = r.move().staminaCost();
            if (cost > 0 && !p.isCreative() && ClientPlayerData.stamina < cost) return null;
            return r;
        };
    }

    /** Called every client tick (InputHandler, before input polling). */
    public static void tick(LocalPlayer player) {
        if (!SELF.busy() && SELF.buffered == null && SELF.chainGrace == 0) return;
        if (!ClientVisualState.selfStance() || !player.isAlive()) {
            cancelSelf(player);
            return;
        }
        SELF.tick(resolver(player), LISTENER);
    }

    private static void startLocal(LocalPlayer p) {
        CombatMove mv = SELF.move;
        if (mv == null) return;
        put(p.getId(), mv, p.level().getGameTime());
        p.swing(InteractionHand.MAIN_HAND); // first-person arm swing
        SoundEvent s = mv.sounds().swing().map(KiAttackParts.Present::soundOf).orElse(ModSounds.MELEE_SWING.get());
        p.playSound(s, 0.5f, 0.95f + p.getRandom().nextFloat() * 0.15f);
    }

    private static void cancelSelf(LocalPlayer p) {
        SELF.cancel();
        SWINGS.remove(p.getId()); // the pose blends out over its blend-out ticks
    }

    public static boolean stunned(long now) {
        return now < stunnedUntil;
    }

    // ---- server messages -----------------------------------------------------------------------------------------------

    public static void onState(MeleeStatePayload msg) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        long now = p.level().getGameTime();
        switch (msg.result()) {
            case MeleeStatePayload.STARTED -> {
                ResourceLocation id = ResourceLocation.tryParse(msg.move());
                CombatMove mv = CombatMoves.get(p.level().registryAccess(), id);
                if (mv == null || msg.seq() < SELF.seq) return; // stale: we already predicted past it
                if (msg.seq() == SELF.seq && SELF.busy() && id.equals(SELF.moveId)) {
                    SELF.chain = msg.chain(); // prediction confirmed
                    return;
                }
                // The server started something we did not predict (or a different move): adopt its verdict.
                if (SELF.buffered != null && SELF.bufferedSeq == msg.seq()) {
                    SELF.buffered = null;
                    SELF.bufferedSeq = -1;
                }
                SELF.forceStart(id, mv, msg.seq(), msg.chain());
                startLocal(p);
            }
            case MeleeStatePayload.REJECTED -> {
                if (SELF.buffered != null && SELF.bufferedSeq == msg.seq()) {
                    SELF.buffered = null;
                    SELF.bufferedSeq = -1;
                }
                if (SELF.busy() && SELF.seq == msg.seq()) cancelSelf(p);
            }
            case MeleeStatePayload.CANCELLED -> cancelSelf(p);
            case MeleeStatePayload.STUNNED -> {
                stunnedUntil = now + msg.stunTicks();
                if (msg.stunTicks() > 0) cancelSelf(p);
            }
            default -> {
                // BUFFERED: nothing to do, our own buffer mirrors it.
            }
        }
    }

    /** Another player's move started / was cut short. */
    public static void onSwing(MeleeSwingPayload msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || (mc.player != null && msg.entityId() == mc.player.getId())) return;
        if (msg.cancel()) {
            SWINGS.remove(msg.entityId());
            return;
        }
        CombatMove mv = CombatMoves.get(mc.level.registryAccess(), ResourceLocation.tryParse(msg.move()));
        if (mv != null) put(msg.entityId(), mv, mc.level.getGameTime());
    }

    // ---- poses (read by KiPoseClient) ------------------------------------------------------------------------------------

    private static void put(int entityId, CombatMove mv, long now) {
        KiPoseTable.Pose pose = KiPoseTable.get(mv.pose());
        if (pose == null) {
            SWINGS.remove(entityId);
            return;
        }
        SWINGS.put(entityId, new Swing(pose, now + Math.max(1, mv.totalTicks()), ++tokens));
    }

    /** The melee pose an entity is playing now, or null. */
    public static Swing swing(int entityId, long now) {
        Swing s = SWINGS.get(entityId);
        if (s == null) return null;
        if (now >= s.until()) {
            SWINGS.remove(entityId);
            return null;
        }
        return s;
    }

    public static void forget(int entityId) {
        SWINGS.remove(entityId);
    }

    public static void reset() {
        SWINGS.clear();
        SELF.cancel();
        stunnedUntil = Long.MIN_VALUE / 2;
    }
}
