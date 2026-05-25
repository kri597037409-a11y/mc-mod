package com.sphxiw.eliteskeletonarcher.skeleton;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class EliteSkeletonCombatGoal extends Goal {
    private static final int RANGED_COOLDOWN_TICKS = 24;
    private static final int MELEE_COOLDOWN_TICKS = 18;
    private static final int COVER_RECHECK_TICKS = 80;
    private static final int BLOCK_INTERACTION_TICKS = 20;
    private static final int POTION_DECISION_TICKS = 30;
    private static final int UTILITY_POTION_COOLDOWN_TICKS = 260;
    private static final int HEAL_POTION_COOLDOWN_TICKS = 180;
    private static final int SPLASH_POTION_COOLDOWN_TICKS = 160;
    private static final int MONSTER_COVER_RECHECK_TICKS = 45;
    private static final int RETREAT_TICKS = 120;
    private static final int AMBUSH_DELAY_TICKS = 24;
    private static final double LOW_HEALTH_RATIO = 0.45D;
    private static final double SAFE_HEAL_DISTANCE_SQR = 169.0D;
    private static final double CLOSE_SPLASH_DISTANCE_SQR = 64.0D;
    private static final double DEFENSIVE_MELEE_RANGE_SQR = 6.25D;
    private static final double OPENING_INVISIBILITY_CHANCE = 0.14D;
    private static final double LOW_HEALTH_UTILITY_POTION_CHANCE = 0.12D;
    private static final double CLOSE_SPLASH_CHANCE = 0.22D;

    private final Skeleton skeleton;
    private final double speedModifier;
    private final double meleeRangeSqr;
    private final double preferredMinSqr;
    private final double preferredMaxSqr;
    private int attackCooldown;
    private int coverCooldown;
    private int blockInteractionCooldown;
    private int potionDecisionCooldown;
    private int utilityPotionCooldown;
    private int healPotionCooldown;
    private int splashPotionCooldown;
    private int monsterCoverCooldown;
    private int moveCooldown;
    private int retreatTicks;
    private int ambushDelayTicks;
    private int lastTargetId = -1;
    private Vec3 lastMoveTarget = Vec3.ZERO;

    public EliteSkeletonCombatGoal(Skeleton skeleton, double speedModifier, double meleeRange, double preferredMin, double preferredMax) {
        this.skeleton = skeleton;
        this.speedModifier = speedModifier;
        this.meleeRangeSqr = meleeRange * meleeRange;
        this.preferredMinSqr = preferredMin * preferredMin;
        this.preferredMaxSqr = preferredMax * preferredMax;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.skeleton.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.skeleton.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.skeleton.setAggressive(true);
    }

    @Override
    public void stop() {
        this.skeleton.setAggressive(false);
        this.skeleton.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = this.skeleton.getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }

        if (this.attackCooldown > 0) {
            --this.attackCooldown;
        }
        if (this.coverCooldown > 0) {
            --this.coverCooldown;
        }
        if (this.blockInteractionCooldown > 0) {
            --this.blockInteractionCooldown;
        }
        if (this.potionDecisionCooldown > 0) {
            --this.potionDecisionCooldown;
        }
        if (this.utilityPotionCooldown > 0) {
            --this.utilityPotionCooldown;
        }
        if (this.healPotionCooldown > 0) {
            --this.healPotionCooldown;
        }
        if (this.splashPotionCooldown > 0) {
            --this.splashPotionCooldown;
        }
        if (this.monsterCoverCooldown > 0) {
            --this.monsterCoverCooldown;
        }
        if (this.moveCooldown > 0) {
            --this.moveCooldown;
        }
        if (this.retreatTicks > 0) {
            --this.retreatTicks;
        }
        if (this.ambushDelayTicks > 0) {
            --this.ambushDelayTicks;
        }

        this.skeleton.getLookControl().setLookAt(target, 30.0F, 30.0F);
        this.tryInteractWithNearbyBlocks();

        double distanceSqr = this.skeleton.distanceToSqr(target);
        boolean canSeeTarget = this.skeleton.getSensing().hasLineOfSight(target);
        this.handleTargetChange(target);
        this.considerPotionUse(distanceSqr, canSeeTarget);

        if (this.ambushDelayTicks > 0) {
            this.tryMoveBehindNearbyMonster(target, true);
            this.strafeAroundTarget(target);
            return;
        }

        if (this.shouldRetreat()) {
            this.switchToBow();
            this.retreatFromTarget(target, distanceSqr, canSeeTarget);
            this.tryShootBow(target, canSeeTarget, RANGED_COOLDOWN_TICKS + 8);
            return;
        }

        if (this.tryThrowCloseSplashPotion(target, distanceSqr, canSeeTarget)) {
            return;
        }

        if (distanceSqr <= this.meleeRangeSqr) {
            this.handleCloseTarget(target, distanceSqr, canSeeTarget);
            return;
        }

        this.switchToBow();
        this.keepRangedAdvantage(target, distanceSqr, canSeeTarget);
    }

    private void handleCloseTarget(LivingEntity target, double distanceSqr, boolean canSeeTarget) {
        if (canSeeTarget && this.attackCooldown <= 0 && distanceSqr <= DEFENSIVE_MELEE_RANGE_SQR) {
            this.switchToMelee();
            this.skeleton.swing(InteractionHand.MAIN_HAND);
            this.skeleton.doHurtTarget(target);
            this.attackCooldown = MELEE_COOLDOWN_TICKS;
        }

        this.switchToBow();
        this.retreatFromTarget(target, distanceSqr, canSeeTarget);
        this.tryShootBow(target, canSeeTarget, RANGED_COOLDOWN_TICKS + 4);
    }

    private void keepRangedAdvantage(LivingEntity target, double distanceSqr, boolean canSeeTarget) {
        boolean repositioned = this.tryMoveBehindNearbyMonster(target, false) || this.tryMoveToCover(target, canSeeTarget);

        if (!repositioned && distanceSqr < this.preferredMinSqr) {
            Vec3 away = DefaultRandomPos.getPosAway(this.skeleton, 12, 6, target.position());
            if (away != null) {
                this.requestMoveTo(away, this.speedModifier * 1.15D, 8, 2.0D);
            }
        } else if (!repositioned && (distanceSqr > this.preferredMaxSqr || !canSeeTarget)) {
            this.requestMoveTo(target.position(), this.speedModifier, 10, 2.0D);
        } else if (!repositioned) {
            this.strafeAroundTarget(target);
        }

        this.tryShootBow(target, canSeeTarget, RANGED_COOLDOWN_TICKS);
    }

    private boolean tryMoveToCover(LivingEntity target, boolean canSeeTarget) {
        if (!canSeeTarget || this.coverCooldown > 0 || this.skeleton.distanceToSqr(target) > 144.0D) {
            return false;
        }

        this.coverCooldown = COVER_RECHECK_TICKS;
        Vec3 cover = this.findCoverPosition(target);
        if (cover == null) {
            return false;
        }

        this.requestMoveTo(cover, this.speedModifier * 1.2D, 16, 1.5D);
        return true;
    }

    private Vec3 findCoverPosition(LivingEntity target) {
        BlockPos origin = this.skeleton.blockPosition();

        for (int i = 0; i < 8; ++i) {
            int dx = Mth.nextInt(this.skeleton.getRandom(), -8, 8);
            int dy = Mth.nextInt(this.skeleton.getRandom(), -2, 2);
            int dz = Mth.nextInt(this.skeleton.getRandom(), -8, 8);
            if (dx * dx + dy * dy + dz * dz < 9) {
                continue;
            }

            BlockPos candidate = origin.offset(dx, dy, dz);
            if (!this.canStandAt(candidate) || !this.isCoveredFromTarget(candidate, target)) {
                continue;
            }

            Path path = this.skeleton.getNavigation().createPath(candidate, 0);
            if (path != null && path.canReach()) {
                return Vec3.atBottomCenterOf(candidate);
            }
        }

        return null;
    }

    private boolean canStandAt(BlockPos pos) {
        Level level = this.skeleton.level();
        return level.getBlockState(pos).isAir()
                && level.getBlockState(pos.above()).isAir()
                && level.getBlockState(pos.below()).isSolidRender(level, pos.below());
    }

    private boolean isCoveredFromTarget(BlockPos pos, LivingEntity target) {
        Vec3 from = target.getEyePosition();
        Vec3 to = Vec3.atBottomCenterOf(pos).add(0.0D, this.skeleton.getEyeHeight(), 0.0D);
        BlockHitResult result = this.skeleton.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this.skeleton));
        return result.getType() == HitResult.Type.BLOCK;
    }

    private void strafeAroundTarget(LivingEntity target) {
        if (!this.skeleton.getNavigation().isDone()) {
            return;
        }

        double angle = Math.atan2(this.skeleton.getZ() - target.getZ(), this.skeleton.getX() - target.getX());
        double direction = this.skeleton.getRandom().nextBoolean() ? 1.0D : -1.0D;
        double nextAngle = angle + direction * (Math.PI / 2.5D);
        double radius = 10.0D + this.skeleton.getRandom().nextDouble() * 4.0D;
        double x = target.getX() + Math.cos(nextAngle) * radius;
        double z = target.getZ() + Math.sin(nextAngle) * radius;

        this.requestMoveTo(new Vec3(x, target.getY(), z), this.speedModifier * 0.9D, 14, 2.0D);
    }

    private void handleTargetChange(LivingEntity target) {
        if (target.getId() == this.lastTargetId) {
            return;
        }

        this.lastTargetId = target.getId();
        if (this.utilityPotionCooldown <= 0
                && !this.skeleton.hasEffect(MobEffects.INVISIBILITY)
                && this.skeleton.getRandom().nextDouble() < OPENING_INVISIBILITY_CHANCE) {
            this.drinkInvisibilityPotion();
            this.utilityPotionCooldown = UTILITY_POTION_COOLDOWN_TICKS;
            this.ambushDelayTicks = AMBUSH_DELAY_TICKS;
        }
    }

    private void considerPotionUse(double distanceSqr, boolean canSeeTarget) {
        if (this.potionDecisionCooldown > 0) {
            return;
        }

        this.potionDecisionCooldown = POTION_DECISION_TICKS + this.skeleton.getRandom().nextInt(20);
        if (!this.isLowHealth()) {
            return;
        }

        this.retreatTicks = Math.max(this.retreatTicks, RETREAT_TICKS);
        if (this.utilityPotionCooldown <= 0 && this.skeleton.getRandom().nextDouble() < LOW_HEALTH_UTILITY_POTION_CHANCE) {
            if (this.skeleton.getRandom().nextBoolean() && !this.skeleton.hasEffect(MobEffects.INVISIBILITY)) {
                this.drinkInvisibilityPotion();
            } else {
                this.drinkSwiftnessPotion();
            }
            this.utilityPotionCooldown = UTILITY_POTION_COOLDOWN_TICKS;
        }

        if (this.healPotionCooldown <= 0 && this.skeleton.getHealth() < this.skeleton.getMaxHealth() * 0.7F
                && (distanceSqr >= SAFE_HEAL_DISTANCE_SQR || !canSeeTarget)) {
            this.drinkHarmingPotionForHealing();
            this.healPotionCooldown = HEAL_POTION_COOLDOWN_TICKS;
            this.retreatTicks = Math.max(this.retreatTicks, RETREAT_TICKS / 2);
        }
    }

    private boolean shouldRetreat() {
        return this.retreatTicks > 0 || this.isLowHealth();
    }

    private boolean isLowHealth() {
        return this.skeleton.getHealth() <= this.skeleton.getMaxHealth() * LOW_HEALTH_RATIO;
    }

    private void retreatFromTarget(LivingEntity target, double distanceSqr, boolean canSeeTarget) {
        if (this.tryMoveBehindNearbyMonster(target, true)) {
            return;
        }

        if (this.tryMoveToCover(target, canSeeTarget)) {
            return;
        }

        Vec3 away = DefaultRandomPos.getPosAway(this.skeleton, 16, 7, target.position());
        if (away != null) {
            this.requestMoveTo(away, this.speedModifier * 1.35D, 8, 2.0D);
            return;
        }

        if (distanceSqr < this.preferredMaxSqr) {
            this.requestMoveTo(this.skeleton.position().subtract(target.position()).normalize().scale(8.0D).add(this.skeleton.position()),
                    this.speedModifier * 1.2D, 10, 2.0D);
        }
    }

    private boolean tryThrowCloseSplashPotion(LivingEntity target, double distanceSqr, boolean canSeeTarget) {
        if (!canSeeTarget || distanceSqr > CLOSE_SPLASH_DISTANCE_SQR || this.splashPotionCooldown > 0) {
            return false;
        }

        if (this.skeleton.getRandom().nextDouble() >= CLOSE_SPLASH_CHANCE) {
            this.splashPotionCooldown = 30;
            return false;
        }

        Potion potion = this.skeleton.getRandom().nextBoolean() ? Potions.SLOWNESS : Potions.POISON;
        this.throwSplashPotionAt(target, potion);
        this.splashPotionCooldown = SPLASH_POTION_COOLDOWN_TICKS;
        this.attackCooldown = Math.max(this.attackCooldown, 20);
        return true;
    }

    private boolean tryMoveBehindNearbyMonster(LivingEntity target, boolean urgent) {
        if (this.monsterCoverCooldown > 0) {
            return false;
        }

        this.monsterCoverCooldown = urgent ? 18 : MONSTER_COVER_RECHECK_TICKS;
        Monster coverMob = this.findNearbyMonsterCover(target);
        if (coverMob == null) {
            return false;
        }

        Vec3 fromTargetToMob = coverMob.position().subtract(target.position());
        if (fromTargetToMob.lengthSqr() < 1.0D) {
            return false;
        }

        Vec3 hidePosition = coverMob.position().add(fromTargetToMob.normalize().scale(3.0D));
        BlockPos hideBlock = BlockPos.containing(hidePosition);
        if (!this.canStandAt(hideBlock)) {
            return false;
        }

        Path path = this.skeleton.getNavigation().createPath(hideBlock, 0);
        if (path == null || !path.canReach()) {
            return false;
        }

        this.requestMoveTo(Vec3.atBottomCenterOf(hideBlock), urgent ? this.speedModifier * 1.25D : this.speedModifier, urgent ? 8 : 14, 1.5D);
        return true;
    }

    private Monster findNearbyMonsterCover(LivingEntity target) {
        Monster best = null;
        double bestScore = Double.MAX_VALUE;
        double targetDistanceSqr = this.skeleton.distanceToSqr(target);

        for (Monster monster : this.skeleton.level().getEntitiesOfClass(Monster.class, this.skeleton.getBoundingBox().inflate(12.0D),
                monster -> monster != this.skeleton && monster.isAlive() && monster.distanceToSqr(target) < targetDistanceSqr)) {
            double score = monster.distanceToSqr(this.skeleton) + monster.distanceToSqr(target) * 0.35D;
            if (score < bestScore) {
                best = monster;
                bestScore = score;
            }
        }

        return best;
    }

    private void drinkInvisibilityPotion() {
        this.playDrinkSound();
        this.skeleton.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 20 * 18, 0));
    }

    private void drinkSwiftnessPotion() {
        this.playDrinkSound();
        this.skeleton.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 20, 1));
    }

    private void drinkHarmingPotionForHealing() {
        this.playDrinkSound();
        this.skeleton.heal(6.0F);
    }

    private void playDrinkSound() {
        this.skeleton.playSound(SoundEvents.WITCH_DRINK, 1.0F, 0.85F + this.skeleton.getRandom().nextFloat() * 0.3F);
    }

    private void throwSplashPotionAt(LivingEntity target, Potion potion) {
        ItemStack stack = PotionUtils.setPotion(new ItemStack(Items.SPLASH_POTION), potion);
        ThrownPotion thrownPotion = new ThrownPotion(this.skeleton.level(), this.skeleton);
        thrownPotion.setItem(stack);

        double dx = target.getX() - this.skeleton.getX();
        double dz = target.getZ() - this.skeleton.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double dy = target.getEyeY() - 0.7D - thrownPotion.getY();

        thrownPotion.shoot(dx, dy + horizontal * 0.2D, dz, 0.75F, 6.0F);
        this.skeleton.level().addFreshEntity(thrownPotion);
        this.skeleton.swing(InteractionHand.MAIN_HAND);
        this.skeleton.playSound(SoundEvents.WITCH_THROW, 1.0F, 0.85F + this.skeleton.getRandom().nextFloat() * 0.3F);
    }

    private boolean requestMoveTo(Vec3 target, double speed, int cooldownTicks, double minDistance) {
        if (target == null || !Double.isFinite(target.x) || !Double.isFinite(target.y) || !Double.isFinite(target.z)) {
            return false;
        }

        double minDistanceSqr = minDistance * minDistance;
        if (this.moveCooldown > 0 && this.lastMoveTarget.distanceToSqr(target) < minDistanceSqr && !this.skeleton.getNavigation().isDone()) {
            return false;
        }

        this.lastMoveTarget = target;
        this.moveCooldown = cooldownTicks;
        this.skeleton.getNavigation().moveTo(target.x, target.y, target.z, speed);
        return true;
    }

    private void tryShootBow(LivingEntity target, boolean canSeeTarget, int baseCooldown) {
        if (canSeeTarget && this.attackCooldown <= 0) {
            this.shootAccurateArrow(target);
            this.attackCooldown = baseCooldown + this.skeleton.getRandom().nextInt(8);
        }
    }

    private void shootAccurateArrow(LivingEntity target) {
        ItemStack bow = this.findBow();
        Arrow arrow = new Arrow(this.skeleton.level(), this.skeleton);
        double damage = 2.5D;

        int power = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.POWER_ARROWS, bow);
        if (power > 0) {
            damage += 0.5D * power + 0.5D;
        }

        int punch = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PUNCH_ARROWS, bow);
        if (punch > 0) {
            arrow.setKnockback(punch);
        }

        if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FLAMING_ARROWS, bow) > 0) {
            arrow.setSecondsOnFire(100);
        }

        arrow.setBaseDamage(damage);

        Vec3 targetMotion = target.getDeltaMovement();
        double dxBase = target.getX() - this.skeleton.getX();
        double dzBase = target.getZ() - this.skeleton.getZ();
        double horizontalDistance = Math.sqrt(dxBase * dxBase + dzBase * dzBase);
        double leadTicks = Mth.clamp(horizontalDistance / 3.0D, 0.0D, 6.0D);
        double predictedX = target.getX() + targetMotion.x * leadTicks;
        double predictedY = target.getY() + target.getBbHeight() * 0.55D + targetMotion.y * leadTicks * 0.4D;
        double predictedZ = target.getZ() + targetMotion.z * leadTicks;
        double dx = predictedX - this.skeleton.getX();
        double dy = predictedY - arrow.getY();
        double dz = predictedZ - this.skeleton.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        this.skeleton.swing(InteractionHand.MAIN_HAND);
        this.skeleton.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.skeleton.getRandom().nextFloat() * 0.4F + 0.8F));
        arrow.shoot(dx, dy + horizontal * 0.14D, dz, 2.0F, 1.0F);
        this.skeleton.level().addFreshEntity(arrow);
    }

    private void tryInteractWithNearbyBlocks() {
        if (this.blockInteractionCooldown > 0) {
            return;
        }

        this.blockInteractionCooldown = BLOCK_INTERACTION_TICKS;
        Level level = this.skeleton.level();
        BlockPos center = this.skeleton.blockPosition();

        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, 0, -1), center.offset(1, 1, 1))) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof DoorBlock door && state.hasProperty(DoorBlock.OPEN) && !state.getValue(DoorBlock.OPEN)) {
                door.setOpen(this.skeleton, level, state, pos, true);
                return;
            }

            if (state.getBlock() instanceof TrapDoorBlock && state.hasProperty(TrapDoorBlock.OPEN) && !state.getValue(TrapDoorBlock.OPEN)) {
                level.setBlock(pos, state.setValue(TrapDoorBlock.OPEN, true), 10);
                return;
            }
        }
    }

    private void switchToBow() {
        ItemStack mainHand = this.skeleton.getMainHandItem();
        ItemStack offHand = this.skeleton.getOffhandItem();
        if (mainHand.is(Items.BOW)) {
            return;
        }

        if (offHand.is(Items.BOW)) {
            this.skeleton.setItemSlot(EquipmentSlot.MAINHAND, offHand.copy());
            this.skeleton.setItemSlot(EquipmentSlot.OFFHAND, mainHand.copy());
        }
    }

    private void switchToMelee() {
        ItemStack mainHand = this.skeleton.getMainHandItem();
        ItemStack offHand = this.skeleton.getOffhandItem();
        if (EliteSkeletonSetup.isMeleeWeapon(mainHand)) {
            return;
        }

        if (EliteSkeletonSetup.isMeleeWeapon(offHand)) {
            this.skeleton.setItemSlot(EquipmentSlot.MAINHAND, offHand.copy());
            this.skeleton.setItemSlot(EquipmentSlot.OFFHAND, mainHand.copy());
        }
    }

    private ItemStack findBow() {
        ItemStack mainHand = this.skeleton.getMainHandItem();
        if (mainHand.is(Items.BOW)) {
            return mainHand;
        }

        ItemStack offHand = this.skeleton.getOffhandItem();
        if (offHand.is(Items.BOW)) {
            return offHand;
        }

        return ItemStack.EMPTY;
    }

}
