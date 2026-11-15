package com.powerlevelling.item;

import com.powerlevelling.client.ClientScreens;

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

/** Respec item. Right-click opens a confirm screen on the client; the server acts on RespecConfirmPayload. */
public class RebirthOrbItem extends Item {
    public RebirthOrbItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // ClientScreens is only touched on the logical client, so a dedicated server never loads it.
        if (level.isClientSide()) ClientScreens.openRebirthConfirm();
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.powerlevelling.rebirth_orb.desc").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.powerlevelling.rebirth_orb.desc2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.powerlevelling.rebirth_orb.lore").withStyle(ChatFormatting.ITALIC, ChatFormatting.LIGHT_PURPLE));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true; // permanent enchant glint
    }
}
