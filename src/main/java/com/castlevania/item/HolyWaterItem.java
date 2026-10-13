package com.castlevania.item;

import com.castlevania.entity.HolyWaterEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Holy Water: right click to throw it. It flies in an arc and breaks on impact,
 * setting fire to the block it hits and damaging nearby enemies.
 */
public class HolyWaterItem extends Item {
    /** Throw speed in blocks per tick. */
    public static final float SHOOT_POWER = 0.8F;

    public HolyWaterItem(final Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);

        level.playSound(
            null,
            player.getX(), player.getY(), player.getZ(),
            SoundEvents.SPLASH_POTION_THROW,
            SoundSource.NEUTRAL,
            0.5F,
            0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F)
        );

        if (level instanceof ServerLevel serverLevel) {
            // Copy the stack for the projectile BEFORE shrinking (so the entity gets the item)
            final ItemStack projectileStack = stack.copy();
            projectileStack.setCount(1);

            // Consume one from the player's stack
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }

            Projectile.spawnProjectileFromRotation(
                HolyWaterEntity::new, serverLevel, projectileStack, player, 0.0F, SHOOT_POWER, 0.0F
            );
        }

        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResult.SUCCESS;
    }
}