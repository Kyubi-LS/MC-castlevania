package com.castlevania.entity;

import com.castlevania.CastlevaniaMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Holy Water projectile: flies in an arc like a potion, breaks on impact,
 * sets fire to the block it hits and damages nearby enemies.
 */
public class HolyWaterEntity extends ThrowableItemProjectile {
    /** Maximum lifetime in ticks (3 seconds). */
    public static final int MAX_LIFETIME_TICKS = 60;
    /** Damage dealt to enemies in the splash radius. */
    public static final float SPLASH_DAMAGE = 4.0F;
    /** Splash radius in blocks. */
    public static final double SPLASH_RADIUS = 2.0D;
    /** Fire duration in seconds when a block is ignited. */
    public static final int FIRE_DURATION = 8;

    public HolyWaterEntity(final EntityType<HolyWaterEntity> type, final Level level) {
        super(type, level);
    }

    public HolyWaterEntity(final Level level, final LivingEntity owner, final ItemStack itemStack) {
        super(CastlevaniaMod.HOLY_WATER_ENTITY.get(), owner, level, itemStack);
    }

    @Override
    protected Item getDefaultItem() {
        return CastlevaniaMod.HOLY_WATER_ITEM.get();
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide() || !this.isAlive()) {
            return;
        }

        if (this.tickCount > MAX_LIFETIME_TICKS) {
            this.breakBottle(this.position());
            return;
        }
    }

    @Override
    protected void onHitBlock(final BlockHitResult result) {
        super.onHitBlock(result);
        if (!this.level().isClientSide()) {
            this.breakBottle(result.getLocation());
        }
    }

    @Override
    protected void onHitEntity(final EntityHitResult result) {
        super.onHitEntity(result);
        if (!this.level().isClientSide()) {
            this.breakBottle(result.getLocation());
        }
    }

    /** Breaks the bottle, igniting the block and damaging nearby entities. */
    private void breakBottle(final Vec3 position) {
        final Level level = this.level();
        final BlockPos blockPos = BlockPos.containing(position);

        // Set fire on the block we hit (and the one above for good measure)
        final BlockState hitState = level.getBlockState(blockPos);
        if (level.isEmptyBlock(blockPos) || hitState.canBeReplaced()) {
            // If air/replaceable, set fire on the block below
            final BlockPos below = blockPos.below();
            if (!level.getBlockState(below).isAir() && level.getBlockState(below).getBlock() != Blocks.FIRE) {
                level.setBlockAndUpdate(below, Blocks.FIRE.defaultBlockState());
                level.gameEvent(GameEvent.BLOCK_CHANGE, below, GameEvent.Context.of(this));
            }
        } else {
            // Set fire on the hit block
            if (level.getBlockState(blockPos).getBlock() != Blocks.FIRE) {
                level.setBlockAndUpdate(blockPos, Blocks.FIRE.defaultBlockState());
                level.gameEvent(GameEvent.BLOCK_CHANGE, blockPos, GameEvent.Context.of(this));
            }
            // Also set fire above
            final BlockPos above = blockPos.above();
            if (level.isEmptyBlock(above)) {
                level.setBlockAndUpdate(above, Blocks.FIRE.defaultBlockState());
                level.gameEvent(GameEvent.BLOCK_CHANGE, above, GameEvent.Context.of(this));
            }
        }

        // Damage nearby living entities (splash damage)
        for (final Entity entity : level.getEntities(this, this.getBoundingBox().inflate(SPLASH_RADIUS))) {
            if (entity instanceof LivingEntity living && entity != this.getOwner() && entity.isAlive()) {
                if (level instanceof ServerLevel serverLevel) {
                    living.hurtServer(serverLevel, this.damageSources().thrown(this, this.getOwner()), SPLASH_DAMAGE);
                }
            }
        }

        // Visual/sound effects
        level.playSound(null, position.x(), position.y(), position.z(),
            SoundEvents.GLASS_BREAK, SoundSource.NEUTRAL, 0.8F, 1.2F);
        level.playSound(null, position.x(), position.y(), position.z(),
            SoundEvents.FIRE_AMBIENT, SoundSource.NEUTRAL, 0.6F, 1.5F);

        // Particles: smoke and flame
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                position.x(), position.y(), position.z(), 10, 0.3, 0.3, 0.3, 0.05);
            serverLevel.sendParticles(ParticleTypes.FLAME,
                position.x(), position.y(), position.z(), 15, 0.4, 0.4, 0.4, 0.1);
        }

        // Drop the empty bottle? No, holy water is consumed entirely
        this.discard();
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
    }
}