package com.powerlevelling.entity;

import java.util.Locale;

import com.powerlevelling.registry.ModItems;

import net.minecraft.network.chat.Component;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Training Dummy (M5, passive mode). No AI, can't be pushed or knocked back, never dies: HP is topped up before every hit
 * and a hit can't take it below 1. Its name tag shows the last hit's damage, the combo count and the combo's total damage,
 * and goes back to the plain name after 3 s without hits. Placed with its spawn egg; sneak + right-click with an empty hand
 * picks it up (gives the egg back outside creative). /kill and the void still remove it. M8 adds guard / attack modes.
 */
public class TrainingDummyEntity extends Mob {
    private static final int RESET_TICKS = 60;
    private static final int OWN_COMBO_WINDOW = 20; // fallback combo counting for non-melee hits

    private float lastDamage;
    private float comboDamage;
    private int combo;
    private long lastHitTick = Long.MIN_VALUE / 2;
    private boolean showing;

    public TrainingDummyEntity(EntityType<? extends TrainingDummyEntity> type, Level level) {
        super(type, level);
        setNoAi(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1000.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        if (isInvulnerableTo(source)) return false;
        setHealth(getMaxHealth()); // "resets HP": always full before a hit ...
        boolean hurt = super.hurt(source, Math.min(amount, getMaxHealth() - 1f)); // ... and a hit never kills
        if (hurt) recordHit(amount);
        return hurt;
    }

    private void recordHit(float amount) {
        long now = level().getGameTime();
        if (now - lastHitTick > OWN_COMBO_WINDOW) {
            combo = 0;
            comboDamage = 0f;
        }
        combo++;
        comboDamage += amount;
        lastDamage = amount;
        lastHitTick = now;
        refreshName();
    }

    /** MeleeEngine tells the dummy the exact combo step after a melee hit (it counts hits in hitstun, not a time window). */
    public void noteCombo(int comboHit) {
        if (comboHit == 1) comboDamage = lastDamage;
        combo = comboHit;
        refreshName();
    }

    private void refreshName() {
        setCustomName(Component.translatable("entity.powerlevelling.training_dummy.hit",
                fmt(lastDamage), combo, fmt(comboDamage)));
        setCustomNameVisible(true);
        showing = true;
    }

    private static String fmt(float f) {
        return String.format(Locale.ROOT, "%.1f", f);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        long now = level().getGameTime();
        if (showing && now - lastHitTick > RESET_TICKS) {
            showing = false;
            combo = 0;
            comboDamage = 0f;
            setCustomName(null);
            setCustomNameVisible(false);
        }
        if (getHealth() < getMaxHealth() && now - lastHitTick > RESET_TICKS) setHealth(getMaxHealth());
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty()) {
            if (!level().isClientSide) {
                if (!player.getAbilities().instabuild) {
                    ItemStack egg = new ItemStack(ModItems.TRAINING_DUMMY_SPAWN_EGG.get());
                    if (!player.getInventory().add(egg)) player.drop(egg, false);
                }
                discard();
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }
}
