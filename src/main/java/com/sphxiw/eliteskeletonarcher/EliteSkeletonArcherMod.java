package com.sphxiw.eliteskeletonarcher;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(EliteSkeletonArcherMod.MOD_ID)
public final class EliteSkeletonArcherMod {
    public static final String MOD_ID = "elite_skeleton_archer";
    public static final Logger LOGGER = LogUtils.getLogger();

    public EliteSkeletonArcherMod() {
        LOGGER.info("Elite Skeleton Archer loaded.");
    }
}
