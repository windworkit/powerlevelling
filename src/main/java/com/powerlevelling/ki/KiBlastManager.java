package com.powerlevelling.ki;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;

import com.powerlevelling.PowerLevelling;
import com.powerlevelling.config.ServerConfig;
import com.powerlevelling.entity.KiBlastEntity;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Enforces the blast caps (design 8.6): maxBlastsPerPlayer and maxBlastsPerLevel. Keeps one insertion-ordered set of live
 * blasts per dimension; when a cap would be exceeded the OLDEST blast (of that player / in that dimension) is discarded.
 * Server thread only. Removed entities (hit, expired, unloaded) are pruned lazily on each registration.
 */
@EventBusSubscriber(modid = PowerLevelling.MODID)
public final class KiBlastManager {
    private static final Map<ResourceKey<Level>, LinkedHashSet<KiBlastEntity>> LIVE = new HashMap<>();

    private KiBlastManager() {
    }

    /** Makes room for one more blast by {@code shooter}, then records {@code blast}. Call right before addFreshEntity. */
    public static void register(ServerLevel level, UUID shooter, KiBlastEntity blast) {
        LinkedHashSet<KiBlastEntity> set = LIVE.computeIfAbsent(level.dimension(), k -> new LinkedHashSet<>());
        set.removeIf(Entity::isRemoved);

        int maxPlayer = ServerConfig.MAX_BLASTS_PER_PLAYER.get();
        int maxLevel = ServerConfig.MAX_BLASTS_PER_LEVEL.get();

        // Per-player cap is in weight units (blast 1, homing seeker 0.6, barrage shot 0.5; Addendum C.6).
        float mine = 0f;
        for (KiBlastEntity b : set) {
            if (shooter.equals(b.getShooterUuid())) mine += b.getCapWeight();
        }
        Iterator<KiBlastEntity> it = set.iterator();
        while (mine + blast.getCapWeight() > maxPlayer && it.hasNext()) {
            KiBlastEntity b = it.next();
            if (shooter.equals(b.getShooterUuid())) {
                mine -= b.getCapWeight();
                b.discard();
                it.remove();
            }
        }

        it = set.iterator();
        while (set.size() >= maxLevel && it.hasNext()) {
            it.next().discard();
            it.remove();
        }

        set.add(blast);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        KiBlastEntity.clearHitStacks();
        LIVE.clear(); // don't leak entities from a closed world into the next one (integrated server)
    }
}
