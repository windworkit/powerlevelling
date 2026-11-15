package com.powerlevelling.combat;

import com.powerlevelling.config.ServerConfig;
import com.powerlevelling.data.CombatResources;
import com.powerlevelling.data.CombatState;
import com.powerlevelling.registry.ModAttachments;
import com.powerlevelling.registry.ModSounds;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Dash / dodge (design 4.3): a short stamina-priced burst in the movement direction (or straight ahead when no
 * movement key is held), with a few i-frames and a cooldown.
 *
 * <p>Server-authoritative: the client reports only the world yaw of its movement input ({@code moveYaw}) with the
 * input edge; the server builds the unit direction itself, so a crafted packet can change where the dash goes but
 * never its speed, stamina cost, i-frames or cooldown.
 */
public final class Dash {
    private Dash() {
    }

    /**
     * Tries to dash. {@code moveYaw} is the world yaw of the player's movement input (or look yaw when standing still).
     *
     * @return true if the input was accepted (it still fails silently while cooling down or busy)
     */
    public static boolean tryDash(ServerPlayer player, CombatState st, float moveYaw) {
        long now = player.level().getGameTime();
        if (now < st.dashCooldownUntil) return false;
        if (!player.isAlive() || player.isSpectator()) return false;
        if (player.isPassenger() || player.isFallFlying()) return false;
        if (st.charging || st.transforming || player.isUsingItem()) return false;

        // A dash cancels a melee move, but not a skill mid-cast: WINDUP / CHARGE / ACTIVE are locked (RECOVERY is not,
        // so a dodge after firing still works).
        CombatState.SkillPhase phase = st.skillPhase;
        if (phase == CombatState.SkillPhase.WINDUP || phase == CombatState.SkillPhase.CHARGE
                || phase == CombatState.SkillPhase.ACTIVE) {
            return false;
        }
        if (st.inHitstun(now)) return false;

        float cost = ServerConfig.DASH_STAMINA_COST.get().floatValue();
        CombatResources r = player.getData(ModAttachments.RESOURCES);
        if (r.stamina < cost) {
            player.displayClientMessage(Component.translatable("message.powerlevelling.no_stamina")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        Vec3 dir = Vec3.directionFromRotation(0f, moveYaw);
        double impulse = ServerConfig.DASH_IMPULSE.get();
        Vec3 motion = player.getDeltaMovement();

        MeleeEngine.cancelForAction(player, st); // dodging drops the current attack
        player.setDeltaMovement(dir.x * impulse, motion.y, dir.z * impulse);
        player.hasImpulse = true;
        player.hurtMarked = true; // makes ServerEntity send the new velocity to the client and trackers

        r.stamina = Math.max(0f, r.stamina - cost);
        st.lastStaminaSpendTick = now;
        st.dashCooldownUntil = now + ServerConfig.DASH_COOLDOWN_TICKS.get();
        int iframes = ServerConfig.DASH_IFRAME_TICKS.get();
        if (iframes > 0) st.iframesUntil = Math.max(st.iframesUntil, now + iframes);

        player.level().playSound(null, player.blockPosition(), ModSounds.DASH.get(), SoundSource.PLAYERS, 0.8f, 1.0f);
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2, player.getZ(),
                    8, 0.25, 0.15, 0.25, 0.02);
        }
        return true;
    }
}
