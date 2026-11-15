package com.powerlevelling.item;

import com.powerlevelling.client.ClientScreens;
import com.powerlevelling.network.CharacterSubmitPayload.Mode;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Character change item (NATURE). Right-click opens the character screen in that edit mode; the server consumes it on a successful submit. */
public class AttunementCrystalItem extends Item {
    public AttunementCrystalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // ClientScreens (and its "Create your character first" hint when not created) is only touched on the logical client.
        if (level.isClientSide()) ClientScreens.openCharacter(Mode.NATURE);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.powerlevelling.attunement_crystal.desc").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.powerlevelling.attunement_crystal.desc2").withStyle(ChatFormatting.GRAY));
    }
}
