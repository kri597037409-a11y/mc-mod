package com.sphxiw.eliteskeletonarcher.event;

import com.sphxiw.eliteskeletonarcher.EliteSkeletonArcherMod;
import com.sphxiw.eliteskeletonarcher.skeleton.EliteSkeletonSetup;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = EliteSkeletonArcherMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EliteSkeletonEvents {
    private static final double NATURAL_SPAWN_KEEP_CHANCE = 0.15D;

    private EliteSkeletonEvents() {
    }

    @SubscribeEvent
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (!(event.getEntity() instanceof Skeleton)) {
            return;
        }

        if (isNaturalWorldSpawn(event.getSpawnType()) && event.getLevel().getRandom().nextDouble() > NATURAL_SPAWN_KEEP_CHANCE) {
            event.setSpawnCancelled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Skeleton skeleton)) {
            return;
        }

        EliteSkeletonSetup.setup(skeleton);
    }

    private static boolean isNaturalWorldSpawn(MobSpawnType spawnType) {
        return spawnType == MobSpawnType.NATURAL || spawnType == MobSpawnType.CHUNK_GENERATION;
    }
}
