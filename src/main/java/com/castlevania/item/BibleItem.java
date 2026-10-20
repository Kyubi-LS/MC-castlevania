package com.castlevania.item;

import com.castlevania.entity.BibleEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The Bible: right click to throw it. It flies outward and then spins around
 * the player for 5 seconds, damaging enemies on contact, and then disappears.
 */
public class BibleItem extends Item {
    /** Throw speed in blocks per tick. */
    public static final float SHOOT_POWER = 1.0F;

    public BibleItem(final Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult use(final Level level, final Player player, final InteractionHand hand) {
        final ItemStack stack = player.getItemInHand(hand);

        level.playSound(
            null,
            player.getX(), player.getY(), player.getZ(),
            SoundEvents.SNOWBALL_THROW,
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

            BibleEntity.spawnProjectileFromRotation(
                BibleEntity::new, serverLevel, projectileStack, player, 0.0F, SHOOT_POWER, 0.0F
            );
        }

        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResult.SUCCESS;
    }
}