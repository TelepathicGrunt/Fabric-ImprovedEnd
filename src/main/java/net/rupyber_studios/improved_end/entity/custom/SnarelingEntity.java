package net.rupyber_studios.improved_end.entity.custom;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.ProjectileDamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.tag.FluidTags;
import net.minecraft.util.TimeHelper;
import net.minecraft.util.math.*;
import net.minecraft.util.math.intprovider.UniformIntProvider;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;

import java.util.*;
import java.util.function.Predicate;

public class SnarelingEntity extends HostileEntity implements IAnimatable, Angerable {
    private final AnimationFactory factory = new AnimationFactory(this);
    private static final UUID ATTACKING_SPEED_BOOST_ID = UUID.fromString("020E0DFB-87AE-4653-9556-831010E291A0");
    private static final EntityAttributeModifier ATTACKING_SPEED_BOOST;
    private static final TrackedData<Boolean> ANGRY;
    private static final TrackedData<Boolean> PROVOKED;
    private int lastAngrySoundAge = Integer.MIN_VALUE;
    private int ageWhenTargetSet;
    private static final UniformIntProvider ANGER_TIME_RANGE;
    private int angerTime;
    @Nullable
    private UUID angryAt;

    public SnarelingEntity(EntityType<? extends HostileEntity> entityType, World world) {
        super(entityType, world);
        setAttributes();
        initGoals();
    }

    public static DefaultAttributeContainer.Builder setAttributes() {
        return HostileEntity.createHostileAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0)
            .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.30000001192092896)
            .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 7.0)
            .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new ChasePlayerGoal(this));
        this.goalSelector.add(2, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.add(7, new WanderAroundFarGoal(this, 1.0, 0.0F));
        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
        this.goalSelector.add(8, new LookAroundGoal(this));
        this.targetSelector.add(1, new TeleportTowardsPlayerGoal(this, this::shouldAngerAt));
        this.targetSelector.add(2, new RevengeGoal(this, new Class[0]));
        this.targetSelector.add(3, new ActiveTargetGoal(this, EndermiteEntity.class, true, false));
        this.targetSelector.add(4, new UniversalAngerGoal(this, false));
    }

    private <E extends IAnimatable> PlayState predicate(AnimationEvent<E> event) {
        if (this.isAttacking()) {
            event.getController().setAnimation(new AnimationBuilder().addAnimation("animation.snareling.attack", true));
            return PlayState.CONTINUE;
        }
        if (event.isMoving()) {
            event.getController().setAnimation(new AnimationBuilder().addAnimation("animation.snareling.walk", true));
            return PlayState.CONTINUE;
        }
        event.getController().setAnimation(new AnimationBuilder().addAnimation("animation.snareling.idle", true));
        return PlayState.CONTINUE;
    }

    @Override
    public void registerControllers(AnimationData animationData) {
        animationData.addAnimationController(new AnimationController(this, "controller",
            0, this::predicate));
    }

    @Override
    public AnimationFactory getFactory() {
        return factory;
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        super.setTarget(target);
        EntityAttributeInstance entityAttributeInstance = this.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (target == null) {
            this.ageWhenTargetSet = 0;
            this.dataTracker.set(ANGRY, false);
            this.dataTracker.set(PROVOKED, false);
            entityAttributeInstance.removeModifier(ATTACKING_SPEED_BOOST);
        } else {
            this.ageWhenTargetSet = this.age;
            this.dataTracker.set(ANGRY, true);
            if (!entityAttributeInstance.hasModifier(ATTACKING_SPEED_BOOST)) {
                entityAttributeInstance.addTemporaryModifier(ATTACKING_SPEED_BOOST);
            }
        }

    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(ANGRY, false);
        this.dataTracker.startTracking(PROVOKED, false);
    }

    @Override
    public void chooseRandomAngerTime() {
        this.setAngerTime(ANGER_TIME_RANGE.get(this.random));
    }

    @Override
    public void setAngerTime(int angerTime) {
        this.angerTime = angerTime;
    }

    @Override
    public int getAngerTime() {
        return this.angerTime;
    }

    @Override
    public void setAngryAt(@Nullable UUID angryAt) {
        this.angryAt = angryAt;
    }

    @Nullable
    @Override
    public UUID getAngryAt() {
        return this.angryAt;
    }

    public void playAngrySound() {
        if (this.age >= this.lastAngrySoundAge + 400) {
            this.lastAngrySoundAge = this.age;
            if (!this.isSilent()) {
                this.world.playSound(this.getX(), this.getEyeY(), this.getZ(), SoundEvents.ENTITY_ENDERMAN_STARE, this.getSoundCategory(), 2.5F, 1.0F, false);
            }
        }
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        if (ANGRY.equals(data) && this.isProvoked() && this.world.isClient) {
            this.playAngrySound();
        }
        super.onTrackedDataSet(data);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        this.writeAngerToNbt(nbt);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.readAngerFromNbt(this.world, nbt);
    }

    boolean isPlayerStaring(PlayerEntity player) {
        ItemStack itemStack = player.getInventory().armor.get(3);
        if (itemStack.isOf(Blocks.CARVED_PUMPKIN.asItem())) {
            return false;
        } else {
            Vec3d vec3d = player.getRotationVec(1.0F).normalize();
            Vec3d vec3d2 = new Vec3d(this.getX() - player.getX(), this.getEyeY() - player.getEyeY(), this.getZ() - player.getZ());
            double d = vec3d2.length();
            vec3d2 = vec3d2.normalize();
            double e = vec3d.dotProduct(vec3d2);
            return e > 1.0 - 0.025 / d && player.canSee(this);
        }
    }

    @Override
    protected float getActiveEyeHeight(EntityPose pose, EntityDimensions dimensions) {
        return 2.20F;
    }

    @Override
    public void tickMovement() {
        if (this.world.isClient) {
            for (int i = 0; i < 2; ++i) {
                this.world.addParticle(ParticleTypes.PORTAL, this.getParticleX(0.5), this.getRandomBodyY() - 0.25, this.getParticleZ(0.5), (this.random.nextDouble() - 0.5) * 2.0, -this.random.nextDouble(), (this.random.nextDouble() - 0.5) * 2.0);
            }
        }

        this.jumping = false;
        if (!this.world.isClient) {
            this.tickAngerLogic((ServerWorld) this.world, true);
        }

        super.tickMovement();
    }

    @Override
    public boolean hurtByWater() {
        return true;
    }

    @Override
    protected void mobTick() {
        if (this.world.isDay() && this.age >= this.ageWhenTargetSet + 600) {
            float f = this.getBrightnessAtEyes();
            if (f > 0.5F && this.world.isSkyVisible(this.getBlockPos()) && this.random.nextFloat() * 30.0F < (f - 0.4F) * 2.0F) {
                this.setTarget(null);
                this.teleportRandomly();
            }
        }

        super.mobTick();
    }

    protected boolean teleportRandomly() {
        if (!this.world.isClient() && this.isAlive()) {
            double d = this.getX() + (this.random.nextDouble() - 0.5) * 64.0;
            double e = this.getY() + (double) (this.random.nextInt(64) - 32);
            double f = this.getZ() + (this.random.nextDouble() - 0.5) * 64.0;
            return this.teleportTo(d, e, f);
        } else {
            return false;
        }
    }

    boolean teleportTo(Entity entity) {
        Vec3d vec3d = new Vec3d(this.getX() - entity.getX(), this.getBodyY(0.5) - entity.getEyeY(), this.getZ() - entity.getZ());
        vec3d = vec3d.normalize();
        double e = this.getX() + (this.random.nextDouble() - 0.5) * 8.0 - vec3d.x * 16.0;
        double f = this.getY() + (double) (this.random.nextInt(16) - 8) - vec3d.y * 16.0;
        double g = this.getZ() + (this.random.nextDouble() - 0.5) * 8.0 - vec3d.z * 16.0;
        return this.teleportTo(e, f, g);
    }

    private boolean teleportTo(double x, double y, double z) {
        BlockPos.Mutable mutable = new BlockPos.Mutable(x, y, z);

        while (mutable.getY() > this.world.getBottomY() && !this.world.getBlockState(mutable).getMaterial().blocksMovement()) {
            mutable.move(Direction.DOWN);
        }

        BlockState blockState = this.world.getBlockState(mutable);
        boolean bl = blockState.getMaterial().blocksMovement();
        boolean bl2 = blockState.getFluidState().isIn(FluidTags.WATER);
        if (bl && !bl2) {
            boolean bl3 = this.teleport(x, y, z, true);
            if (bl3 && !this.isSilent()) {
                this.world.playSound(null, this.prevX, this.prevY, this.prevZ, SoundEvents.ENTITY_ENDERMAN_TELEPORT, this.getSoundCategory(), 1.0F, 1.0F);
                this.playSound(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);
            }

            return bl3;
        } else {
            return false;
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return this.isAngry() ? SoundEvents.ENTITY_ENDERMAN_SCREAM : SoundEvents.ENTITY_ENDERMAN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_ENDERMAN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_ENDERMAN_DEATH;
    }

    @Override
    protected void dropEquipment(DamageSource source, int lootingMultiplier, boolean allowDrops) {
        super.dropEquipment(source, lootingMultiplier, allowDrops);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        } else if (source instanceof ProjectileDamageSource) {
            Entity entity = source.getSource();
            boolean bl;
            if (entity instanceof PotionEntity) {
                bl = this.damageFromPotion(source, (PotionEntity) entity, amount);
            } else {
                bl = false;
            }

            for (int i = 0; i < 64; ++i) {
                if (this.teleportRandomly()) {
                    return true;
                }
            }

            return bl;
        } else {
            boolean bl2 = super.damage(source, amount);
            if (!this.world.isClient() && !(source.getAttacker() instanceof LivingEntity) && this.random.nextInt(10) != 0) {
                this.teleportRandomly();
            }

            return bl2;
        }
    }

    private boolean damageFromPotion(DamageSource source, PotionEntity potion, float amount) {
        ItemStack itemStack = potion.getStack();
        Potion potion2 = PotionUtil.getPotion(itemStack);
        List<StatusEffectInstance> list = PotionUtil.getPotionEffects(itemStack);
        boolean bl = potion2 == Potions.WATER && list.isEmpty();
        return bl && super.damage(source, amount);
    }

    public boolean isAngry() {
        return this.dataTracker.get(ANGRY);
    }

    public boolean isProvoked() {
        return this.dataTracker.get(PROVOKED);
    }

    public void setProvoked() {
        this.dataTracker.set(PROVOKED, true);
    }

    @Override
    public boolean cannotDespawn() {
        return super.cannotDespawn();
    }

    static {
        ATTACKING_SPEED_BOOST = new EntityAttributeModifier(ATTACKING_SPEED_BOOST_ID, "Attacking speed boost", 0.15000000596046448, EntityAttributeModifier.Operation.ADDITION);
        ANGRY = DataTracker.registerData(SnarelingEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
        PROVOKED = DataTracker.registerData(SnarelingEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
        ANGER_TIME_RANGE = TimeHelper.betweenSeconds(20, 39);
    }

    static class ChasePlayerGoal extends Goal {
        private final SnarelingEntity enderman;
        @Nullable
        private LivingEntity target;

        public ChasePlayerGoal(SnarelingEntity enderman) {
            this.enderman = enderman;
            this.setControls(EnumSet.of(Control.JUMP, Control.MOVE));
        }

        @Override
        public boolean canStart() {
            this.target = this.enderman.getTarget();
            if (!(this.target instanceof PlayerEntity)) {
                return false;
            } else {
                double d = this.target.squaredDistanceTo(this.enderman);
                return !(d > 256.0) && this.enderman.isPlayerStaring((PlayerEntity) this.target);
            }
        }

        @Override
        public void start() {
            this.enderman.getNavigation().stop();
        }

        @Override
        public void tick() {
            assert this.target != null;
            this.enderman.getLookControl().lookAt(this.target.getX(), this.target.getEyeY(), this.target.getZ());
        }
    }

    static class TeleportTowardsPlayerGoal extends ActiveTargetGoal<PlayerEntity> {
        private final SnarelingEntity enderman;
        @Nullable
        private PlayerEntity targetPlayer;
        private int lookAtPlayerWarmup;
        private int ticksSinceUnseenTeleport;
        private final TargetPredicate staringPlayerPredicate;
        private final TargetPredicate validTargetPredicate = TargetPredicate.createAttackable().ignoreVisibility();

        public TeleportTowardsPlayerGoal(SnarelingEntity enderman, @Nullable Predicate<LivingEntity> targetPredicate) {
            super(enderman, PlayerEntity.class, 10, false, false, targetPredicate);
            this.enderman = enderman;
            this.staringPlayerPredicate = TargetPredicate.createAttackable().setBaseMaxDistance(this.getFollowRange()).setPredicate((playerEntity) -> enderman.isPlayerStaring((PlayerEntity) playerEntity));
        }

        @Override
        public boolean canStart() {
            this.targetPlayer = this.enderman.world.getClosestPlayer(this.staringPlayerPredicate, this.enderman);
            return this.targetPlayer != null;
        }

        @Override
        public void start() {
            this.lookAtPlayerWarmup = this.getTickCount(5);
            this.ticksSinceUnseenTeleport = 0;
            this.enderman.setProvoked();
        }

        @Override
        public void stop() {
            this.targetPlayer = null;
            super.stop();
        }

        @Override
        public boolean shouldContinue() {
            if (this.targetPlayer != null) {
                if (!this.enderman.isPlayerStaring(this.targetPlayer)) {
                    return false;
                } else {
                    this.enderman.lookAtEntity(this.targetPlayer, 10.0F, 10.0F);
                    return true;
                }
            } else {
                return this.targetEntity != null && this.validTargetPredicate.test(this.enderman, this.targetEntity) || super.shouldContinue();
            }
        }

        @Override
        public void tick() {
            if (this.enderman.getTarget() == null) {
                super.setTargetEntity(null);
            }
            if (this.targetPlayer != null) {
                if (--this.lookAtPlayerWarmup <= 0) {
                    this.targetEntity = this.targetPlayer;
                    this.targetPlayer = null;
                    super.start();
                }
            } else {
                if (this.targetEntity != null && !this.enderman.hasVehicle()) {
                    if (this.enderman.isPlayerStaring((PlayerEntity) this.targetEntity)) {
                        if (this.targetEntity.squaredDistanceTo(this.enderman) < 16.0) {
                            this.enderman.teleportRandomly();
                        }
                        this.ticksSinceUnseenTeleport = 0;
                    } else if (this.targetEntity.squaredDistanceTo(this.enderman) > 256.0 && this.ticksSinceUnseenTeleport++ >= this.getTickCount(30) && this.enderman.teleportTo(this.targetEntity)) {
                        this.ticksSinceUnseenTeleport = 0;
                    }
                }
                super.tick();
            }
        }
    }
}