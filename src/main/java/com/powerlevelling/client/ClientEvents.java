package com.powerlevelling.client;

import com.powerlevelling.PowerLevelling;
import com.powerlevelling.client.fx.KiAuraParticle;
import com.powerlevelling.client.fx.KiAuraWispParticle;
import com.powerlevelling.client.fx.KiDebrisParticle;
import com.powerlevelling.client.fx.KiFx;
import com.powerlevelling.client.fx.KiGroundRingParticle;
import com.powerlevelling.client.fx.KiRockLargeParticle;
import com.powerlevelling.client.fx.KiRockParticle;
import com.powerlevelling.client.fx.KiSparkParticle;
import com.powerlevelling.client.hud.ResourceHudLayer;
import com.powerlevelling.client.hud.SkillSlotLayer;
import com.powerlevelling.client.render.KiBlastRenderer;
import com.powerlevelling.client.render.GeoModelLoader;
import com.powerlevelling.client.render.EyeLayer;
import com.powerlevelling.client.render.HairLayer;
import com.powerlevelling.client.render.TailLayer;
import com.powerlevelling.client.render.KiOrbLayer;
import com.powerlevelling.client.render.AuraLayer;
import com.powerlevelling.client.input.InputHandler;
import com.powerlevelling.character.CharacterSnapshot;
import com.powerlevelling.character.TailState;
import com.powerlevelling.config.ClientConfig;
import com.powerlevelling.network.TailTogglePayload;
import com.powerlevelling.registry.ModEntities;
import com.powerlevelling.registry.ModParticles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client-only wiring: HUD layer registration and cache reset. */
public final class ClientEvents {
    private ClientEvents() {
    }

    @EventBusSubscriber(modid = PowerLevelling.MODID, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        public static void registerGuiLayers(RegisterGuiLayersEvent event) {
            event.registerAbove(VanillaGuiLayers.HOTBAR,
                    ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "resources"),
                    new ResourceHudLayer());
            // Skill slots 1-4 (stance only), drawn just above the hotbar layer like the panel.
            event.registerAbove(VanillaGuiLayers.HOTBAR,
                    ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, "skill_slots"),
                    new SkillSlotLayer());
        }

        @SubscribeEvent
        public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.KI_BLAST.get(), KiBlastRenderer::new);
            event.registerEntityRenderer(ModEntities.KI_DISC.get(), com.powerlevelling.client.render.KiDiscRenderer::new);
            event.registerEntityRenderer(ModEntities.TRAINING_DUMMY.get(), com.powerlevelling.client.render.TrainingDummyRenderer::new);
        }

        /** The ki orb, hair, tail, eye and aura layers on both player models (WIDE = Steve arms, SLIM = Alex arms). */
        @SubscribeEvent
        public static void addLayers(EntityRenderersEvent.AddLayers event) {
            PlayerRenderer wide = event.getSkin(PlayerSkin.Model.WIDE);
            if (wide != null) {
                wide.addLayer(new KiOrbLayer(wide));
                wide.addLayer(new HairLayer(wide));
                wide.addLayer(new TailLayer(wide));
                wide.addLayer(new EyeLayer(wide));
                wide.addLayer(new AuraLayer(wide));
            }
            PlayerRenderer slim = event.getSkin(PlayerSkin.Model.SLIM);
            if (slim != null) {
                slim.addLayer(new KiOrbLayer(slim));
                slim.addLayer(new HairLayer(slim));
                slim.addLayer(new TailLayer(slim));
                slim.addLayer(new EyeLayer(slim));
                slim.addLayer(new AuraLayer(slim));
            }
        }

        /** Bedrock geo models (hair, tails...) are re-read on every resource reload (F3+T). */
        @SubscribeEvent
        public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener(new GeoModelLoader());
        }

        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            for (var key : ModKeys.all()) event.register(key);
        }

        @SubscribeEvent
        public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(ModParticles.KI_SPARK.get(), KiSparkParticle.Provider::new);
            event.registerSpriteSet(ModParticles.KI_AURA.get(), KiAuraParticle.Provider::new);
            event.registerSpriteSet(ModParticles.KI_AURA_WISP.get(), KiAuraWispParticle.Provider::new);
            event.registerSpriteSet(ModParticles.KI_ROCK.get(), KiRockParticle.Provider::new);
            event.registerSpriteSet(ModParticles.KI_DEBRIS.get(), KiDebrisParticle.Provider::new);
            event.registerSpriteSet(ModParticles.KI_ROCK_LARGE.get(), KiRockLargeParticle.Provider::new);
            event.registerSpriteSet(ModParticles.KI_GROUND_RING.get(), KiGroundRingParticle.Provider::new);
        }
    }

    @EventBusSubscriber(modid = PowerLevelling.MODID, value = Dist.CLIENT)
    public static final class GameBus {
        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientPlayerData.reset();
            ClientVisualState.reset();
            ClientCombat.reset();
            InputHandler.reset();
            ClientCooldowns.reset();
            ClientSkillState.reset();
            ClientOrbState.reset();
            ClientCharacters.reset();
            TailLayer.reset();
            CharacterPrompt.reset();
            KiFx.reset();
            ClientMelee.reset();
            TurboManager.reset();
        }

        /** Joined a world: arm the "create your character" prompt (it only fires if the character is not created). */
        @SubscribeEvent
        public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
            CharacterPrompt.arm();
        }

        /** Drop cached visual state of entities that leave the client level (unloaded, died, disconnected). */
        @SubscribeEvent
        public static void onEntityLeave(EntityLeaveLevelEvent event) {
            if (event.getLevel().isClientSide()) {
                ClientVisualState.remove(event.getEntity().getId());
                ClientOrbState.remove(event.getEntity().getId());
                ClientCharacters.remove(event.getEntity().getId());
                TailLayer.forget(event.getEntity().getId());
            }
        }

        /**
         * K opens the character screen until the character is created (or a free re-creation is open), then the stats screen
         * (K again is handled inside the screens, because open screens swallow key mappings).
         */
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            while (ModKeys.OPEN_STATS.consumeClick()) {
                if (mc.player != null && mc.screen == null) ClientScreens.openMain();
            }
            while (ModKeys.TOGGLE_TAIL.consumeClick()) {
                CharacterSnapshot self = ClientCharacters.selfSnapshot();
                if (mc.player != null && mc.screen == null && self != null && self.tail() == TailState.PRESENT) {
                    PacketDistributor.sendToServer(new TailTogglePayload());
                }
            }
            CharacterPrompt.tick(mc);
        }

        /** Hide the vanilla heart row (the HUD HP bar replaces it). The armor bar is left alone. */
        @SubscribeEvent
        public static void onRenderGuiLayerPre(RenderGuiLayerEvent.Pre event) {
            if (ClientConfig.HIDE_VANILLA_HEARTS.get() && event.getName().equals(VanillaGuiLayers.PLAYER_HEALTH)) {
                event.setCanceled(true);
            }
        }
    }
}
