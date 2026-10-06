package com.example.examplemod.entity;

import com.example.examplemod.ExampleMod;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;

public class RedDiamondSwordProjectile extends Projectile implements IEntityAdditionalSpawnData {
    
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(RedDiamondSwordProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TIMER = SynchedEntityData.defineId(RedDiamondSwordProjectile.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vec3> ORIGIN_POS = SynchedEntityData.defineId(RedDiamondSwordProjectile.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> MAX_DISTANCE = SynchedEntityData.defineId(RedDiamondSwordProjectile.class, EntityDataSerializers.FLOAT);
    
    // States: 0 = flying forward, 1 = waiting, 2 = returning
    private static final int STATE_FLYING = 0;
    private static final int STATE_WAITING = 1;
    private static final int STATE_RETURNING = 2;
    
    private static final int MAX_FLY_TICKS = 40; // ~2 seconds at 20 ticks/sec for 10 blocks
    private static final int WAIT_TICKS = 40; // 2 seconds
    private static final float MAX_DISTANCE_BLOCKS = 10.0f;
    private static final float RETURN_SPEED = 1.5f;
    private static final float DAMAGE = 8.0f;
    
    private ItemStack itemStack;
    private Player owner;
    private int flyTicks = 0;
    private int waitTicks = 0;
    private Vec3 startPos;
    private boolean hasHitEntity = false;

    public RedDiamondSwordProjectile(EntityType<? extends Projectile> type, Level level) {
        super(type, level);
        this.itemStack = new ItemStack(ExampleMod.RED_DIAMOND_SWORD.get());
        this.startPos = this.position();
    }

    public RedDiamondSwordProjectile(Level level, Player player, ItemStack stack) {
        super(ExampleMod.RED_DIAMOND_SWORD_PROJECTILE.get(), level);
        this.owner = player;
        this.itemStack = stack.copy();
        this.startPos = player.position();
        this.setPos(player.getEyePosition());
        this.entityData.set(ORIGIN_POS, startPos);
        this.entityData.set(MAX_DISTANCE, MAX_DISTANCE_BLOCKS);
        this.entityData.set(STATE, STATE_FLYING);
        this.entityData.set(TIMER, 0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(STATE, STATE_FLYING);
        builder.define(TIMER, 0);
        builder.define(ORIGIN_POS, Vec3.ZERO);
        builder.define(MAX_DISTANCE, MAX_DISTANCE_BLOCKS);
    }

    @Override
    public void tick() {
        super.tick();
        
        if (this.level().isClientSide()) {
            // Spawn particles for visual effect
            if (this.entityData.get(STATE) == STATE_FLYING || this.entityData.get(STATE) == STATE_RETURNING) {
                for (int i = 0; i < 3; i++) {
                    this.level().addParticle(ParticleTypes.CRIT, 
                        this.getX() + (this.random.nextDouble() - 0.5) * 0.5,
                        this.getY() + (this.random.nextDouble() - 0.5) * 0.5,
                        this.getZ() + (this.random.nextDouble() - 0.5) * 0.5,
                        0, 0, 0);
                }
            }
            return;
        }

        int currentState = this.entityData.get(STATE);
        int currentTimer = this.entityData.get(TIMER);
        
        switch (currentState) {
            case STATE_FLYING -> {
                flyTicks++;
                this.entityData.set(TIMER, flyTicks);
                
                // Check if we've reached max distance or time
                double distanceTraveled = this.position().distanceTo(this.entityData.get(ORIGIN_POS));
                if (distanceTraveled >= this.entityData.get(MAX_DISTANCE) || flyTicks >= MAX_FLY_TICKS) {
                    // Stop and wait
                    this.setDeltaMovement(Vec3.ZERO);
                    this.entityData.set(STATE, STATE_WAITING);
                    this.entityData.set(TIMER, 0);
                    waitTicks = 0;
                }
            }
            case STATE_WAITING -> {
                waitTicks++;
                this.entityData.set(TIMER, waitTicks);
                this.setDeltaMovement(Vec3.ZERO);
                
                if (waitTicks >= WAIT_TICKS) {
                    // Start returning
                    this.entityData.set(STATE, STATE_RETURNING);
                    this.entityData.set(TIMER, 0);
                }
            }
            case STATE_RETURNING -> {
                if (this.owner != null && this.owner.isAlive() && !this.owner.isSpectator()) {
                    Vec3 ownerPos = this.owner.getEyePosition();
                    Vec3 toOwner = ownerPos.subtract(this.position()).normalize();
                    
                    // Move towards owner
                    this.setDeltaMovement(toOwner.scale(RETURN_SPEED));
                    this.move(MoverType.SELF, this.getDeltaMovement());
                    
                    // Check if reached owner
                    if (this.position().distanceTo(ownerPos) < 2.0) {
                        // Give item back to owner
                        if (this.owner.getInventory().add(this.itemStack.copy())) {
                            this.itemStack = ItemStack.EMPTY;
                        } else {
                            this.owner.drop(this.itemStack.copy(), false);
                        }
                        this.discard();
                    }
                } else {
                    // Owner dead or invalid, drop item and disappear
                    this.spawnAtLocation(this.itemStack);
                    this.discard();
                }
            }
        }
        
        // Check for entity collisions in all states except waiting
        if (currentState != STATE_WAITING) {
            checkEntityCollisions();
        }
    }

    private void checkEntityCollisions() {
        AABB box = this.getBoundingBox().inflate(0.5);
        for (Entity entity : this.level().getEntities(this, box, e -> e instanceof LivingEntity && e != this.owner && e.isAlive())) {
            if (entity instanceof LivingEntity living) {
                // Damage the entity
                living.hurt(this.damageSources().thrown(this, this.owner), DAMAGE);
                
                // Apply knockback
                Vec3 knockback = this.getDeltaMovement().normalize().scale(0.5);
                living.setDeltaMovement(living.getDeltaMovement().add(knockback.x, 0.2, knockback.z));
                
                // Play hit sound
                this.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 1.0F);
                
                // Spawn hit particles
                for (int i = 0; i < 10; i++) {
                    this.level().addParticle(ParticleTypes.CRIT,
                        entity.getX() + (this.random.nextDouble() - 0.5) * entity.getBbWidth(),
                        entity.getY() + this.random.nextDouble() * entity.getBbHeight(),
                        entity.getZ() + (this.random.nextDouble() - 0.5) * entity.getBbWidth(),
                        0, 0, 0);
                }
                
                // The projectile continues moving (doesn't stop on hit)
                hasHitEntity = true;
            }
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        // We handle entity hits in tick() to allow piercing
        super.onHitEntity(result);
    }

    @Override
    protected void onHit(HitResult result) {
        // Don't stop on block hits either, just continue
        if (result.getType() == HitResult.Type.BLOCK) {
            // Bounce slightly or continue
            Vec3 motion = this.getDeltaMovement();
            this.setDeltaMovement(motion.x * -0.2, motion.y, motion.z * -0.2);
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void writeSpawnData(net.minecraft.network.FriendlyByteBuf buffer) {
        buffer.writeItem(this.itemStack);
        if (this.owner != null) {
            buffer.writeInt(this.owner.getId());
        } else {
            buffer.writeInt(-1);
        }
        buffer.writeVec3(this.startPos);
    }

    @Override
    public void readSpawnData(net.minecraft.network.FriendlyByteBuf buffer) {
        this.itemStack = buffer.readItem();
        int ownerId = buffer.readInt();
        this.startPos = buffer.readVec3();
        this.entityData.set(ORIGIN_POS, this.startPos);
        
        if (ownerId != -1 && this.level() != null) {
            Entity entity = this.level().getEntity(ownerId);
            if (entity instanceof Player player) {
                this.owner = player;
            }
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return entity instanceof LivingEntity && entity != this.owner && entity.isAlive();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.put("Item", this.itemStack.save(new CompoundTag()));
        compound.putInt("State", this.entityData.get(STATE));
        compound.putInt("Timer", this.entityData.get(TIMER));
        compound.putDouble("StartX", this.startPos.x);
        compound.putDouble("StartY", this.startPos.y);
        compound.putDouble("StartZ", this.startPos.z);
        compound.putFloat("MaxDistance", this.entityData.get(MAX_DISTANCE));
        compound.putBoolean("HasHitEntity", hasHitEntity);
        
        if (this.owner != null) {
            compound.putUUID("OwnerUUID", this.owner.getUUID());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.itemStack = ItemStack.of(compound.getCompound("Item"));
        this.entityData.set(STATE, compound.getInt("State"));
        this.entityData.set(TIMER, compound.getInt("Timer"));
        this.startPos = new Vec3(
            compound.getDouble("StartX"),
            compound.getDouble("StartY"),
            compound.getDouble("StartZ")
        );
        this.entityData.set(ORIGIN_POS, this.startPos);
        this.entityData.set(MAX_DISTANCE, compound.getFloat("MaxDistance"));
        this.hasHitEntity = compound.getBoolean("HasHitEntity");
        
        if (compound.hasUUID("OwnerUUID")) {
            this.owner = this.level().getPlayerByUUID(compound.getUUID("OwnerUUID"));
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 256.0; // Render up to 16 blocks away
    }
}