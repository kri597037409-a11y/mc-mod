package com.sphxiw.eliteskeletonarcher.skeleton;

import com.sphxiw.eliteskeletonarcher.EliteSkeletonArcherMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

public final class EliteSkeletonSetup {
    private static final String TAG_EQUIPPED = EliteSkeletonArcherMod.MOD_ID + ".equipped";
    private static final Item[] MELEE_WEAPONS = {
            Items.WOODEN_SWORD,
            Items.STONE_SWORD,
            Items.IRON_SWORD,
            Items.GOLDEN_SWORD,
            Items.DIAMOND_SWORD,
            Items.WOODEN_AXE,
            Items.STONE_AXE,
            Items.IRON_AXE,
            Items.GOLDEN_AXE,
            Items.DIAMOND_AXE
    };

    private EliteSkeletonSetup() {
    }

    public static void setup(Skeleton skeleton) {
        CompoundTag data = skeleton.getPersistentData();
        if (!data.getBoolean(TAG_EQUIPPED)) {
            equip(skeleton);
            data.putBoolean(TAG_EQUIPPED, true);
        }

        installGoals(skeleton);
    }

    private static void equip(Skeleton skeleton) {
        RandomSource random = skeleton.getRandom();
        DifficultyInstance difficulty = skeleton.level().getCurrentDifficultyAt(skeleton.blockPosition());

        skeleton.setItemSlot(EquipmentSlot.MAINHAND, createEnchantedBow(random, difficulty));
        skeleton.setItemSlot(EquipmentSlot.OFFHAND, createMeleeWeapon(random));
    }

    private static ItemStack createEnchantedBow(RandomSource random, DifficultyInstance difficulty) {
        ItemStack bow = new ItemStack(Items.BOW);
        int enchantmentLevel = 16 + random.nextInt(16) + Math.round(difficulty.getSpecialMultiplier() * 8.0F);
        bow = EnchantmentHelper.enchantItem(random, bow, enchantmentLevel, true);

        if (!bow.isEnchanted()) {
            bow.enchant(Enchantments.POWER_ARROWS, 1 + random.nextInt(2));
        }

        return bow;
    }

    private static ItemStack createMeleeWeapon(RandomSource random) {
        return new ItemStack(MELEE_WEAPONS[random.nextInt(MELEE_WEAPONS.length)]);
    }

    private static void installGoals(Skeleton skeleton) {
        skeleton.goalSelector.removeAllGoals(goal ->
                goal instanceof EliteSkeletonCombatGoal
                        || goal instanceof RangedBowAttackGoal<?>
                        || goal instanceof MeleeAttackGoal
                        || goal instanceof RandomStrollGoal
                        || goal instanceof LookAtPlayerGoal
                        || goal instanceof RandomLookAroundGoal
                        || goal instanceof OpenDoorGoal
        );

        skeleton.goalSelector.addGoal(0, new FloatGoal(skeleton));
        skeleton.goalSelector.addGoal(1, new OpenDoorGoal(skeleton, true));
        skeleton.goalSelector.addGoal(2, new EliteSkeletonCombatGoal(skeleton, 1.25D, 4.0D, 9.0D, 18.0D));
        skeleton.goalSelector.addGoal(7, new RandomStrollGoal(skeleton, 0.9D));
        skeleton.goalSelector.addGoal(8, new LookAtPlayerGoal(skeleton, Player.class, 12.0F));
        skeleton.goalSelector.addGoal(8, new RandomLookAroundGoal(skeleton));
    }

    static boolean isMeleeWeapon(ItemStack stack) {
        return stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem;
    }
}
