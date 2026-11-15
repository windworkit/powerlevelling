package com.powerlevelling.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** Client key mappings (registered in ClientEvents.ModBus). All are rebindable in Options > Controls. */
public final class ModKeys {
    public static final String CATEGORY = "key.categories.powerlevelling";

    /**
     * Active only while Combat Stance is on (and no screen is open). Conflicts only with itself, so stance keys
     * (F, 1-4, middle mouse) can share a key with vanilla bindings (swap hands, hotbar, pick block) without a
     * red conflict warning; InputHandler consumes the vanilla click while in stance so both don't fire.
     */
    public static final IKeyConflictContext IN_STANCE = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            return KeyConflictContext.IN_GAME.isActive() && ClientVisualState.selfStance();
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return other == this;
        }
    };

    public static final KeyMapping OPEN_STATS = new KeyMapping("key.powerlevelling.stats",
            InputConstants.Type.KEYSYM, InputConstants.KEY_K, CATEGORY);

    public static final KeyMapping STANCE = new KeyMapping("key.powerlevelling.stance",
            InputConstants.Type.KEYSYM, InputConstants.KEY_R, CATEGORY);

    public static final KeyMapping GUARD = new KeyMapping("key.powerlevelling.guard",
            IN_STANCE, InputConstants.Type.KEYSYM, InputConstants.KEY_F, CATEGORY);

    public static final KeyMapping DASH = new KeyMapping("key.powerlevelling.dash",
            InputConstants.Type.KEYSYM, InputConstants.KEY_LALT, CATEGORY);

    public static final KeyMapping CHARGE_KI = new KeyMapping("key.powerlevelling.charge_ki",
            InputConstants.Type.KEYSYM, InputConstants.KEY_C, CATEGORY);

    /** Skill slots 1-4 (default keys 1-4), only active in stance. */
    public static final KeyMapping[] SKILLS = {
            new KeyMapping("key.powerlevelling.skill_1", IN_STANCE, InputConstants.Type.KEYSYM, InputConstants.KEY_1, CATEGORY),
            new KeyMapping("key.powerlevelling.skill_2", IN_STANCE, InputConstants.Type.KEYSYM, InputConstants.KEY_2, CATEGORY),
            new KeyMapping("key.powerlevelling.skill_3", IN_STANCE, InputConstants.Type.KEYSYM, InputConstants.KEY_3, CATEGORY),
            new KeyMapping("key.powerlevelling.skill_4", IN_STANCE, InputConstants.Type.KEYSYM, InputConstants.KEY_4, CATEGORY),
    };

    /** Loadout wheel (not implemented yet; V keeps it clear of the default transform key). */
    public static final KeyMapping LOADOUT = new KeyMapping("key.powerlevelling.loadout",
            InputConstants.Type.KEYSYM, InputConstants.KEY_V, CATEGORY);

    /** Middle mouse. In-stance context so vanilla pick-block keeps working outside stance. */
    public static final KeyMapping LOCK_ON = new KeyMapping("key.powerlevelling.lock_on",
            IN_STANCE, InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_MIDDLE, CATEGORY);

    /** Wrap / unwrap the tail (unbound by default; only does something while the character has a tail). */
    public static final KeyMapping TOGGLE_TAIL = new KeyMapping("key.powerlevelling.toggle_tail",
            InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);

    /** Toggle transformation (Super Saiyan, etc.) while in stance. Default G. */
    public static final KeyMapping TRANSFORM = new KeyMapping("key.powerlevelling.transform",
            IN_STANCE, InputConstants.Type.KEYSYM, InputConstants.KEY_G, CATEGORY);

    /** Turbo mode: boost speed and ki drain; default T. Works both in and out of stance. Press to toggle. */
    public static final KeyMapping TURBO = new KeyMapping("key.powerlevelling.turbo",
            InputConstants.Type.KEYSYM, InputConstants.KEY_T, CATEGORY);

    private ModKeys() {
    }

    public static List<KeyMapping> all() {
        List<KeyMapping> list = new ArrayList<>(List.of(OPEN_STATS, STANCE, GUARD, DASH, CHARGE_KI, LOADOUT, LOCK_ON, TOGGLE_TAIL, TRANSFORM, TURBO));
        list.addAll(List.of(SKILLS));
        return list;
    }
}
