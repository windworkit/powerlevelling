package com.powerlevelling.client.fx;

import java.util.Map;

import com.powerlevelling.PowerLevelling;
import com.powerlevelling.client.ClientOrbState;
import com.powerlevelling.client.render.KiOrbHandRenderer;
import com.powerlevelling.network.OrbStatePayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Juice for forming ki orbs (every visible player's): a few sparks sucked INTO the orb and a soft rising chime. All local,
 * driven by ClientOrbState. Respects particleDensity, the vanilla Minimal particle setting and the KiTintParticle cap.
 */
@EventBusSubscriber(modid = PowerLevelling.MODID, value = Dist.CLIENT)
public final class KiOrbFx {
    private static final double MAX_DIST_SQ = 48.0 * 48.0;
    /** KiTintParticle friction per tick. */
    private static final double FRICTION = 0.88;

    private KiOrbFx() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.isPaused() || ClientOrbState.isEmpty()) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        long now = level.getGameTime();

        for (Map.Entry<Integer, ClientOrbState.Orb> e : ClientOrbState.entries()) {
            Entity ent = level.getEntity(e.getKey());
            if (!(ent instanceof Player player)) continue;
            ClientOrbState.Orb orb = e.getValue();
            float elapsed = orb.elapsed(now, 0f);
            float progress = orb.progress(elapsed);
            float radius = orb.radius(elapsed);
            boolean first = player == mc.player && mc.options.getCameraType().isFirstPerson()
                    && orb.hand != OrbStatePayload.HAND_NONE; // a body flare is seen from outside even in first person

            Vec3 pos = orbPos(mc, player, orb, radius, first, now);
            if (pos.distanceToSqr(cam) > MAX_DIST_SQ) continue;

            chime(level, pos, orb, progress);
            suckIn(mc, level, player, pos, orb, radius, progress, first);
        }
    }

    /** Soft rising chime: every few ticks, pitch climbing with progress, one brighter note when full, then quiet. */
    private static void chime(ClientLevel level, Vec3 pos, ClientOrbState.Orb orb, float progress) {
        if (progress >= 1f) {
            if (!orb.fullChimed) {
                orb.fullChimed = true;
                level.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                        0.18f, 1.9f, false);
            }
            return;
        }
        if (--orb.soundCooldown > 0) return;
        orb.soundCooldown = Mth.clamp(orb.duration / 5, 3, 6);
        float pitch = 0.7f + 1.1f * progress;
        float volume = 0.10f + 0.08f * progress;
        level.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, volume, pitch, false);
    }

    private static void suckIn(Minecraft mc, ClientLevel level, Player player, Vec3 pos, ClientOrbState.Orb orb, float radius,
                               float progress, boolean first) {
        if (!KiFx.particlesAllowed(mc)) return;
        RandomSource rand = level.random;
        float wanted = (0.7f + 1.8f * progress) * KiFx.density() * (first ? 0.6f : 1f);
        int count = Mth.floor(wanted) + (rand.nextFloat() < wanted - Mth.floor(wanted) ? 1 : 0);
        if (count <= 0) return;
        TextureAtlasSprite sprite = KiFx.sprite(mc);
        float shown = first ? KiOrbHandRenderer.viewRadius(radius) : radius; // size of the orb as the player sees it
        for (int i = 0; i < count && KiTintParticle.canSpawn(); i++) {
            Vec3 dir = new Vec3(rand.nextGaussian(), rand.nextGaussian(), rand.nextGaussian());
            dir = dir.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : dir.normalize();
            double dist = shown * 1.5 + (first ? 0.15 : 0.35) + rand.nextDouble() * (first ? 0.15 : 0.35);
            Vec3 off = dir.scale(dist);
            int life = 6 + rand.nextInt(3);
            double reach = (1.0 - Math.pow(FRICTION, life)) / (1.0 - FRICTION); // sum of the decaying steps
            Vec3 vel = off.scale(-0.9 / reach); // arrives at the orb as it fades
            mc.particleEngine.add(new KiTintParticle(level, pos.x + off.x, pos.y + off.y, pos.z + off.z,
                    vel.x, vel.y, vel.z, KiFx.mix(auraTint(player, orb.color), 0xFFFFFF, rand.nextFloat() * 0.5f),
                    0.04f + rand.nextFloat() * 0.04f, life, -0.4f, 0.9f, sprite));
        }
    }

    /** Player's aura colour (auraColor, -1 -> kiColor) for the orb's suck-in sparks; {@code fallback} if unknown or forced default. */
    private static int auraTint(Player player, int fallback) {
        return AuraFx.auraColorOf(player, fallback);
    }

    /** World position of the orb: the first-person view-space spot, the position the layer last rendered, or an estimate. */
    private static Vec3 orbPos(Minecraft mc, Player player, ClientOrbState.Orb orb, float radius, boolean first, long now) {
        if (orb.hand == OrbStatePayload.HAND_NONE) return player.position().add(0, player.getBbHeight() * 0.5, 0); // body flare
        if (!first && orb.renderedPos != null && now - orb.renderedAt <= 2) return orb.renderedPos;

        Vec3 look = player.getViewVector(1f);
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(look);
        double main = player.getMainArm() == HumanoidArm.RIGHT ? 1.0 : -1.0;
        double side = switch (orb.hand) {
            case OrbStatePayload.HAND_OFF -> -main;
            case OrbStatePayload.HAND_BOTH -> 0.0;
            default -> main;
        };
        Vec3 eye = player.getEyePosition(1f);
        if (first) { // same spot as KiOrbHandRenderer, in camera axes
            float r = KiOrbHandRenderer.viewRadius(radius);
            if (orb.anchor == OrbStatePayload.ANCHOR_HIP) {
                return eye.add(look.scale(-KiOrbHandRenderer.HIP_Z + r * 0.5)).add(right.scale(KiOrbHandRenderer.HIP_X * main))
                        .add(up.scale(KiOrbHandRenderer.HIP_Y));
            }
            if (orb.anchor == OrbStatePayload.ANCHOR_OVERHEAD) {
                return eye.add(look.scale(-KiOrbHandRenderer.OVERHEAD_Z + r * 0.5)).add(up.scale(KiOrbHandRenderer.OVERHEAD_Y));
            }
            if (orb.hand == OrbStatePayload.HAND_FINGER) {
                return eye.add(look.scale(-KiOrbHandRenderer.VIEW_Z + 0.28)).add(right.scale(KiOrbHandRenderer.VIEW_X * side))
                        .add(up.scale(KiOrbHandRenderer.VIEW_Y + 0.12));
            }
            return eye.add(look.scale(-KiOrbHandRenderer.VIEW_Z + r * 0.8)).add(right.scale(KiOrbHandRenderer.VIEW_X * side))
                    .add(up.scale(KiOrbHandRenderer.VIEW_Y));
        }
        if (orb.anchor == OrbStatePayload.ANCHOR_OVERHEAD) return eye.add(0, 0.55 + radius * 0.6, 0);
        if (orb.anchor == OrbStatePayload.ANCHOR_HIP) {
            return player.position().add(0, player.getBbHeight() * 0.5, 0).add(right.scale(0.42 * main)).add(look.x * 0.3, 0, look.z * 0.3);
        }
        if (orb.hand == OrbStatePayload.HAND_FINGER) return eye.add(look.scale(0.9)).add(right.scale(0.3 * side)).add(up.scale(-0.2));
        return eye.add(look.scale(0.6)).add(right.scale(0.35 * side)).add(up.scale(-0.25)); // same as the server's spawn estimate
    }
}
