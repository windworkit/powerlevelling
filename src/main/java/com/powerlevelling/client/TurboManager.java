package com.powerlevelling.client;

import com.powerlevelling.config.ClientConfig;
import com.powerlevelling.PowerLevelling;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Turbo mode: holds T to get amplified movement speed.
 */
@EventBusSubscriber(modid = "powerlevelling", value = Dist.CLIENT)
public final class TurboManager {
    private static final ResourceLocation TURBO_SPEED_ID = 
            ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "turbo_speed");
    private static boolean wasTurbo = false;

    private TurboManager() {}

    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        
        if (player == null) {
            if (wasTurbo) removeSpeedBoost(null);
            wasTurbo = false;
            return;
        }

        boolean isTurbo = ClientVisualState.selfTurbo();

        if (isTurbo != wasTurbo) {
            if (isTurbo) {
                // Activating turbo
                applySpeedBoost(player);
            } else {
                // Deactivating turbo
                removeSpeedBoost(player);
            }
            wasTurbo = isTurbo;
        } else if (isTurbo) {
            // Already in turbo state, update if config changed
            AttributeInstance speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttr != null) {
                float mult = ClientConfig.TURBO_SPEED_MULT.get().floatValue();
                double expectedAmount = mult - 1.0;
                // Check if modifier needs update by looking at modifiers with our ID
                AttributeModifier existing = null;
                for (AttributeModifier m : speedAttr.getModifiers()) {
                    if (m.id().equals(TURBO_SPEED_ID)) {
                        existing = m;
                        break;
                    }
                }
                if (existing == null || Math.abs(existing.amount() - expectedAmount) > 0.001) {
                    speedAttr.removeModifier(TURBO_SPEED_ID);
                    speedAttr.addOrReplacePermanentModifier(
                        new AttributeModifier(TURBO_SPEED_ID, expectedAmount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                }
            }
        }
    }

    private static void applySpeedBoost(LocalPlayer player) {
        AttributeInstance speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            float mult = ClientConfig.TURBO_SPEED_MULT.get().floatValue();
            double turboAmount = mult - 1.0;
            speedAttr.removeModifier(TURBO_SPEED_ID);
            speedAttr.addOrReplacePermanentModifier(
                new AttributeModifier(TURBO_SPEED_ID, turboAmount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void removeSpeedBoost(LocalPlayer player) {
        if (player == null) return;
        AttributeInstance speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(TURBO_SPEED_ID);
        }
    }

    public static void reset() {
        wasTurbo = false;
    }
}
