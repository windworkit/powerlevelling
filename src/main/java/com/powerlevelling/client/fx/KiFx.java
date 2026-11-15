package com.powerlevelling.client.fx;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.powerlevelling.PowerLevelling;
import com.powerlevelling.client.ClientCharacters;
import com.powerlevelling.client.ClientCooldowns;
import com.powerlevelling.client.ClientOrbState;
import com.powerlevelling.client.ClientPlayerData;
import com.powerlevelling.client.ClientSkillState;
import com.powerlevelling.client.ClientVisualState;
import com.powerlevelling.character.CharacterSnapshot;
import com.powerlevelling.combat.Phase;
import com.powerlevelling.config.ClientConfig;
import com.powerlevelling.entity.KiBlastEntity;
import com.powerlevelling.ki.KiAttack;
import net.minecraft.resources.ResourceLocation;
import com.powerlevelling.ki.KiAttackParts;
import com.powerlevelling.ki.BurstAttack;
import com.powerlevelling.ki.KiAttacks;
import com.powerlevelling.network.KiImpactPayload;
import com.powerlevelling.data.KiProgression;
import com.powerlevelling.network.VisualStatePayload;
import com.powerlevelling.registry.ModParticles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

/**
 * Client-only ki attack effects, all driven locally: blast trails, impact burst/flash/sound (from KiImpactPayload),
 * the release spark burst, the predicted HUD charge state and the skill cooldown countdown (the forming orb is KiOrbFx / client/render).
 * Nothing here touches the network or the server. Every particle respects particleDensity, the vanilla "Minimal"
 * particle setting and the hard cap in KiTintParticle.
 */
@EventBusSubscriber(modid = PowerLevelling.MODID, value = Dist.CLIENT)
public final class KiFx {
    private static final double MAX_FX_DISTANCE_SQ = 64.0 * 64.0;
    private static final List<KiBlastEntity> BLASTS = new ArrayList<>();
    private static final List<KiBlastEntity> NEW_BLASTS = new ArrayList<>();
    private static ClientLevel lastLevel;

    private KiFx() {
    }

    // ---- blast tracking + per-tick work ----------------------------------------------------------------------------

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof KiBlastEntity blast) {
            BLASTS.add(blast);
            NEW_BLASTS.add(blast);
        }
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof KiBlastEntity blast) BLASTS.remove(blast);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level != lastLevel) { // new world / dimension: the particle engine was wiped
            lastLevel = level;
            KiTintParticle.resetCounter();
            KiAuraWispParticle.resetCounter();
            KiRockParticle.resetCounter();
            KiDebrisParticle.resetCounter();
            KiRockLargeParticle.resetCounter();
            KiGroundRingParticle.resetCounter();
        }
        if (level == null || mc.player == null || mc.isPaused()) return;

        ClientCooldowns.tick();
        tickSkillState(level);
        releaseBursts(mc, level);
        tickTrails(mc, level);
        // spawnAuraParticles(mc, level); // Disabled - AuraFx now handles all aura particle effects
    }

    /** Disconnect: forget everything. */
    public static void reset() {
        BLASTS.clear();
        NEW_BLASTS.clear();
        lastLevel = null;
        KiTintParticle.resetCounter();
        KiAuraParticle.resetCounter();
        KiAuraWispParticle.resetCounter();
        KiRockParticle.resetCounter();
        KiDebrisParticle.resetCounter();
        KiRockLargeParticle.resetCounter();
        KiGroundRingParticle.resetCounter();
        KiWorldFx.reset();
        BeamRenderer.reset();
        KiShake.reset();
    }

    // ---- trails ----------------------------------------------------------------------------------------------------

    private static void tickTrails(Minecraft mc, ClientLevel level) {
        if (!particlesAllowed(mc)) return;
        float density = density();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        RandomSource rand = level.random;
        TextureAtlasSprite sprite = sprite(mc);

        Iterator<KiBlastEntity> it = BLASTS.iterator();
        while (it.hasNext()) {
            KiBlastEntity b = it.next();
            if (b.isRemoved()) {
                it.remove();
                continue;
            }
            if (b.distanceToSqr(cam) > MAX_FX_DISTANCE_SQ) continue;
            if (b.getStyle() == KiBlastEntity.STYLE_BOMB && b.getMotion() == KiBlastEntity.MotionMode.RESTING) {
                bombWarning(level, b); // everyone sees the danger ring while the grenade sits there blinking
            }
            if (b.distanceToSqr(cam) < 6.0) continue; // don't fill the shooter's own view with sparks at the muzzle

            Vec3 from = new Vec3(b.xOld, b.yOld, b.zOld);
            Vec3 to = b.position();
            float radius = b.getBlastRadius();
            int color = b.getColor();
            if (b.getStyle() == KiBlastEntity.STYLE_BOMB) color = 0x585860; // smoke puffs, not bright sparks

            // ~1.2 sparks per tick for a small blast, more for a big one (the cube tail in KiBlastRenderer does most of
            // the work now, so this is ~40% fewer than before), spread along the path travelled this tick.
            float wanted = (1.2f + radius * 2.4f) * density * (b.getStyle() == KiBlastEntity.STYLE_BOMB ? 0.5f : 1f);
            int count = Mth.floor(wanted);
            if (rand.nextFloat() < wanted - count) count++;
            for (int i = 0; i < count && KiTintParticle.canSpawn(); i++) {
                double t = rand.nextDouble();
                double spread = radius * 0.8;
                double x = Mth.lerp(t, from.x, to.x) + (rand.nextDouble() - 0.5) * spread;
                double y = Mth.lerp(t, from.y, to.y) + b.getBbHeight() / 2 + (rand.nextDouble() - 0.5) * spread;
                double z = Mth.lerp(t, from.z, to.z) + (rand.nextDouble() - 0.5) * spread;
                double s = 0.01 + 0.02 * radius;
                float size = 0.05f + rand.nextFloat() * 0.05f + radius * 0.06f;
                mc.particleEngine.add(new KiTintParticle(level, x, y, z,
                        (rand.nextDouble() - 0.5) * s, (rand.nextDouble() - 0.5) * s, (rand.nextDouble() - 0.5) * s,
                        mix(color, 0xFFFFFF, rand.nextFloat() * 0.5f), size, 8 + rand.nextInt(6), -0.6f, 0.9f, sprite));
            }
        }
    }

    /** A resting bomb draws a ground ring at its blast radius on every blink. */
    private static void bombWarning(ClientLevel level, KiBlastEntity b) {
        int cycle = (int) KiBlastEntity.fusePhase(b.tickCount, b.getFuseTotal());
        if (cycle == b.clientFxCycle) return;
        b.clientFxCycle = cycle;
        ResourceLocation rl = ResourceLocation.tryParse(b.getAttackIdString());
        KiAttack atk = rl == null ? null : KiAttacks.resolve(level.registryAccess(), rl);
        float r = atk == null ? 3f : atk.explosionRadius();
        KiWorldFx.pulseRing(b.position().add(0, 0.06, 0), r, 0xFF4A24);
    }

    // ---- impact (from KiImpactPayload) -----------------------------------------------------------------------------

    public static void impact(KiImpactPayload p) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;
        Vec3 at = new Vec3(p.x(), p.y(), p.z());
        if (at.distanceToSqr(mc.gameRenderer.getMainCamera().getPosition()) > MAX_FX_DISTANCE_SQ) return;

        KiAttack attack = KiAttacks.resolve(level.registryAccess(), p.attack());
        KiAttackParts.Present v = attack == null ? KiAttackParts.Present.DEFAULT : attack.common().present();
        RandomSource rand = level.random;
        float size = Math.max(0.6f, p.size());
        int tint = p.kiColor() >= 0 && !ClientConfig.FORCE_DEFAULT_ATTACK_COLORS.get() ? p.kiColor() : v.color();

        // Feel levers from the attack's present block: screen shake (fades with distance; duration grows with hitstop) and
        // a short FOV punch for the caster (the camera is within ~3 blocks of its own burst).
        double camDist = Math.sqrt(at.distanceToSqr(mc.gameRenderer.getMainCamera().getPosition()));
        if (v.shake() > 0f) {
            KiShake.add(v.shake() * (float) Mth.clamp(1.0 - (camDist - 6.0) / 26.0, 0.0, 1.0), 6 + v.hitstop() * 2);
        }
        if (v.fovPunch() > 0f && camDist < 3.0) KiShake.fovPunch(v.fovPunch());

        // Style extras (world-space cube effects, KiWorldFx): burst = expanding ring + dome, sphere explosion = ground ring.
        if (p.style() == KiImpactPayload.STYLE_RING) {
            int expand = attack instanceof BurstAttack b ? b.burst().expandTicks() : 6;
            KiWorldFx.burst(at, p.size(), tint, mix(tint, 0xFFFFFF, 0.6f), expand);
            return; // the release sound was played by the server, no impact sparks
        }
        if (p.style() == KiImpactPayload.STYLE_EXPLOSION_RING) {
            KiWorldFx.groundRing(at, p.size(), tint);
        }
        if (p.style() == KiImpactPayload.STYLE_SLICE) {
            KiWorldFx.slash(at, tint, mix(tint, 0xFFFFFF, 0.6f));
        }
        final boolean small = p.style() == KiImpactPayload.STYLE_SPARK_SMALL;

        // Sound: explosions are quieter and lower so a charged blast doesn't deafen you.
        SoundEvent sound = attack == null ? SoundEvents.FIREWORK_ROCKET_BLAST : v.impactSoundEvent();
        float volume = small ? 0.3f : p.explosion() ? 0.5f : 0.7f;
        float pitch = (small ? 1.5f : p.explosion() ? 1.1f : 1.0f) + (rand.nextFloat() - 0.5f) * 0.2f;
        level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch, false);

        if (!particlesAllowed(mc)) return;
        float density = density();
        TextureAtlasSprite sprite = sprite(mc);

        // Flash: one big, very short-lived glow.
        if (KiTintParticle.canSpawn()) {
            mc.particleEngine.add(new KiTintParticle(level, at.x, at.y, at.z, 0, 0, 0,
                    mix(tint, 0xFFFFFF, 0.5f), size * 0.9f, 5, 0.8f, 0.9f, sprite));
        }

        // Burst of sparks (the attack's colour), or its own impact_particle if it names another particle type.
        boolean customParticle = !v.impactParticle().equals(KiAttackParts.KI_SPARK_ID);
        SimpleParticleType custom = null;
        if (customParticle) {
            var type = BuiltInRegistries.PARTICLE_TYPE.getOptional(v.impactParticle()).orElse(null);
            if (type instanceof SimpleParticleType spt) custom = spt;
        }
        float wanted = (10f + size * 12f) * density * (small ? 0.3f : p.style() == KiImpactPayload.STYLE_SLICE ? 0.6f : 1f);
        int count = Math.min(48, Mth.floor(wanted) + (rand.nextFloat() < wanted - Mth.floor(wanted) ? 1 : 0));
        double speed = 0.12 + 0.07 * size;
        for (int i = 0; i < count; i++) {
            double vx = rand.nextGaussian();
            double vy = rand.nextGaussian();
            double vz = rand.nextGaussian();
            double len = Math.max(1.0E-4, Math.sqrt(vx * vx + vy * vy + vz * vz));
            double sp = speed * (0.4 + rand.nextDouble() * 0.6) / len;
            if (custom != null) {
                level.addParticle(custom, at.x, at.y, at.z, vx * sp, vy * sp, vz * sp);
            } else if (KiTintParticle.canSpawn()) {
                mc.particleEngine.add(new KiTintParticle(level, at.x, at.y, at.z, vx * sp, vy * sp, vz * sp,
                        mix(tint, 0xFFFFFF, rand.nextFloat() * 0.6f), 0.06f + rand.nextFloat() * 0.08f,
                        12 + rand.nextInt(10), -0.5f, 0.95f, sprite));
            }
        }
    }

    // ---- local prediction (HUD charge bar) and the release spark burst -------------------------------------------------

    /**
     * Called by InputHandler the moment a skill key edge is sent. Only predicts the HUD bar (the forming orb comes from the
     * server, see ClientOrbState): PRESS starts the bar for a hold charge or a tap wind-up, RELEASE ends a hold charge (a
     * wind-up runs on by itself). The server decides for real (a rejected ack stops the bar via ClientCombat).
     */
    public static void predictSkill(LocalPlayer player, int slot, Phase phase) {
        if (phase == Phase.RELEASE) {
            ClientSkillState.release(slot);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (!ClientVisualState.selfStance() || ClientVisualState.selfCharging() || ClientPlayerData.kiBreak) return;
        var id = ClientPlayerData.loadout[slot];
        KiAttack attack = id == null ? null : KiAttacks.resolve(mc.level.registryAccess(), id);
        if (attack == null || ClientCooldowns.remaining(slot) > 0) return;
        float cost = attack.baseCost(ClientPlayerData.maxKi) * KiProgression.tierCostMult(ClientPlayerData.attackTier(id));
        if (ClientPlayerData.ki < cost) return;

        if (attack.charged()) {
            ClientSkillState.start(slot, false, attack.common().charge().maxTicks());
        } else if (attack.windupTicks() > 0) {
            ClientSkillState.start(slot, true, attack.windupTicks()); // the server starts the cooldown when it fires
        } else {
            ClientCooldowns.start(slot, attack.common().cooldown());
        }
    }

    /** Advances the predicted HUD state; drops it when it can no longer be valid. */
    private static void tickSkillState(ClientLevel level) {
        int slot = ClientSkillState.heldSlot;
        if (slot < 0) return;
        var id = ClientPlayerData.loadout[slot];
        KiAttack attack = id == null ? null : KiAttacks.resolve(level.registryAccess(), id);
        if (attack == null || !ClientVisualState.selfStance()) {
            ClientSkillState.stop(slot);
            return;
        }
        ClientSkillState.heldTicks++;
        if (ClientSkillState.heldWindup && ClientSkillState.heldTicks > ClientSkillState.heldMax + 1) ClientSkillState.stop(slot);
    }

    /** A new blast appeared (checked one tick later so its synced colour/radius have arrived): small spark burst at the orb. */
    private static void releaseBursts(Minecraft mc, ClientLevel level) {
        if (NEW_BLASTS.isEmpty()) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        boolean ok = particlesAllowed(mc);
        RandomSource rand = level.random;
        TextureAtlasSprite sprite = ok ? sprite(mc) : null;
        for (KiBlastEntity b : NEW_BLASTS) {
            if (!ok || b.isRemoved() || b.tickCount > 4 || b.distanceToSqr(cam) > MAX_FX_DISTANCE_SQ) continue;
            Vec3 at = b.position().add(0, b.getBbHeight() / 2.0, 0);
            float radius = b.getBlastRadius();
            int color = b.getColor();
            float wanted = (5f + radius * 6f) * density();
            int count = Math.min(14, Mth.floor(wanted) + (rand.nextFloat() < wanted - Mth.floor(wanted) ? 1 : 0));
            if (b.getStyle() == KiBlastEntity.STYLE_BARRAGE) count = Math.min(count, 2); // a stream: a flicker per shot
            for (int i = 0; i < count && KiTintParticle.canSpawn(); i++) {
                Vec3 d = new Vec3(rand.nextGaussian(), rand.nextGaussian(), rand.nextGaussian());
                d = d.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : d.normalize();
                double sp = (0.06 + rand.nextDouble() * 0.08) * (1.0 + radius);
                mc.particleEngine.add(new KiTintParticle(level, at.x, at.y, at.z, d.x * sp, d.y * sp, d.z * sp,
                        mix(color, 0xFFFFFF, rand.nextFloat() * 0.5f), 0.05f + rand.nextFloat() * 0.04f, 8, -0.5f, 0.9f, sprite));
            }
        }
        NEW_BLASTS.clear();
    }

    // ---- helpers ---------------------------------------------------------------------------------------------------

    static boolean particlesAllowed(Minecraft mc) {
        return mc.options.particles().get() != ParticleStatus.MINIMAL;
    }

    static float density() {
        return ClientConfig.PARTICLE_DENSITY.get().floatValue();
    }

    static TextureAtlasSprite sprite(Minecraft mc) {
        // The particle atlas is owned by the ParticleEngine, not the ModelManager, so getTextureAtlas() can't find it.
        // Use the SpriteSet captured when our particle provider was registered.
        return KiSparkParticle.Provider.SHARED.get(mc.level.random);
    }

    /** Linear mix of two packed RGB colours (t = 0 -> a, t = 1 -> b). */
    static int mix(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return (r << 16) | (g << 8) | bl;
    }

    /** Spawns soft aura particles around players who are in combat stance or charging ki. */
    private static void spawnAuraParticles(Minecraft mc, ClientLevel level) {
        if (!particlesAllowed(mc)) return;
        float auraDensity = ClientConfig.AURA_PARTICLE_DENSITY.get().floatValue();
        if (auraDensity <= 0f) return;
        
        TextureAtlasSprite sprite = KiSparkParticle.Provider.SHARED.get(level.random);
        LocalPlayer self = mc.player;
        long now = level.getGameTime();
        
        // Spawn for self
        spawnAuraForPlayer(mc, level, self, self, now, sprite, true);
        
        // Spawn for other players
        for (var other : level.players()) {
            if (other == self) continue;
            if (other.distanceToSqr(mc.gameRenderer.getMainCamera().getPosition()) > 48.0 * 48.0) continue;
            spawnAuraForPlayer(mc, level, other, self, now, sprite, false);
        }
    }

    private static void spawnAuraForPlayer(Minecraft mc, ClientLevel level, Player player,
                                             LocalPlayer viewer, long now, TextureAtlasSprite sprite, boolean isSelf) {
        // Check if player is in combat stance, charging ki, or transforming
        int entityId = player.getId();
        boolean inStance = ClientVisualState.has(entityId, VisualStatePayload.STANCE);
        boolean charging = ClientVisualState.has(entityId, VisualStatePayload.CHARGING);
        boolean transforming = ClientVisualState.has(entityId, VisualStatePayload.TRANSFORMING);
        boolean isSSJ = ClientVisualState.hasSuperSaiyan(entityId);
        if (!inStance && !charging && !transforming && !isSSJ) return;
        
        // Get player's ki color for the aura
        CharacterSnapshot snapshot = ClientCharacters.effective(entityId);
        if (snapshot == null) return;
        int auraColor = snapshot.kiColor();
        if (auraColor == -1) return;
        
        RandomSource rand = level.random;
        float density = ClientConfig.AURA_PARTICLE_DENSITY.get().floatValue();
        if (density <= 0f) return;
        
        // Number of particles per tick (scaled by density)
        int count = Math.max(1, Mth.floor(6f * density * (isSelf ? 1f : 0.6f)));
        
        // SSJ: spawn more particles with golden color
        if (isSSJ) {
            count = Math.max(1, Mth.floor(12f * density * (isSelf ? 1f : 0.8f)));
        }

        Vec3 eyePos = player.getEyePosition(1f);
        float radius = 0.6f + rand.nextFloat() * 0.4f;

        for (int i = 0; i < count && KiAuraParticle.canSpawn(); i++) {
            // Orbit around player at varying heights and radii
            float orbitRadius = 0.5f + rand.nextFloat() * 0.5f;
            float orbitSpeed = (0.03f + rand.nextFloat() * 0.03f) * (rand.nextBoolean() ? 1f : -1f);
            float startAngle = rand.nextFloat() * Mth.TWO_PI;
            float verticalOffset = -0.3f + rand.nextFloat() * 1.0f; // around waist to head
            
            // Spawn at player position with orbit data encoded in speed params
            level.addParticle(ModParticles.KI_AURA.get(),
                    eyePos.x, eyePos.y, eyePos.z,
                    orbitRadius, verticalOffset, orbitSpeed);
        }
        
        // SSJ: additional golden spark particles
        if (isSSJ) {
            spawnSSJAura(level, eyePos, rand, isSelf);
        }
    }
    
    private static void spawnSSJAura(ClientLevel level, Vec3 eyePos, RandomSource rand, boolean isSelf) {
        // Spawn golden spark particles around the player
        int count = isSelf ? 4 : 2;
        for (int i = 0; i < count; i++) {
            double x = eyePos.x + (rand.nextDouble() - 0.5) * 1.5;
            double y = eyePos.y + (rand.nextDouble() - 0.5) * 1.5;
            double z = eyePos.z + (rand.nextDouble() - 0.5) * 1.5;
            level.addParticle(ModParticles.KI_SPARK.get(), x, y, z, 0, 0.05, 0);
        }
    }
}
