package com.powerlevelling.progression;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/** How a kill victim is classified for Battle EXP (Addendum A.1). */
public enum MobClass {
    PLAYER(1.0),
    BOSS(3.0),
    HOSTILE(1.0),
    NEUTRAL(0.5),
    PASSIVE(0.1);

    public final double expMultiplier;

    MobClass(double expMultiplier) {
        this.expMultiplier = expMultiplier;
    }

    public static MobClass of(LivingEntity e) {
        if (e instanceof Player) return PLAYER;
        EntityType<?> t = e.getType();
        if (t == EntityType.WITHER || t == EntityType.ENDER_DRAGON || t == EntityType.WARDEN || t == EntityType.ELDER_GUARDIAN) {
            return BOSS;
        }
        if (e instanceof Enemy || t.getCategory() == MobCategory.MONSTER) return HOSTILE;
        if (e instanceof NeutralMob) return NEUTRAL; // wolves, bees, iron golems, llamas...
        return PASSIVE;
    }
}
