package com.powerlevelling.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.powerlevelling.PowerLevelling;
import com.powerlevelling.character.Appearance;
import com.powerlevelling.character.CharacterBalance;
import com.powerlevelling.character.Channel;
import com.powerlevelling.character.CharacterMods;
import com.powerlevelling.character.CharacterData;
import com.powerlevelling.character.CharacterRegistries;
import com.powerlevelling.character.CharacterService;
import com.powerlevelling.character.KiNature;
import com.powerlevelling.character.Race;
import com.powerlevelling.config.ServerConfig;
import com.powerlevelling.data.CombatResources;
import com.powerlevelling.data.KiProgression;
import com.powerlevelling.data.KiProgression.MasteryTrack;
import com.powerlevelling.data.KiProgression.Stat;
import com.powerlevelling.data.KiProgression.Track;
import com.powerlevelling.ki.KiAttack;
import com.powerlevelling.ki.KiAttackRegistry;
import com.powerlevelling.ki.KiSkills;
import com.powerlevelling.network.ModNetwork;
import com.powerlevelling.network.OpenCharacterScreenPayload;
import com.powerlevelling.progression.Progression;
import com.powerlevelling.progression.StatEffects;
import com.powerlevelling.registry.ModAttachments;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.RegistryAccess;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * /powerlevelling (alias /pl): help | setki | setlevel | addexp | setpoints | setstat | loadout set/clear | info |
 * character info/list/reset/balance/set (op level 2); character open / tail are open to every player.
 */
@EventBusSubscriber(modid = PowerLevelling.MODID)
public final class ModCommands {
    private static final String CHARACTER = "character";
    private static final Predicate<CommandSourceStack> OP = src -> src.hasPermission(2);
    private static final int ARG_MAX = 1000; // upper bound for command args; real limits are checked against config

    private ModCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        List<String> levelTargets = new ArrayList<>(Track.ids());
        levelTargets.add(CHARACTER);

        // The root is open to everyone because `character open` and `character tail` are player commands (the clickable
        // chat [Create] runs the first). Every other node below carries its own op-level-2 requirement.
        LiteralCommandNode<CommandSourceStack> root = d.register(Commands.literal("powerlevelling")
                .executes(ctx -> bareHelp(ctx.getSource()))
                .then(Commands.literal("help")
                        .executes(ctx -> help(ctx.getSource()))
                        .then(Commands.literal("admin")
                                .requires(OP)
                                .executes(ctx -> helpAdmin(ctx.getSource()))))
                // Aliases for player-friendly commands
                .then(Commands.literal("stats")
                        .executes(ctx -> info(ctx.getSource())))
                .then(Commands.literal("me")
                        .executes(ctx -> characterInfo(ctx.getSource())))
                .then(Commands.literal("give")
                        .requires(OP)
                        .then(Commands.literal("exp")
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ctx -> addExp(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "amount"))))))
                .then(Commands.literal("setki")
                        .requires(OP)
                        .then(Commands.argument("amount", FloatArgumentType.floatArg(0f))
                                .executes(ctx -> setKi(ctx.getSource(), FloatArgumentType.getFloat(ctx, "amount")))))
                // setlevel <track|character> <level>
                .then(Commands.literal("setlevel")
                        .requires(OP)
                        .then(Commands.argument("track", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(levelTargets, builder))
                                .then(Commands.argument("level", IntegerArgumentType.integer(0, ARG_MAX))
                                        .executes(ctx -> setLevel(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "track"),
                                                IntegerArgumentType.getInteger(ctx, "level"))))))
                .then(Commands.literal("addexp")
                        .requires(OP)
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(ctx -> addExp(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "amount")))))
                .then(Commands.literal("setpoints")
                        .requires(OP)
                        .then(Commands.argument("points", IntegerArgumentType.integer(0, 1_000_000))
                                .executes(ctx -> setPoints(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "points")))))
                .then(Commands.literal("setstat")
                        .requires(OP)
                        .then(Commands.argument("stat", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Stat.ids(), builder))
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, ARG_MAX))
                                        .executes(ctx -> setStat(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "stat"),
                                                IntegerArgumentType.getInteger(ctx, "value"))))))
                // loadout set <slot 1-4> <attack id> | loadout clear <slot 1-4> (players edit their own loadout)
                .then(Commands.literal("loadout")
                        .then(Commands.literal("set")
                                .then(Commands.argument("slot", IntegerArgumentType.integer(1, KiProgression.LOADOUT_SLOTS))
                                        .then(Commands.argument("attack", ResourceLocationArgument.id())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                                        KiAttackRegistry.ids(ctx.getSource().registryAccess()), builder))
                                                .executes(ctx -> loadoutSet(ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "slot"),
                                                        ResourceLocationArgument.getId(ctx, "attack"))))))
                        .then(Commands.literal("clear")
                                .then(Commands.argument("slot", IntegerArgumentType.integer(1, KiProgression.LOADOUT_SLOTS))
                                        .executes(ctx -> loadoutClear(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "slot"))))))
                .then(Commands.literal("info")
                        .executes(ctx -> info(ctx.getSource())))
                // character open | tail (everyone) | info | list | reset | balance | set race <id> | set nature <id> (op)
                .then(Commands.literal(CHARACTER)
                        .then(Commands.literal("open")
                                .executes(ctx -> characterOpen(ctx.getSource())))
                        .then(Commands.literal("tail")
                                .executes(ctx -> characterTail(ctx.getSource())))
                        .then(Commands.literal("info")
                                .requires(OP)
                                .executes(ctx -> characterInfo(ctx.getSource())))
                        .then(Commands.literal("list")
                                .requires(OP)
                                .executes(ctx -> characterList(ctx.getSource())))
                        .then(Commands.literal("reset")
                                .requires(OP)
                                .executes(ctx -> characterReset(ctx.getSource())))
                        .then(Commands.literal("balance")
                                .requires(OP)
                                .executes(ctx -> characterBalance(ctx.getSource())))
                        .then(Commands.literal("set")
                                .requires(OP)
                                .then(Commands.literal("race")
                                        .then(Commands.argument("race", ResourceLocationArgument.id())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                                        CharacterRegistries.ids(ctx.getSource().registryAccess(), CharacterRegistries.RACE), builder))
                                                .executes(ctx -> characterSetRace(ctx.getSource(),
                                                        ResourceLocationArgument.getId(ctx, "race")))))
                                .then(Commands.literal("hair")
                                        .then(Commands.argument("style", ResourceLocationArgument.id())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(hairStyleIds(ctx.getSource().registryAccess()), builder))
                                                .executes(ctx -> characterSetHair(ctx.getSource(),
                                                        ResourceLocationArgument.getId(ctx, "style"), null))
                                                .then(Commands.argument("color", StringArgumentType.greedyString())
                                                        .executes(ctx -> characterSetHair(ctx.getSource(),
                                                                ResourceLocationArgument.getId(ctx, "style"),
                                                                StringArgumentType.getString(ctx, "color"))))))
                                .then(Commands.literal("nature")
                                        .then(Commands.argument("nature", ResourceLocationArgument.id())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                                        CharacterRegistries.ids(ctx.getSource().registryAccess(), CharacterRegistries.KI_NATURE), builder))
                                                .executes(ctx -> characterSetNature(ctx.getSource(),
                                                        ResourceLocationArgument.getId(ctx, "nature"))))))));

        // /pl: same tree as /powerlevelling. Redirects carry no executes of their own, so the no-arg help is set here too.
        d.register(Commands.literal("pl")
                .executes(ctx -> bareHelp(ctx.getSource()))
                .redirect(root));
    }

    // ---- presentation helpers -----------------------------------------------------------------------------------

    /** A value inside a line: yellow, whatever colour the line itself uses. Translated names pass through as Components. */
    private static Component v(Object value) {
        Component c = value instanceof Component comp ? comp : Component.literal(String.valueOf(value));
        return Component.empty().append(c).withStyle(ChatFormatting.YELLOW);
    }

    private static Object[] values(Object... args) {
        Object[] out = new Object[args.length];
        for (int i = 0; i < args.length; i++) out[i] = v(args[i]);
        return out;
    }

    /** Green success line. */
    private static Component ok(String key, Object... args) {
        return Component.translatable(key, values(args)).withStyle(ChatFormatting.GREEN);
    }

    /** Gray label line; values inside are yellow. */
    private static Component line(String key, Object... args) {
        return Component.translatable(key, values(args)).withStyle(ChatFormatting.GRAY);
    }

    /** Gold bold heading. */
    private static Component title(String key, Object... args) {
        return Component.translatable(key, values(args)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
    }

    /** Red error line. */
    private static void fail(CommandSourceStack src, String key, Object... args) {
        src.sendFailure(Component.translatable(key, values(args)).withStyle(ChatFormatting.RED));
    }

    // ---- help ---------------------------------------------------------------------------------------------------

    /** /powerlevelling with no argument: the full list for ops, the player commands for everyone else. */
    private static int bareHelp(CommandSourceStack src) {
        if (src.hasPermission(2)) return helpAdmin(src);
        src.sendSuccess(() -> title("commands.powerlevelling.help.title"), false);
        helpGroup(src, "commands.powerlevelling.help.group.character");
        helpEntry(src, "character open", "commands.powerlevelling.help.character_open");
        helpEntry(src, "character tail", "commands.powerlevelling.help.character_tail");
        helpGroup(src, "commands.powerlevelling.help.group.stats");
        helpEntry(src, "stats", "commands.powerlevelling.help.info");
        helpEntry(src, "me", "commands.powerlevelling.help.character_info");
        helpGroup(src, "commands.powerlevelling.help.group.ki");
        helpEntry(src, "loadout set <slot 1-4> <attack>", "commands.powerlevelling.help.loadout_set");
        helpEntry(src, "loadout clear <slot 1-4>", "commands.powerlevelling.help.loadout_clear");
        return 1;
    }

    private static int help(CommandSourceStack src) {
        src.sendSuccess(() -> title("commands.powerlevelling.help.title"), false);
        src.sendSuccess(() -> Component.translatable("commands.powerlevelling.help.alias").withStyle(ChatFormatting.GRAY), false);

        helpGroup(src, "commands.powerlevelling.help.group.stats");
        helpEntry(src, "info", "commands.powerlevelling.help.info");
        helpEntry(src, "stats", "commands.powerlevelling.help.info");
        helpEntry(src, "me", "commands.powerlevelling.help.character_info");

        helpGroup(src, "commands.powerlevelling.help.group.ki");
        helpEntry(src, "loadout set <slot 1-4> <attack>", "commands.powerlevelling.help.loadout_set");
        helpEntry(src, "loadout clear <slot 1-4>", "commands.powerlevelling.help.loadout_clear");
        return 1;
    }

    private static int helpAdmin(CommandSourceStack src) {
        help(src);
        helpGroup(src, "commands.powerlevelling.help.group.admin");
        helpEntry(src, "give exp <amount>", "commands.powerlevelling.help.addexp");
        helpEntry(src, "setki <amount>", "commands.powerlevelling.help.setki");
        helpEntry(src, "setlevel <track|character> <level>", "commands.powerlevelling.help.setlevel");
        helpEntry(src, "addexp <amount>", "commands.powerlevelling.help.addexp");
        helpEntry(src, "setpoints <points>", "commands.powerlevelling.help.setpoints");
        helpEntry(src, "setstat <stat> <value>", "commands.powerlevelling.help.setstat");
        helpGroup(src, "commands.powerlevelling.help.group.character");
        helpEntry(src, "character open", "commands.powerlevelling.help.character_open");
        helpEntry(src, "character tail", "commands.powerlevelling.help.character_tail");
        helpEntry(src, "character info", "commands.powerlevelling.help.character_info");
        helpEntry(src, "character list", "commands.powerlevelling.help.character_list");
        helpEntry(src, "character set race <id>", "commands.powerlevelling.help.character_set_race");
        helpEntry(src, "character set nature <id>", "commands.powerlevelling.help.character_set_nature");
        helpEntry(src, "character set hair <style> [#RRGGBB]", "commands.powerlevelling.help.character_set_hair");
        helpEntry(src, "character reset", "commands.powerlevelling.help.character_reset");
        helpEntry(src, "character balance", "commands.powerlevelling.help.character_balance");
        return 1;
    }

    private static void helpGroup(CommandSourceStack src, String key) {
        src.sendSuccess(() -> title(key), false);
    }

    private static void helpEntry(CommandSourceStack src, String usage, String descKey) {
        src.sendSuccess(() -> Component.literal("  /powerlevelling " + usage).withStyle(ChatFormatting.GOLD)
                .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.translatable(descKey).withStyle(ChatFormatting.GRAY)), false);
    }

    // ---- admin / progression ------------------------------------------------------------------------------------

    private static int setKi(CommandSourceStack src, float amount) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        CombatResources r = player.getData(ModAttachments.RESOURCES);
        float maxKi = StatEffects.maxKi(player); // stats + race/nature
        r.ki = Mth.clamp(amount, 0f, maxKi);
        ModNetwork.syncResources(player);
        int shownKi = Math.round(r.ki);
        int shownMax = Math.round(maxKi);
        src.sendSuccess(() -> ok("commands.powerlevelling.setki", shownKi, shownMax), true);
        return 1;
    }

    private static int setLevel(CommandSourceStack src, String target, int level) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        KiProgression progression = player.getData(ModAttachments.KI_PROGRESSION);

        if (CHARACTER.equals(target)) {
            int cap = ServerConfig.LEVEL_CAP.get();
            if (level < 1 || level > cap) {
                fail(src, "commands.powerlevelling.setlevel.out_of_range", target, 1, cap);
                return 0;
            }
            int before = progression.level;
            progression.level = level;
            progression.exp = 0;
            ModNetwork.syncProgression(player);
            src.sendSuccess(() -> ok("commands.powerlevelling.setlevel.character", level, before), true);
            return 1;
        }

        Track track = Track.byId(target);
        if (track == null) {
            fail(src, "commands.powerlevelling.setlevel.unknown_track", target);
            return 0;
        }
        if (level > KiProgression.MAX_LEVEL) {
            fail(src, "commands.powerlevelling.setlevel.out_of_range", target, 0, KiProgression.MAX_LEVEL);
            return 0;
        }
        MasteryTrack t = progression.track(track);
        int before = t.level;
        t.level = level;
        t.xp = 0;
        // Admin changes never pay milestone points: mark the milestones up to this level as already paid.
        t.milestones = Math.max(t.milestones, level / KiProgression.MASTERY_MILESTONE_EVERY);

        ModNetwork.syncProgression(player);
        Component trackName = Component.translatable("track.powerlevelling." + track.id);
        src.sendSuccess(() -> ok("commands.powerlevelling.setlevel", trackName, level, before), true);
        return 1;
    }

    private static int addExp(CommandSourceStack src, int amount) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        KiProgression before = player.getData(ModAttachments.KI_PROGRESSION);
        int oldPoints = before.unspentPoints;
        Progression.addExp(player, amount, Progression.ExpSource.COMMAND);
        KiProgression p = player.getData(ModAttachments.KI_PROGRESSION);
        int newLevel = p.level;
        int gainedPoints = p.unspentPoints - oldPoints;
        src.sendSuccess(() -> ok("commands.powerlevelling.addexp", amount, newLevel, gainedPoints), true);
        return 1;
    }

    private static int setPoints(CommandSourceStack src, int points) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        player.getData(ModAttachments.KI_PROGRESSION).unspentPoints = points;
        ModNetwork.syncProgression(player);
        src.sendSuccess(() -> ok("commands.powerlevelling.setpoints", points), true);
        return 1;
    }

    private static int setStat(CommandSourceStack src, String statId, int value) throws CommandSyntaxException {
        Stat stat = Stat.byId(statId);
        if (stat == null) {
            fail(src, "commands.powerlevelling.setstat.unknown_stat", statId);
            return 0;
        }
        int cap = ServerConfig.STAT_CAP.get();
        int clamped = Math.min(value, cap);
        ServerPlayer player = src.getPlayerOrException();
        player.getData(ModAttachments.KI_PROGRESSION).setStat(stat, clamped);
        Progression.onStatsChanged(player); // attributes, resource clamps, sync
        Component statName = Component.translatable("stat.powerlevelling." + stat.id);
        src.sendSuccess(() -> ok("commands.powerlevelling.setstat", statName, clamped), true);
        return 1;
    }

    private static int loadoutSet(CommandSourceStack src, int slot, ResourceLocation attackId) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        KiAttack attack = KiAttackRegistry.get(src.registryAccess(), attackId);
        if (attack == null) {
            fail(src, "commands.powerlevelling.loadout.unknown", attackId.toString());
            return 0;
        }
        player.getData(ModAttachments.KI_PROGRESSION).setLoadout(slot - 1, attackId);
        ModNetwork.syncProgression(player);
        KiSkills.syncCooldowns(player);
        src.sendSuccess(() -> ok("commands.powerlevelling.loadout.set", slot, attackId.toString()), true);
        return 1;
    }

    private static int loadoutClear(CommandSourceStack src, int slot) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        player.getData(ModAttachments.KI_PROGRESSION).setLoadout(slot - 1, null);
        ModNetwork.syncProgression(player);
        KiSkills.syncCooldowns(player);
        src.sendSuccess(() -> ok("commands.powerlevelling.loadout.clear", slot), true);
        return 1;
    }

    private static int info(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        KiProgression p = player.getData(ModAttachments.KI_PROGRESSION);
        CombatResources r = player.getData(ModAttachments.RESOURCES);
        long zenkaiTicks = Math.max(0L, p.zenkaiCooldownUntil - player.level().getGameTime());
        int zenkaiSecs = (int) ((zenkaiTicks + 19) / 20);

        src.sendSuccess(() -> title("commands.powerlevelling.info.header", player.getDisplayName()), false);
        src.sendSuccess(() -> line("commands.powerlevelling.info.level",
                p.level, p.exp, KiProgression.expToNext(p.level), p.powerLevel()), false);
        src.sendSuccess(() -> line("commands.powerlevelling.info.race", p.race.toString(), p.form.toString()), false);
        src.sendSuccess(() -> line("commands.powerlevelling.info.points",
                p.unspentPoints, p.spentPoints(), p.pointsFromMastery()), false);
        src.sendSuccess(() -> line("commands.powerlevelling.info.stats",
                p.stat(Stat.STRENGTH), p.stat(Stat.KI_POWER), p.stat(Stat.KI_CAPACITY),
                p.stat(Stat.VITALITY), p.stat(Stat.DEFENSE), p.stat(Stat.SPEED)), false);
        src.sendSuccess(() -> line("commands.powerlevelling.info.tracks",
                p.level(Track.MARTIAL), p.level(Track.KI_CONTROL), p.level(Track.TOUGHNESS), p.level(Track.MOBILITY)), false);
        src.sendSuccess(() -> line("commands.powerlevelling.info.resources",
                Math.round(r.ki), Math.round(StatEffects.maxKi(player)), Math.round(r.stamina), Math.round(StatEffects.maxStamina(player)),
                Math.round(r.endurance), String.valueOf(r.kiBreak)), false);
        src.sendSuccess(() -> line("commands.powerlevelling.info.health",
                Math.round(player.getHealth()), Math.round(player.getMaxHealth())), false);
        src.sendSuccess(() -> zenkaiSecs <= 0
                ? line("commands.powerlevelling.info.zenkai_ready")
                : line("commands.powerlevelling.info.zenkai_cooldown", zenkaiSecs), false);
        return 1;
    }

    // ---- character (Addendum B) ---------------------------------------------------------------------------------

    private static int characterInfo(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        CharacterData d = CharacterService.get(player);
        Race race = CharacterRegistries.getRace(src.registryAccess(), d.race);
        KiNature nature = CharacterRegistries.getNature(src.registryAccess(), d.nature);
        Appearance a = d.appearance;

        String auraText = a.auraColor() < 0 ? "match ki" : hex(a.auraColor());
        String skinText = a.skinColor() < 0 ? "vanilla" : hex(a.skinColor());

        src.sendSuccess(() -> title("commands.powerlevelling.character.info.header", player.getDisplayName()), false);
        src.sendSuccess(() -> line("commands.powerlevelling.character.info.state",
                String.valueOf(d.created), String.valueOf(d.freeRecreate)), false);
        src.sendSuccess(() -> line("commands.powerlevelling.character.info.race", d.race.toString(),
                race == null ? Component.translatable("commands.powerlevelling.character.missing") : Component.translatable(race.name())), false);
        src.sendSuccess(() -> line("commands.powerlevelling.character.info.nature", d.nature.toString(),
                nature == null ? Component.translatable("commands.powerlevelling.character.missing") : Component.translatable(nature.name())), false);
        src.sendSuccess(() -> line("commands.powerlevelling.character.info.hair",
                a.hairStyle().toString(), hex(a.hairColor()), hex(a.eyeColor()), a.eyeStyle()), false);
        src.sendSuccess(() -> line("commands.powerlevelling.character.info.ki",
                hex(a.kiColor()), auraText, skinText), false);
        src.sendSuccess(() -> line("commands.powerlevelling.character.info.body",
                String.format("%.2f", a.height()), String.format("%.2f", a.build()), a.parts().size()), false);
        src.sendSuccess(() -> line("commands.powerlevelling.character.info.tail",
                d.tail.getSerializedName(), String.valueOf(d.tailWrapped), d.tailRegrowAt), false);

        StringBuilder mods = new StringBuilder();
        for (Channel c : Channel.values()) {
            if (d.mods.isNeutral(c)) continue;
            if (mods.length() > 0) mods.append(", ");
            mods.append(c.id);
            if (d.mods.mult(c) != 1f) mods.append(String.format(" x%.3f", d.mods.mult(c)));
            if (d.mods.flat(c) != 0f) mods.append(String.format(" %+.3f", d.mods.flat(c)));
        }
        StringBuilder scaling = new StringBuilder();
        for (Stat st : Stat.values()) {
            if (d.mods.scaling(st) == 1f) continue;
            if (scaling.length() > 0) scaling.append(", ");
            scaling.append(st.id).append(String.format(" x%.3f", d.mods.scaling(st)));
        }
        String modsText = mods.length() == 0 ? "none" : mods.toString();
        String scalingText = scaling.length() == 0 ? "none" : scaling.toString();
        src.sendSuccess(() -> line("commands.powerlevelling.character.info.mods", modsText), false);
        src.sendSuccess(() -> line("commands.powerlevelling.character.info.scaling", scalingText), false);

        // Effective derived values (stats + race + nature), straight from StatEffects.
        KiProgression p = player.getData(ModAttachments.KI_PROGRESSION);
        CharacterMods m = d.mods;
        String maxKi = String.valueOf(Math.round(StatEffects.maxKi(p, m)));
        String maxHp = String.format(Locale.ROOT, "%.1f", StatEffects.maxHp(p, m, ServerConfig.BASE_BONUS_HEALTH.get()));
        String stamina = String.valueOf(Math.round(StatEffects.maxStamina(p, m)));
        String melee = mult(StatEffects.meleeMultiplier(p, m));
        String kiDmg = mult(StatEffects.kiDamageMultiplier(p, m));
        String taken = mult(StatEffects.damageTakenMultiplier(p, m));
        String speed = String.format(Locale.ROOT, "%+.1f", StatEffects.moveSpeedBonus(p, m) * 100.0);
        String kiRegen = mult(StatEffects.kiRegenMult(m));
        String kiCharge = mult(StatEffects.kiChargeRateMult(m));
        String kiCost = mult(StatEffects.kiCostMult(m));
        String stRegen = mult(StatEffects.staminaRegenMult(p, m));
        String exp = mult(StatEffects.expGainMult(m));
        String mastery = mult(StatEffects.masteryXpMult(m));
        src.sendSuccess(() -> line("commands.powerlevelling.character.info.derived1", maxKi, maxHp, stamina), false);
        src.sendSuccess(() -> line("commands.powerlevelling.character.info.derived2", melee, kiDmg, taken, speed), false);
        src.sendSuccess(() -> line("commands.powerlevelling.character.info.derived3",
                kiRegen, kiCharge, kiCost, stRegen, exp, mastery), false);
        return 1;
    }

    private static String mult(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    /** B.3.2 sanity check: damage x effective HP of each enabled nature relative to Balanced at 0/100/200 points. */
    private static int characterBalance(CommandSourceStack src) {
        List<CharacterBalance.Row> rows = CharacterBalance.compute(src.registryAccess(), ServerConfig.BASE_BONUS_HEALTH.get());
        if (rows.isEmpty()) {
            fail(src, "commands.powerlevelling.character.balance.no_reference", CharacterData.DEFAULT_NATURE.toString());
            return 0;
        }
        src.sendSuccess(() -> title("commands.powerlevelling.character.balance.header",
                String.format(Locale.ROOT, "%.0f", CharacterBalance.LIMIT * 100)), false);
        src.sendSuccess(() -> line("commands.powerlevelling.character.balance.note"), false);
        int outside = 0;
        for (CharacterBalance.Row r : rows) {
            if (!r.within()) outside++;
            Component status = Component.translatable(r.within() ? "commands.powerlevelling.character.balance.ok"
                    : "commands.powerlevelling.character.balance.out").withStyle(r.within() ? ChatFormatting.GREEN : ChatFormatting.RED);
            Component style = Component.translatable(r.kiStyle() ? "commands.powerlevelling.character.balance.ki"
                    : "commands.powerlevelling.character.balance.melee");
            src.sendSuccess(() -> line("commands.powerlevelling.character.balance.row", r.nature().getPath(), r.points(),
                    mult(r.relDamage()), style, mult(r.relEhp()), mult(r.relTotal()), status), false);
        }
        int total = outside;
        src.sendSuccess(() -> total == 0
                ? ok("commands.powerlevelling.character.balance.all_ok")
                : Component.translatable("commands.powerlevelling.character.balance.some_out", v(total)).withStyle(ChatFormatting.RED), false);
        return 1;
    }

    private static String hex(int color) {
        return String.format("#%06X", color & 0xFFFFFF);
    }

    private static int characterList(CommandSourceStack src) {
        RegistryAccess access = src.registryAccess();
        src.sendSuccess(() -> title("commands.powerlevelling.character.list.races"), false);
        for (var e : CharacterRegistries.allRaces(access)) {
            boolean playable = CharacterService.isPlayable(access, e.id());
            Component state = Component.translatable(playable ? "commands.powerlevelling.character.playable"
                    : "commands.powerlevelling.character.locked").withStyle(playable ? ChatFormatting.GREEN : ChatFormatting.RED);
            String hidden = e.value().hidden() ? " (hidden)" : "";
            src.sendSuccess(() -> line("commands.powerlevelling.character.list.race_line", e.id().toString(), state, hidden), false);
        }
        src.sendSuccess(() -> title("commands.powerlevelling.character.list.natures"), false);
        for (var e : CharacterRegistries.allNatures(access)) {
            Component state = Component.translatable(e.value().enabled() ? "commands.powerlevelling.character.enabled"
                    : "commands.powerlevelling.character.disabled").withStyle(e.value().enabled() ? ChatFormatting.GREEN : ChatFormatting.RED);
            src.sendSuccess(() -> line("commands.powerlevelling.character.list.nature_line", e.id().toString(), state), false);
        }
        src.sendSuccess(() -> title("commands.powerlevelling.character.list.parts"), false);
        Map<String, List<String>> bySlot = new TreeMap<>();
        for (var e : CharacterRegistries.allParts(access)) {
            bySlot.computeIfAbsent(e.value().slot(), k -> new ArrayList<>()).add(e.id().toString());
        }
        if (bySlot.isEmpty()) {
            src.sendSuccess(() -> line("commands.powerlevelling.character.list.none"), false);
        }
        bySlot.forEach((slot, ids) -> src.sendSuccess(() -> line(
                "commands.powerlevelling.character.list.part_line", slot, String.join(", ", ids)), false));
        return 1;
    }

    /** Player command: asks this client to open the character screen (the client decides whether creating is allowed). */
    private static int characterOpen(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        PacketDistributor.sendToPlayer(player, new OpenCharacterScreenPayload());
        return 1;
    }

    /** Player command: wrap / unwrap the tail around the waist. */
    private static int characterTail(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        switch (CharacterService.toggleTail(player)) {
            case WRAPPED -> src.sendSuccess(() -> ok("commands.powerlevelling.character.tail.wrapped"), false);
            case UNWRAPPED -> src.sendSuccess(() -> ok("commands.powerlevelling.character.tail.unwrapped"), false);
            case NO_TAIL -> {
                fail(src, "commands.powerlevelling.character.tail.none");
                return 0;
            }
            case RATE_LIMITED -> {
                fail(src, "commands.powerlevelling.character.tail.slow");
                return 0;
            }
        }
        return 1;
    }

    private static int characterReset(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        CharacterService.reset(player);
        src.sendSuccess(() -> ok("commands.powerlevelling.character.reset"), true);
        return 1;
    }

    private static int characterSetRace(CommandSourceStack src, ResourceLocation id) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        Race race = CharacterRegistries.getRace(src.registryAccess(), id);
        if (race == null) {
            fail(src, "commands.powerlevelling.character.unknown_race", id.toString());
            return 0;
        }
        CharacterService.setRace(player, id);
        Component raceName = Component.translatable(race.name());
        src.sendSuccess(() -> ok("commands.powerlevelling.character.set_race", raceName, id.toString()), true);
        return 1;
    }

    private static int characterSetNature(CommandSourceStack src, ResourceLocation id) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        KiNature nature = CharacterRegistries.getNature(src.registryAccess(), id);
        if (nature == null) {
            fail(src, "commands.powerlevelling.character.unknown_nature", id.toString());
            return 0;
        }
        CharacterService.setNature(player, id);
        Component natureName = Component.translatable(nature.name());
        src.sendSuccess(() -> ok("commands.powerlevelling.character.set_nature", natureName, id.toString()), true);
        return 1;
    }

    /** Hair style ids offered to /pl character set hair: "none" plus every cosmetic_part with slot hair. */
    private static List<ResourceLocation> hairStyleIds(RegistryAccess access) {
        List<ResourceLocation> ids = new ArrayList<>();
        ids.add(Appearance.NO_HAIR);
        for (var e : CharacterRegistries.allParts(access)) {
            if ("hair".equals(e.value().slot())) ids.add(e.id());
        }
        return ids;
    }

    /**
     * Debug: set the hair style (and optionally the color, "#RRGGBB" or "RRGGBB") straight on the CharacterData and sync via
     * CharacterService.onChanged. A bare "flare" is read as powerlevelling:flare. Does not touch created / race rules.
     */
    private static int characterSetHair(CommandSourceStack src, ResourceLocation raw, String colorText) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        ResourceLocation id = raw;
        List<ResourceLocation> valid = hairStyleIds(src.registryAccess());
        if (!valid.contains(id) && "minecraft".equals(id.getNamespace())) {
            id = ResourceLocation.fromNamespaceAndPath(PowerLevelling.MODID, id.getPath());
        }
        if (!valid.contains(id)) {
            fail(src, "commands.powerlevelling.character.unknown_hair", raw.toString());
            return 0;
        }
        CharacterData d = CharacterService.get(player);
        Appearance a = d.appearance;
        int color = a.hairColor();
        if (colorText != null) {
            String t = colorText.trim();
            if (t.startsWith("#")) t = t.substring(1);
            try {
                if (t.length() != 6) throw new NumberFormatException();
                color = Integer.parseInt(t, 16);
            } catch (NumberFormatException ex) {
                fail(src, "commands.powerlevelling.character.bad_color", colorText.trim());
                return 0;
            }
        }
        d.appearance = new Appearance(id, color, a.eyeColor(), a.kiColor(), a.auraColor(), a.skinColor(),
                a.height(), a.build(), a.parts(), a.eyeStyle());
        CharacterService.onChanged(player);
        ResourceLocation shown = id;
        String shownColor = hex(color);
        src.sendSuccess(() -> ok("commands.powerlevelling.character.set_hair", shown.toString(), shownColor), true);
        if (!d.created) src.sendSuccess(() -> line("commands.powerlevelling.character.set_hair.not_created"), false);
        return 1;
    }
}
