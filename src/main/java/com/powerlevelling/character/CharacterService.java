package com.powerlevelling.character;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.powerlevelling.PowerLevelling;
import com.powerlevelling.combat.KiCharge;
import com.powerlevelling.config.ServerConfig;
import com.powerlevelling.data.CombatState;
import com.powerlevelling.data.KiProgression;
import com.powerlevelling.network.CharacterResultPayload;
import com.powerlevelling.network.CharacterSubmitPayload.Mode;
import com.powerlevelling.network.ModNetwork;
import com.powerlevelling.network.OpenCharacterScreenPayload;
import com.powerlevelling.progression.Advancements;
import com.powerlevelling.progression.Progression;
import com.powerlevelling.registry.ModAttachments;
import com.powerlevelling.registry.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-side character logic: access, mod computation, playable checks, admin set/reset. */
public final class CharacterService {
    /** Missing-id warnings already logged this session (so a broken datapack doesn't spam on every login). */
    private static final Set<String> WARNED = new HashSet<>();

    /** Minimum ticks between two character submits / tail toggles from one player. */
    public static final int SUBMIT_COOLDOWN_TICKS = 10;
    public static final int TAIL_TOGGLE_COOLDOWN_TICKS = 10;
    /** Ticks after login before the creation prompt / free re-creation notice goes out. */
    public static final int JOIN_PROMPT_DELAY_TICKS = 40;
    /** Ki and aura colors need at least this HSV value (brightness) so they never vanish into the dark. */
    public static final float MIN_COLOR_VALUE = 0.35f;
    public static final int MAX_EYE_STYLE = 3;
    /** Command the clickable chat [Create] runs (open is allowed for every player, see ModCommands). */
    public static final String OPEN_COMMAND = "/powerlevelling character open";

    private CharacterService() {
    }

    public static CharacterData get(ServerPlayer player) {
        return player.getData(ModAttachments.CHARACTER);
    }

    /**
     * Rebuilds {@link CharacterData#mods} from the player's race + nature. If either is missing from the registries
     * the mods are NEUTRAL and freeRecreate is set (the player may re-create for free); logged once per missing id.
     */
    public static void recomputeMods(ServerPlayer player) {
        CharacterData data = get(player);
        RegistryAccess access = player.level().registryAccess();
        Race race = CharacterRegistries.getRace(access, data.race);
        KiNature nature = CharacterRegistries.getNature(access, data.nature);
        if (race == null || nature == null) {
            data.mods = CharacterMods.NEUTRAL;
            data.freeRecreate = true;
            if (race == null) warnOnce("race", data.race);
            if (nature == null) warnOnce("ki_nature", data.nature);
            return;
        }
        data.mods = CharacterMods.combine(List.of(race.modifiers(), nature.modifiers()));
    }

    private static void warnOnce(String kind, ResourceLocation id) {
        if (WARNED.add(kind + "|" + id)) {
            PowerLevelling.LOGGER.warn("Character {} '{}' is not in the registry; players using it get neutral modifiers and a free re-creation.", kind, id);
        }
    }

    /** Race exists, is not in disabledRaces, and is playable (race file) or listed in extraPlayableRaces. */
    public static boolean isPlayable(RegistryAccess access, ResourceLocation raceId) {
        Race race = CharacterRegistries.getRace(access, raceId);
        if (race == null) return false;
        if (configIds(ServerConfig.DISABLED_RACES.get()).contains(raceId)) return false;
        return race.playable() || configIds(ServerConfig.EXTRA_PLAYABLE_RACES.get()).contains(raceId);
    }

    /** Parses config id strings; a bare name means powerlevelling:name. Malformed entries are skipped. */
    private static Set<ResourceLocation> configIds(List<? extends String> raw) {
        Set<ResourceLocation> out = new HashSet<>();
        for (String s : raw) {
            ResourceLocation id = ResourceLocation.tryParse(s.contains(":") ? s : PowerLevelling.MODID + ":" + s);
            if (id != null) out.add(id);
        }
        return out;
    }

    // ---- admin / command operations (no item or playable checks; the C4 submit path does those) ----------------

    /** Sets the race (race must exist - the caller checks). A first-time or changed race resets the tail to the race default. */
    public static void setRace(ServerPlayer player, ResourceLocation raceId) {
        CharacterData data = get(player);
        Race race = CharacterRegistries.getRace(player.level().registryAccess(), raceId);
        boolean firstTime = !data.created;
        boolean changed = !raceId.equals(data.race);
        data.race = raceId;
        if (race != null && (firstTime || changed)) {
            data.tail = race.appearance().tail().initialState();
            data.tailWrapped = false;
            data.tailRegrowAt = 0L;
            if (firstTime) {
                race.appearance().hair().defaultStyle().ifPresent(style -> data.appearance = withHair(data.appearance, style));
            }
        }
        data.created = true;
        data.freeRecreate = false;
        onChanged(player);
    }

    /** Sets the ki nature (nature must exist - the caller checks). */
    public static void setNature(ServerPlayer player, ResourceLocation natureId) {
        CharacterData data = get(player);
        data.nature = natureId;
        data.created = true;
        data.freeRecreate = false;
        onChanged(player);
    }

    /** Back to "not created": progression (level, exp, stats, mastery) is untouched. */
    public static void reset(ServerPlayer player) {
        CharacterData data = get(player);
        data.created = false;
        KiCharge.resetAll(player); // an uncreated character can't hold stance / charge any more
        ModNetwork.syncVisual(player);
        onChanged(player);
    }

    /**
     * Recompute mods, mirror the race into the deprecated KiProgression.race, then re-apply attributes (HP, speed),
     * clamp ki/stamina to the new maximums and resync resources + progression (Progression.onStatsChanged does the
     * last three; the progression sync also carries race/nature/created for the client's previews).
     */
    public static void onChanged(ServerPlayer player) {
        recomputeMods(player);
        mirrorRace(player);
        Progression.onStatsChanged(player);
        ModNetwork.syncCharacter(player); // self + everyone tracking
    }


    // ---- C4: join prompt / free re-creation notice ------------------------------------------------------------------

    /** The gold clickable [Create] (runs {@link #OPEN_COMMAND}); shared by the server notice and the client chat prompt. */
    public static Component createLink() {
        return Component.translatable("message.powerlevelling.character.prompt.create").withStyle(Style.EMPTY
                .withColor(ChatFormatting.GOLD).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, OPEN_COMMAND))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("message.powerlevelling.character.prompt.hover"))));
    }

    /** Called on login: the prompt goes out JOIN_PROMPT_DELAY_TICKS later (see tickJoinPrompt). */
    public static void scheduleJoinPrompt(ServerPlayer player) {
        CombatState st = player.getData(ModAttachments.COMBAT_STATE);
        st.joinPromptPending = true;
        st.joinPromptAt = player.level().getGameTime() + JOIN_PROMPT_DELAY_TICKS;
    }

    /**
     * One-shot, from the player tick. A character with a missing race / nature gets a chat notice (K opens a free
     * re-creation); with openCreationOnJoin an uncreated (or free-recreate) player also gets the screen opened.
     * The plain "create your character" toast + chat line for new players is client-side (CharacterPrompt).
     */
    public static void tickJoinPrompt(ServerPlayer player, CombatState st, long now) {
        if (!st.joinPromptPending || now < st.joinPromptAt) return;
        st.joinPromptPending = false;
        CharacterData data = get(player);
        if (data.freeRecreate) {
            player.sendSystemMessage(Component.translatable("message.powerlevelling.character.recreate_notice", createLink())
                    .withStyle(ChatFormatting.YELLOW));
        }
        if ((!data.created || data.freeRecreate) && ServerConfig.OPEN_CREATION_ON_JOIN.get()) {
            PacketDistributor.sendToPlayer(player, new OpenCharacterScreenPayload());
        }
    }

    // ---- C4: tail toggle ---------------------------------------------------------------------------------------------

    public enum TailToggle { WRAPPED, UNWRAPPED, NO_TAIL, RATE_LIMITED }

    /** TailTogglePayload: silent when there is nothing to toggle or the player is too fast. */
    public static void handleTailToggle(ServerPlayer player) {
        toggleTail(player);
    }

    /** Flips tailWrapped (only when the tail is PRESENT), 10-tick rate limit, then syncs to self + trackers. */
    public static TailToggle toggleTail(ServerPlayer player) {
        CombatState st = player.getData(ModAttachments.COMBAT_STATE);
        long now = player.level().getGameTime();
        if (st.lastTailToggleTick > now) st.lastTailToggleTick = Long.MIN_VALUE / 2;
        if (now - st.lastTailToggleTick < TAIL_TOGGLE_COOLDOWN_TICKS) return TailToggle.RATE_LIMITED;
        st.lastTailToggleTick = now;
        CharacterData data = get(player);
        if (data.tail != TailState.PRESENT) return TailToggle.NO_TAIL;
        data.tailWrapped = !data.tailWrapped;
        ModNetwork.syncCharacter(player);
        return data.tailWrapped ? TailToggle.WRAPPED : TailToggle.UNWRAPPED;
    }

    // ---- C4: submit (create / change the character) ------------------------------------------------------------------

    /** The state a valid submit would produce; computed without touching the player. */
    private record Plan(ResourceLocation race, ResourceLocation nature, Appearance appearance, TailState tail,
                        boolean tailWrapped, long tailRegrowAt, ItemStack item) {
    }

    /** Either a plan or the reason key it was refused for. */
    private record Outcome(Plan plan, String reason) {
        static Outcome fail(String reason) {
            return new Outcome(null, reason);
        }

        static Outcome ok(Plan plan) {
            return new Outcome(plan, "");
        }
    }

    /**
     * CharacterSubmitPayload. Validation order (B.7.2): rate limit, mode/state (+ change item), race, nature, look
     * (hair, eyes, parts, colors, size), tail rules, no-op check; then apply. The client's numbers are never used for
     * stats: onChanged recomputes the mods and every attribute from the registries.
     */
    public static void handleSubmit(ServerPlayer player, Mode mode, CharacterChoice choice) {
        CombatState st = player.getData(ModAttachments.COMBAT_STATE);
        long now = player.level().getGameTime();
        if (st.lastCharacterSubmitTick > now) st.lastCharacterSubmitTick = Long.MIN_VALUE / 2;
        if (now - st.lastCharacterSubmitTick < SUBMIT_COOLDOWN_TICKS) {
            sendResult(player, false, "rate_limited");
            return;
        }
        st.lastCharacterSubmitTick = now;

        if (mode == null || choice == null || choice.appearance() == null) {
            sendResult(player, false, "invalid");
            return;
        }
        Outcome outcome = validate(player, mode, choice);
        if (outcome.plan() == null) {
            sendResult(player, false, outcome.reason());
            return;
        }
        apply(player, mode, outcome.plan());
    }

    private static Outcome validate(ServerPlayer player, Mode mode, CharacterChoice choice) {
        CharacterData cur = get(player);
        RegistryAccess access = player.level().registryAccess();
        boolean creative = player.isCreative();

        // 2. mode / state, and the change item
        if (mode == Mode.CREATE) {
            if (cur.created && !cur.freeRecreate) return Outcome.fail("already_created");
        } else if (!cur.created) {
            return Outcome.fail("not_created");
        }
        ItemStack item = ItemStack.EMPTY;
        if (mode != Mode.CREATE && !creative) {
            item = findChangeItem(player, mode);
            if (item.isEmpty()) return Outcome.fail("item_required");
        }

        // 3. race: exists, playable, not hidden (STYLE / NATURE keep the current race)
        boolean keepRace = mode == Mode.STYLE || mode == Mode.NATURE;
        if (keepRace && !choice.race().equals(cur.race)) return Outcome.fail("race_locked");
        ResourceLocation raceId = keepRace ? cur.race : choice.race();
        Race race = CharacterRegistries.getRace(access, raceId);
        if (race == null) return Outcome.fail("unknown_race");
        if (!keepRace && (race.hidden() || !isPlayable(access, raceId))) return Outcome.fail("race_unavailable");

        // 4. nature: exists and enabled (STYLE keeps the current nature)
        ResourceLocation natureId = cur.nature;
        if (mode == Mode.STYLE) {
            if (!choice.nature().equals(cur.nature)) return Outcome.fail("nature_locked");
        } else {
            natureId = choice.nature();
            KiNature nature = CharacterRegistries.getNature(access, natureId);
            if (nature == null) return Outcome.fail("unknown_nature");
            if (!nature.enabled()) return Outcome.fail("nature_disabled");
        }

        Appearance appearance;
        TailState tail;
        boolean wrapped;
        long regrowAt;
        if (mode == Mode.NATURE) {
            // Only the nature changes; the look fields of the request are ignored.
            appearance = cur.appearance;
            tail = cur.tail;
            wrapped = cur.tailWrapped;
            regrowAt = cur.tailRegrowAt;
        } else {
            // 5-7. cosmetics, colors, sizes
            Appearance in = choice.appearance();
            if (!Float.isFinite(in.height()) || !Float.isFinite(in.build())) return Outcome.fail("invalid");
            Race.RaceAppearance ra = race.appearance();

            Race.Hair hair = ra.hair();
            ResourceLocation style = in.hairStyle();
            int hairColor = in.hairColor() & 0xFFFFFF;
            if (!Appearance.NO_HAIR.equals(style)) {
                if (!hair.allowed()) return Outcome.fail("hair_not_allowed");
                CosmeticPart part = CharacterRegistries.getPart(access, style);
                if (part == null || !"hair".equals(part.slot())) return Outcome.fail("bad_hair");
                if (!hair.styles().allows(style)) return Outcome.fail("hair_not_allowed");
                if (!hair.colors().allows(hairColor)) return Outcome.fail("bad_hair_color"); // "any" for Saiyan (B.10)
            }

            int eyeColor = in.eyeColor() & 0xFFFFFF;
            if (!ra.eyes().colors().allows(eyeColor)) return Outcome.fail("bad_eye_color");
            if (in.eyeStyle() < 0 || in.eyeStyle() > MAX_EYE_STYLE) return Outcome.fail("bad_eye_style");

            int kiColor = in.kiColor() & 0xFFFFFF;
            if (colorValue(kiColor) < MIN_COLOR_VALUE) return Outcome.fail("ki_too_dark");
            int auraColor = in.auraColor() < 0 ? -1 : in.auraColor() & 0xFFFFFF;
            if (auraColor >= 0 && colorValue(auraColor) < MIN_COLOR_VALUE) return Outcome.fail("aura_too_dark");

            int skinColor = in.skinColor() < 0 ? -1 : in.skinColor() & 0xFFFFFF;
            Race.Skin skin = ra.skin();
            if (skin.mode() == Race.SkinMode.VANILLA) {
                if (skinColor >= 0) return Outcome.fail("bad_skin");
            } else if (!skin.palette().isEmpty()) {
                if (skinColor < 0) skinColor = skin.palette().get(0); // palette races always have a tinted skin
                else if (!skin.palette().contains(skinColor)) return Outcome.fail("bad_skin");
            } else if (skinColor >= 0) {
                return Outcome.fail("bad_skin");
            }

            List<ResourceLocation> parts = new ArrayList<>();
            Set<String> usedSlots = new HashSet<>();
            for (ResourceLocation id : in.parts()) {
                CosmeticPart part = CharacterRegistries.getPart(access, id);
                if (part == null || "hair".equals(part.slot()) || "tail".equals(part.slot())) return Outcome.fail("bad_part");
                List<ResourceLocation> allowed = ra.parts().slots().get(part.slot());
                if (allowed == null || !allowed.contains(id)) return Outcome.fail("part_not_allowed");
                if (!usedSlots.add(part.slot())) return Outcome.fail("duplicate_slot");
                parts.add(id);
            }

            float height = Mth.clamp(in.height(), ra.height().min(), ra.height().max());
            float build = Mth.clamp(in.build(), ra.build().min(), ra.build().max());
            appearance = new Appearance(style, hairColor, eyeColor, kiColor, auraColor, skinColor, height, build,
                    List.copyOf(parts), in.eyeStyle());

            // 8. tail rules. LOST stays LOST (and keeps its regrow time) unless this is a whole new character.
            boolean fresh = mode == Mode.CREATE || mode == Mode.FULL;
            if (!fresh && cur.tail == TailState.LOST) {
                tail = TailState.LOST;
                wrapped = false;
                regrowAt = cur.tailRegrowAt;
            } else {
                tail = switch (ra.tail().mode()) {
                    case NONE -> TailState.NONE;
                    case FORCED -> TailState.PRESENT;
                    case DEFAULT_ON, OPTIONAL -> choice.tailPresent() ? TailState.PRESENT : TailState.NONE;
                };
                wrapped = tail == TailState.PRESENT && choice.tailWrapped();
                regrowAt = 0L;
            }
        }

        // 9. no-op edits are refused (so an item is never wasted)
        boolean newCharacter = !cur.created || cur.freeRecreate;
        if (!newCharacter && raceId.equals(cur.race) && natureId.equals(cur.nature) && appearance.equals(cur.appearance)
                && tail == cur.tail && wrapped == cur.tailWrapped) {
            return Outcome.fail("no_change");
        }
        return Outcome.ok(new Plan(raceId, natureId, appearance, tail, wrapped, regrowAt, item));
    }

    /** The change item for {@code mode} held in the main hand, then the off hand, or ItemStack.EMPTY. */
    private static ItemStack findChangeItem(ServerPlayer player, Mode mode) {
        Item wanted = switch (mode) {
            case STYLE -> ModItems.STYLE_CAPSULE.get();
            case NATURE -> ModItems.ATTUNEMENT_CRYSTAL.get();
            case FULL -> ModItems.REINCARNATION_ORB.get();
            default -> null;
        };
        if (wanted == null) return ItemStack.EMPTY;
        if (player.getMainHandItem().is(wanted)) return player.getMainHandItem();
        if (player.getOffhandItem().is(wanted)) return player.getOffhandItem();
        return ItemStack.EMPTY;
    }

    private static void apply(ServerPlayer player, Mode mode, Plan plan) {
        CharacterData data = get(player);
        data.race = plan.race();
        data.nature = plan.nature();
        data.appearance = plan.appearance();
        data.tail = plan.tail();
        data.tailWrapped = plan.tailWrapped();
        data.tailRegrowAt = plan.tailRegrowAt();
        data.created = true;
        data.freeRecreate = false;

        // FULL (Reincarnation Orb) also refunds all spent stat points, exactly like the Rebirth Orb respec (tail was reset in validate).
        boolean refunded = false;
        if (mode == Mode.FULL) {
            KiProgression prog = player.getData(ModAttachments.KI_PROGRESSION);
            int spent = prog.spentPoints();
            if (spent > 0) {
                prog.unspentPoints += spent;
                prog.clearStats();
                refunded = true;
            }
        }
        // plan.item() is only non-empty for non-creative STYLE / NATURE / FULL: consumed on success only.
        if (!plan.item().isEmpty()) plan.item().shrink(1);

        if (refunded) Progression.onStatsChanged(player);
        onChanged(player); // mods, mirror race, attributes + resources, progression sync, character sync

        sendResult(player, true, "");
        Advancements.characterCreated(player);
        RegistryAccess access = player.level().registryAccess();
        Race race = CharacterRegistries.getRace(access, data.race);
        KiNature nature = CharacterRegistries.getNature(access, data.nature);
        Component raceName = race == null ? Component.literal(data.race.getPath()) : Component.translatable(race.name());
        Component natureName = nature == null ? Component.literal(data.nature.getPath()) : Component.translatable(nature.name());
        String key = switch (mode) {
            case CREATE -> "message.powerlevelling.character.created";
            case STYLE -> "message.powerlevelling.character.restyled";
            case NATURE -> "message.powerlevelling.character.nature_changed";
            case FULL -> "message.powerlevelling.character.reborn";
        };
        player.sendSystemMessage(Component.translatable(key, raceName, natureName).withStyle(ChatFormatting.GOLD));
        Progression.playSound(player, mode == Mode.CREATE ? SoundEvents.PLAYER_LEVELUP : SoundEvents.BEACON_ACTIVATE, 1.0f,
                mode == Mode.CREATE ? 1.0f : 1.2f);
    }

    private static void sendResult(ServerPlayer player, boolean ok, String reason) {
        PacketDistributor.sendToPlayer(player, new CharacterResultPayload(ok, reason));
    }

    /** HSV value (brightness) of an RGB color, 0..1. */
    public static float colorValue(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return Math.max(r, Math.max(g, b)) / 255f;
    }

    public static void mirrorRace(ServerPlayer player) {
        KiProgression p = player.getData(ModAttachments.KI_PROGRESSION);
        p.race = get(player).race;
    }

    private static Appearance withHair(Appearance a, ResourceLocation style) {
        return new Appearance(style, a.hairColor(), a.eyeColor(), a.kiColor(), a.auraColor(), a.skinColor(),
                a.height(), a.build(), a.parts(), a.eyeStyle());
    }
}
