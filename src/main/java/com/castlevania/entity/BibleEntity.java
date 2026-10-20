package com.castlevania.entity;

import com.castlevania.CastlevaniaMod;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.ArrayList;

/**
 * The Bible: a thrown weapon that spins around the player for 5 seconds,
 * then disappears. Enemies touched take 10 damage; the weapon keeps its path.
 */
public class BibleEntity extends ThrowableItemProjectile {
    /** How long the bible spins around the player (5 seconds = 100 ticks). */
    public static final int SPIN_DURATION_TICKS = 100;
    /** Damage dealt to every enemy touched by the bible. */
    public static final float DAMAGE = 10.0F;
    /** Spin speed in degrees per tick. */
    public static final float SPIN_SPEED = 10.0F; // 36 degrees per tick = full circle in 10 ticks

    /** Current rotation for spinning effect. */
    public float spinRotation;

    private int spinTicks;
    private Vec3 direction;
    /** Flag: true once the outward flight phase has set the initial direction. */
    private boolean directionSet;

    public BibleEntity(final EntityType<BibleEntity> type, final Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    public BibleEntity(final Level level, final LivingEntity owner, final ItemStack itemStack) {
        super(CastlevaniaMod.BIBLE_ENTITY.get(), owner, level, itemStack);
        this.setNoGravity(true);
    }

    @Override
    protected Item getDefaultItem() {
        return CastlevaniaMod.BIBLE_ITEM.get();
    }

    @Override
    public void tick() {
        super.tick();

        // Update spin rotation every tick (both client and server)
        this.spinRotation += SPIN_SPEED;
        if (this.spinRotation >= 360.0F) {
            this.spinRotation -= 360.0F;
        }
        // Set entity yaw rotation for spinning (clockwise = increasing yaw)
        this.setYRot(this.spinRotation);
        // Keep pitch at 0 for flat horizontal flight
        this.setXRot(0.0F);

        if (!this.isAlive()) {
            return;
        }

        // Failsafe: after max lifetime, drop the item
        if (this.tickCount > 200) { // safety net
            this.dropBible(this.position());
            return;
        }

        // On the first server tick, capture the initial flight direction
        if (this.direction == null && !this.directionSet) {
            this.startFlight();
        }

        // Handle behavior based on current phase
        if (this.tickCount <= SPIN_DURATION_TICKS) {
            // Spinning phase: keep spinning around the owner
            this.tickSpinning();
        } else {
            // Duration expired: discard
            this.discard();
        }
    }

    /** Captures the initial flight direction on the first server tick. */
    private void startFlight() {
        final Vec3 motion = this.getDeltaMovement();
        this.direction = motion.lengthSqr() < 1.0E-4D
            ? Vec3.directionFromRotation(this.getXRot(), this.getYRot())
            : motion.normalize();
        this.setDeltaMovement(Vec3.ZERO);
        this.directionSet = true;
    }

    /** Spins the bible around the owner for 5 seconds. */
    private void tickSpinning() {
        final Entity owner = this.getOwner();
        if (owner != null && owner.isAlive()) {
            // Calculate a position in a circle around the owner
            final double angle = this.tickCount * Math.toRadians(SPIN_SPEED);
            final double radius = 1.5D; // distance from player
            final double ox = owner.getX();
            final double oy = owner.getY() + owner.getBbHeight() * 0.5D;
            final double oz = owner.getZ();
            final double nx = ox + Math.cos(angle) * radius;
            final double ny = oy;
            final double nz = oz + Math.sin(angle) * radius;

            // Move towards the circular position (smooth following)
            final Vec3 target = new Vec3(nx, ny, nz);
            final Vec3 from = this.position();
            final Vec3 delta = target.subtract(from);
            final double distance = delta.length();

            // If we're far enough, move; otherwise just set position
            if (distance > 0.1D) {
                final Vec3 step = delta.scale(Math.min(0.3D, distance));
                this.setDeltaMovement(step);
                this.setPos(from.add(step));
            } else {
                this.setPos(target);
            }

            // Damage any enemies in the bible's bounding box
            // Collect entities first to avoid ConcurrentModificationException
            final List<Entity> entities = new ArrayList<>(this.level().getEntities(this, this.getBoundingBox().inflate(0.5D)));
            for (final Entity entity : entities) {
                if (entity == owner || !entity.isAlive() || entity.isSpectator()) {
                    continue;
                }
                if (entity instanceof LivingEntity) {
                    this.hurtAt(entity);
                }
            }
        } else {
            // No owner: just spin in place
            this.spinRotation += SPIN_SPEED;
            if (this.spinRotation >= 360.0F) {
                this.spinRotation -= 360.0F;
            }
            this.setYRot(this.spinRotation);
        }
    }

    /** Damages an entity hit by the bible, but the bible keeps its path. */
    private void hurtAt(final Entity target) {
        if (!(target instanceof LivingEntity)) {
            return;
        }

        final LivingEntity livingTarget = (LivingEntity) target;
        // Si la entidad ya está muerta o moribunda, no hacer nada
        if (livingTarget.isDeadOrDying()) {
            return;
        }

        final Level level = this.level();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        // Causar daño
        final boolean hurt = livingTarget.hurtServer(serverLevel, this.damageSources().thrown(this, this.getOwner()), DAMAGE);
        if (hurt) {
            // Re-verificar que la entidad aún esté viva después del daño
            if (livingTarget.isDeadOrDying()) {
                return;
            }

            // Reproducir sonido
            this.level().playSound(
                null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.6F, 1.1F
            );

            // Enviar partículas solo si la entidad sigue viva y tiene posiciones válidas
            if (hurt) {
                // Re-verificar que la entidad aún esté viva después del daño
                if (livingTarget.isDeadOrDying()) {
                    return;
                }
                // Reproducir sonido
                this.level().playSound(
                    null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.6F, 1.1F
                );
                // Enviar partículas
                serverLevel.sendParticles(
                    ParticleTypes.CRIT,
                    livingTarget.getX(), livingTarget.getY() + livingTarget.getBbHeight() * 0.5D, livingTarget.getZ(),
                    8, 0.2D, 0.2D, 0.2D, 0.05D
                );
            }
        }
    }

    /** Failsafe: the bible always drops as an item when removed. */
    private void dropBible(final Vec3 position) {
        final ItemStack stack = this.getItem().copy();
        final ItemEntity item = new ItemEntity(this.level(), position.x(), position.y(), position.z(), stack);
        item.setDefaultPickUpDelay();
        this.level().addFreshEntity(item);
        this.discard();
    }

    @Override
    protected void addAdditionalSaveData(final net.minecraft.world.level.storage.ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("SpinTicks", this.spinTicks);
        output.store("Direction", net.minecraft.world.phys.Vec3.CODEC, this.direction);
    }

    @Override
    protected void readAdditionalSaveData(final net.minecraft.world.level.storage.ValueInput input) {
        super.readAdditionalSaveData(input);
        this.spinTicks = input.getIntOr("SpinTicks", 0);
        this.direction = input.read("Direction", net.minecraft.world.phys.Vec3.CODEC).orElse(null);
        if (this.direction == null) {
            this.direction = Vec3.ZERO;
        }
    }
}