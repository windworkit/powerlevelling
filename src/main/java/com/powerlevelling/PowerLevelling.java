package com.powerlevelling;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import com.powerlevelling.config.ClientConfig;
import com.powerlevelling.config.ServerConfig;
import com.powerlevelling.registry.ModRegistries;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(PowerLevelling.MODID)
public class PowerLevelling {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "powerlevelling";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public PowerLevelling(IEventBus modEventBus, ModContainer modContainer) {
        // Register the config specs so FML creates and loads the config files for us
        modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);

        // Register the built-in config screen (client-side only)
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        }

        // Register all deferred registers to the mod event bus
        ModRegistries.register(modEventBus);
    }
}
