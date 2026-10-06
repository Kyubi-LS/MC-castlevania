package com.example.examplemod.item;

import com.example.examplemod.entity.RedDiamondSwordProjectile;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.Level;

public class RedDiamondSwordItem extends SwordItem {

    public RedDiamondSwordItem(Tier tier, int attackDamageModifier, float attackSpeedModifier, Item.Properties properties) {
        super(tier, attackDamageModifier, attackSpeedModifier, properties);
    }

    public RedDiamondSwordItem() {
        this(Tiers.DIAMOND, 3, -2.4f, new Item.Properties()
            .setId(com.example.examplemod.ExampleMod.ITEMS.key("red_diamond_sword"))
            .durability(1561)
            .fireResistant());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        
        if (!level.isClientSide()) {
            // Create and throw the projectile
            RedDiamondSwordProjectile projectile = new RedDiamondSwordProjectile(level, player, itemStack);
            projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 1.0F);
            level.addFreshEntity(projectile);
            
            // Play sound
            level.playSound(null, player.getX(), player.getY(), player.getZ(), 
                SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.0F, 1.0F);
            
            // Damage the item
            itemStack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
        }
        
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide());
    }
}