package cofh.thermal.core.entity.monster;

import cofh.lib.util.references.CoreReferences;
import cofh.thermal.core.entity.projectile.BlizzProjectileEntity;
import cofh.thermal.lib.common.ThermalConfig;
import cofh.thermal.lib.common.ThermalFlags;
import net.minecraft.enchantment.FrostWalkerEnchantment;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.attributes.AttributeModifierMap;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.controller.FlyingMovementController;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.monster.MonsterEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.pathfinding.FlyingPathNavigator;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.potion.EffectInstance;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.IServerWorld;
import net.minecraft.world.World;

import java.util.EnumSet;
import java.util.Random;

import static cofh.thermal.core.ThermalCore.ITEMS;
import static cofh.thermal.core.init.TCoreSounds.*;
import static cofh.thermal.lib.common.ThermalFlags.FLAG_MOB_BLIZZ;
import static cofh.thermal.lib.common.ThermalIDs.ID_BLIZZ;

public class BlizzEntity extends MonsterEntity {

    private static final DataParameter<Byte> ANGRY = EntityDataManager.defineId(BlizzEntity.class, DataSerializers.BYTE);

    public static boolean canSpawn(EntityType<BlizzEntity> entityType, IServerWorld world, SpawnReason reason, BlockPos pos, Random rand) {

        return ThermalFlags.getFlag(FLAG_MOB_BLIZZ).getAsBoolean() && MonsterEntity.checkMonsterSpawnRules(entityType, world, reason, pos, rand);
    }

    public BlizzEntity(EntityType<? extends BlizzEntity> type, World world) {

        super(type, world);
        this.moveControl = new FlyingMovementController(this, 20, false);
        this.navigation = new FlyingPathNavigator(this, world);
        //this.setPathfindingMalus(PathNodeType.WATER, -1.0F);
        this.setPathfindingMalus(PathNodeType.LAVA, -1.0F);
        this.xpReward = 10;
    }

    @Override
    protected void registerGoals() {

        this.goalSelector.addGoal(4, new BlizzAttackGoal(this));
        this.goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 1.0D));
        this.goalSelector.addGoal(7, new RandomWalkingGoal(this, 1.0D));
        this.goalSelector.addGoal(8, new LookAtGoal(this, PlayerEntity.class, 8.0F));
        this.goalSelector.addGoal(8, new LookRandomlyGoal(this));
        this.goalSelector.addGoal(8, new SwimGoal(this));
        this.targetSelector.addGoal(1, (new HurtByTargetGoal(this)).setAlertOthers());
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, PlayerEntity.class, true));
    }

    public static AttributeModifierMap.MutableAttribute registerAttributes() {

        return MonsterEntity.createMonsterAttributes()
                .add(Attributes.ATTACK_DAMAGE, 7.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.23F)
                .add(Attributes.FLYING_SPEED, 0.6F)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    @Override
    protected void defineSynchedData() {

        super.defineSynchedData();
        this.entityData.define(ANGRY, (byte) 0);
    }

    @Override
    protected SoundEvent getAmbientSound() {

        return ThermalConfig.mobAmbientSounds ? SOUND_BLIZZ_AMBIENT : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {

        return SOUND_BLIZZ_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {

        return SOUND_BLIZZ_DEATH;
    }

    @Override
    public void aiStep() {

        if (!this.onGround && this.getDeltaMovement().y < 0.0D) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(1.0D, 0.6D, 1.0D));
        }
        if (this.level.isClientSide) {
            //            if (this.rand.nextInt(256) == 0 && !this.isSilent()) {
            //                this.world.playSound(this.getPosX() + 0.5D, this.getPosY() + 0.5D, this.getPosZ() + 0.5D, SOUND_BLIZZ_ROAM, this.getSoundCategory(), 0.5F + 0.25F * this.rand.nextFloat(), this.rand.nextFloat() * 0.7F + 0.3F, true);
            //            }
            if (this.random.nextInt(2) == 0) {
                this.level.addParticle(CoreReferences.FROST_PARTICLE, this.getRandomX(0.5D), this.getRandomY(), this.getRandomZ(0.5D), 0.0D, 0.0D, 0.0D);
            }
        }
        super.aiStep();
    }

    @Override
    protected void onChangedBlock(BlockPos pos) {

        FrostWalkerEnchantment.onEntityMoved(this, level, pos, 1);

        if (this.shouldRemoveSoulSpeed(this.getBlockStateOn())) {
            this.removeSoulSpeed();
        }
        this.tryAddSoulSpeed();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {

        return super.hurt(source, source.isFire() ? amount + 3 : amount);
    }

    @Override
    public boolean canBeAffected(EffectInstance effect) {

        return super.canBeAffected(effect) && !effect.equals(CoreReferences.CHILLED);
    }

    @Override
    public boolean causeFallDamage(float distance, float damageMultiplier) {

        return false;
    }

    @Override
    public ItemStack getPickedResult(RayTraceResult target) {

        return new ItemStack(ITEMS.get("blizz_spawn_egg"));
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {

        return source.msgId.equals(ID_BLIZZ) || super.isInvulnerableTo(source);
    }

    // region ANGER MANAGEMENT
    public boolean isAngry() {

        return (this.entityData.get(ANGRY) & 1) != 0;
    }

    protected void setAngry(boolean angry) {

        byte b0 = this.entityData.get(ANGRY);
        if (angry) {
            b0 = (byte) (b0 | 1);
        } else {
            b0 = (byte) (b0 & -2);
        }
        this.entityData.set(ANGRY, b0);
    }
    // endregion

    static class BlizzAttackGoal extends Goal {

        private final BlizzEntity blizz;
        private final Vector3d[] hoverOffsets = getHoverOffsets(new Vector3d(1.5, 2.25, 0), 16);
        private int attackTime;
        private int hoverStep;
        private int chaseStep;

        public BlizzAttackGoal(BlizzEntity blizzIn) {

            this.blizz = blizzIn;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        /**
         * Returns whether execution should begin. You can also read and cache any state necessary for execution in this
         * method as well.
         */
        public boolean canUse() {

            LivingEntity target = this.blizz.getTarget();
            return target != null && target.isAlive() && this.blizz.canAttack(target);
        }

        /**
         * Execute a one shot task or start executing a continuous task
         */
        public void start() {

            this.chaseStep = 0;
        }

        /**
         * Reset the task's internal state. Called when this task is interrupted by another one
         */
        public void stop() {

            this.blizz.setAngry(false);
            this.chaseStep = 0;
        }

        /**
         * Keep ticking a continuous task that has already been started
         */
        public void tick() {

            --attackTime;
            LivingEntity target = blizz.getTarget();
            if (target == null) {
                return;
            }
            Vector3d pos = blizz.position();
            Vector3d targetPos = target.getEyePosition(0.5F);
            Vector3d diff = pos.subtract(targetPos);
            double distSqr = blizz.distanceToSqr(target);
            double horzDistSqr = getHorizontalDistanceSqr(diff);
            if (blizz.getSensing().canSee(target) && distSqr < getFollowDistance() * getFollowDistance()) {
                chaseStep = 0;
                blizz.getLookControl().setLookAt(target, 10.0F, 10.0F);
                if (distSqr < 4.0) {
                    if (attackTime <= 0) {
                        attackTime = 20;
                        blizz.doHurtTarget(target);
                    }
                } else if (pos.y > target.getY() + 2) {
                    blizz.setAngry(true);
                    if (attackTime <= 0) {
                        attackTime = 7;
                        Random rand = blizz.getRandom();
                        World world = blizz.level;
                        //TODO: less annoying sound
                        //world.playSound(null, pos.x + 0.5D, pos.y + 0.5D, pos.z + 0.5D, SOUND_BASALZ_SHOOT, SoundCategory.HOSTILE, 1.0F, (rand.nextFloat() - 0.5F) * 0.2F + 1.0F);
                        BlizzProjectileEntity projectile;
                        if (horzDistSqr < 9) {
                            projectile = new BlizzProjectileEntity(targetPos.x + rand.nextGaussian() * 0.8F, pos.y - rand.nextFloat() * 0.5F - 0.2F, targetPos.z + rand.nextGaussian() * 0.8F, 0, -1.0, 0, world);
                        } else {
                            projectile = new BlizzProjectileEntity(pos.x + rand.nextGaussian() * 0.8F, pos.y - rand.nextFloat() - 0.2F, pos.z + rand.nextGaussian() * 0.8F, 0, -1.0, 0, world);
                        }
                        projectile.setOwner(blizz);
                        world.addFreshEntity(projectile);
                        ++hoverStep;
                        hoverStep %= hoverOffsets.length;
                    }
                    blizz.getMoveControl().setWantedPosition(targetPos.x + hoverOffsets[hoverStep].x, targetPos.y + hoverOffsets[hoverStep].y, targetPos.z + hoverOffsets[hoverStep].z, 1.0D);
                    return;
                } else {
                    blizz.setAngry(false);
                }
            } else {
                blizz.setAngry(false);
            }
            if (chaseStep < 5) {
                ++chaseStep;
                blizz.getMoveControl().setWantedPosition(targetPos.x, targetPos.y + hoverOffsets[0].y, targetPos.z, 1.0D);
            }
            super.tick();
        }

        private double getFollowDistance() {

            return this.blizz.getAttributeValue(Attributes.FOLLOW_RANGE);
        }

        protected static Vector3d[] getHoverOffsets(Vector3d start, int steps) {

            float stepRad = (float) Math.PI * 2 / steps;
            Vector3d[] offsets = new Vector3d[steps];
            for (int i = 0; i < steps; ++i) {
                offsets[i] = start.yRot(stepRad * i);
            }
            return offsets;
        }

    }

}
