package com.castlevania.entity;

import com.castlevania.CastlevaniaMod;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Cross: a thrown weapon that flies exactly {@value #MAX_TRAVEL_DISTANCE} blocks forward,
 * stays motionless for {@value #WAIT_TICKS} ticks (2 seconds) and then flies back to its
 * owner, landing in the owner's inventory.
 *
 * <p>Every living entity hit on the way out or on the way back takes {@value #DAMAGE} damage,
 * but the cross never changes its path because of a hit: it simply keeps going.</p>
 *
 * <p>All game logic runs on the server; clients only interpolate the synced position.</p>
 */
public class CrossEntity extends ThrowableItemProjectile {
    /** How far the cross travels forward before stopping. */
    public static final double MAX_TRAVEL_DISTANCE = 10.0D;
    /** How long the cross stays motionless at the end of the outward flight (2 seconds). */
    public static final int WAIT_TICKS = 40;
    /** Outward speed in blocks per tick (24 blocks per second). */
    public static final double OUTGOING_SPEED = 1.2D;
    /** Damage dealt to every enemy touched by the cross. */
    public static final float DAMAGE = 6.0F;
    /** Failsafe lifetime: after this many ticks the cross simply drops on the ground. */
    public static final int MAX_LIFETIME_TICKS = 1200;

    /** Distance to the owner at which the cross is considered "caught". */
    private static final double CATCH_DISTANCE = 1.0D;
    /** Ticks the cross looks for a missing owner before dropping itself. */
    private static final int OWNER_MISSING_TICKS = 100;

    private enum Phase {
        OUTGOING,
        WAITING,
        RETURNING
    }

    private Phase phase = Phase.OUTGOING;
    private Vec3 direction;
    private double traveled;
    private int waitTicks;
    private int returnTicks;
    private int ownerMissingTicks;

    public CrossEntity(final EntityType<CrossEntity> type, final Level level) {
        super(type, level);
    }

    public CrossEntity(final Level level, final LivingEntity owner, final ItemStack itemStack) {
        super(CastlevaniaMod.CROSS_ENTITY.get(), owner, level, itemStack);
    }

    @Override
    protected Item getDefaultItem() {
        return CastlevaniaMod.CROSS_ITEM.get();
    }

    @Override
    public void tick() {
        super.tick(); // projectile bookkeeping + base tick (portals, fire, fluids, ...)

        if (this.level().isClientSide() || !this.isAlive()) {
            return;
        }

        if (this.tickCount > MAX_LIFETIME_TICKS) {
            this.dropCross(this.position());
            return;
        }

        if (this.direction == null) {
            this.startFlight();
        }

        switch (this.phase) {
            case OUTGOING -> this.tickOutgoing();
            case WAITING -> this.tickWaiting();
            case RETURNING -> this.tickReturning();
        }
    }

    /**
     * Captures the flight direction on the first server tick (the shot happens right
     * after the entity is created, so the velocity is already set here).
     */
    private void startFlight() {
        final Vec3 motion = this.getDeltaMovement();
        this.direction = motion.lengthSqr() < 1.0E-4D
            ? Vec3.directionFromRotation(this.getXRot(), this.getYRot())
            : motion.normalize();
        this.setDeltaMovement(Vec3.ZERO);
    }

    /** Flies straight forward until {@value #MAX_TRAVEL_DISTANCE} blocks are covered or a block stops it. */
    private void tickOutgoing() {
        final double remaining = MAX_TRAVEL_DISTANCE - this.traveled;
        if (remaining <= 1.0E-4D) {
            this.beginWaiting();
            return;
        }

        final double stepLength = Math.min(OUTGOING_SPEED, remaining);
        final Vec3 step = this.direction.scale(stepLength);
        final Vec3 from = this.position();
        final Vec3 to = from.add(step);

        final BlockHitResult blockHit = this.level().clip(
            new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)
        );
        final EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
            this.level(), this, from, to, this.getBoundingBox().expandTowards(step).inflate(0.5D), this::canDamageEntity
        );

        final double blockDistance = blockHit.getType() == HitResult.Type.MISS
            ? Double.MAX_VALUE
            : blockHit.getLocation().distanceTo(from);
        final double entityDistance = entityHit == null
            ? Double.MAX_VALUE
            : entityHit.getLocation().distanceTo(from);

        // Hurt the enemy in front of us; a hit never changes the path of the cross.
        if (entityHit != null && entityDistance <= blockDistance) {
            this.hurtAt(entityHit.getEntity());
        }

        if (blockDistance <= stepLength) {
            // A block is in the way: stop right before it and start waiting.
            final double stopDistance = Math.max(0.0D, blockDistance - 0.05D);
            this.setPos(from.add(this.direction.scale(stopDistance)));
            this.traveled += stopDistance;
            this.beginWaiting();
            return;
        }

        this.setPos(to);
        this.traveled += stepLength;

        if (this.traveled >= MAX_TRAVEL_DISTANCE - 1.0E-4D) {
            this.beginWaiting();
        }
    }

    /** Waits motionless for {@value #WAIT_TICKS} ticks, then heads home. */
    private void tickWaiting() {
        // Something may walk into the hovering cross.
        for (final Entity entity : this.level().getEntities(this, this.getBoundingBox().inflate(0.2D), this::canDamageEntity)) {
            this.hurtAt(entity);
        }

        if (--this.waitTicks <= 0) {
            this.phase = Phase.RETURNING;
            this.returnTicks = 0;
        }
    }

    /** Flies back to the owner (through walls, so the weapon can never be lost) and lands in the inventory. */
    private void tickReturning() {
        this.returnTicks++;

        final Entity owner = this.getOwner();
        if (owner == null) {
            if (++this.ownerMissingTicks >= OWNER_MISSING_TICKS) {
                this.dropCross(this.position());
            }
            return;
        }
        if (owner.level() != this.level() || !owner.isAlive()) {
            this.dropCross(this.position());
            return;
        }
        this.ownerMissingTicks = 0;

        final Vec3 from = this.position();
        final Vec3 target = owner.position().add(0.0D, owner.getBbHeight() * 0.6D, 0.0D);
        final Vec3 delta = target.subtract(from);
        final double distance = delta.length();

        if (distance <= CATCH_DISTANCE) {
            this.giveToOwner(owner);
            return;
        }

        final double speed = Math.min(1.6D, Math.max(0.4D, distance * 0.35D));
        final Vec3 step = delta.scale(speed / distance);

        final EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
            this.level(), this, from, from.add(step), this.getBoundingBox().expandTowards(step).inflate(0.5D), this::canDamageEntity
        );
        if (entityHit != null) {
            this.hurtAt(entityHit.getEntity());
        }

        this.setDeltaMovement(step);
        this.setPos(from.add(step));
    }

    private void beginWaiting() {
        this.phase = Phase.WAITING;
        this.waitTicks = WAIT_TICKS;
        this.setDeltaMovement(Vec3.ZERO);
        this.level().playSound(
            null, this.getX(), this.getY(), this.getZ(),
            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.7F, 1.6F
        );
    }

    /** Puts the cross back into the owner's inventory (or drops it at their feet if the inventory is full). */
    private void giveToOwner(final Entity owner) {
        final ItemStack stack = this.getItem().copy();
        boolean added = false;

        if (owner instanceof Player player) {
            added = player.getInventory().add(stack);
        }
        if (!added && !stack.isEmpty()) {
            this.dropItemAt(owner.position().add(0.0D, 0.5D, 0.0D), stack);
        }

        this.level().playSound(
            null, owner.getX(), owner.getY(), owner.getZ(),
            SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6F, 1.4F
        );
        this.discard();
    }

    /** Failsafe: the cross never disappears silently, it always drops as an item. */
    private void dropCross(final Vec3 position) {
        final ItemStack stack = this.getItem().copy();
        if (!stack.isEmpty()) {
            this.dropItemAt(position, stack);
        }
        this.discard();
    }

    private void dropItemAt(final Vec3 position, final ItemStack stack) {
        final ItemEntity item = new ItemEntity(this.level(), position.x(), position.y(), position.z(), stack);
        item.setDefaultPickUpDelay();
        this.level().addFreshEntity(item);
    }

    /** Damage, sound and crit particles for a successful hit. The cross continues its path afterwards. */
    private void hurtAt(final Entity target) {
        if (!(target instanceof LivingEntity) || !(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        final boolean hurt = target.hurtServer(serverLevel, this.damageSources().thrown(this, this.getOwner()), DAMAGE);
        if (hurt) {
            this.level().playSound(
                null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.6F, 1.1F
            );
            serverLevel.sendParticles(
                ParticleTypes.CRIT,
                target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
                8, 0.2D, 0.2D, 0.2D, 0.05D
            );
        }
    }

    /** Living entities only, never the owner, never spectators or creative players. */
    private boolean canDamageEntity(final Entity entity) {
        if (!entity.isAlive() || entity.isSpectator() || this.ownedBy(entity)) {
            return false;
        }
        if (!(entity instanceof LivingEntity)) {
            return false;
        }
        return !(entity instanceof Player player) || !player.getAbilities().instabuild;
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Phase", this.phase.ordinal());
        output.putDouble("Traveled", this.traveled);
        output.putInt("WaitTicks", this.waitTicks);
        output.putInt("ReturnTicks", this.returnTicks);
        output.putInt("OwnerMissingTicks", this.ownerMissingTicks);
        if (this.direction != null) {
            output.store("Direction", Vec3.CODEC, this.direction);
        }
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        super.readAdditionalSaveData(input);
        final Phase[] phases = Phase.values();
        this.phase = phases[Math.floorMod(input.getIntOr("Phase", 0), phases.length)];
        this.traveled = input.getDoubleOr("Traveled", 0.0D);
        this.waitTicks = input.getIntOr("WaitTicks", 0);
        this.returnTicks = input.getIntOr("ReturnTicks", 0);
        this.ownerMissingTicks = input.getIntOr("OwnerMissingTicks", 0);
        this.direction = input.read("Direction", Vec3.CODEC).orElse(null);
    }
}
