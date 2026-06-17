package com.perl.blackout.defensive.motion.builders;

import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.combat.HeadMotionAim;
import com.hypixel.hytale.server.npc.corecomponents.combat.builders.BuilderHeadMotionAim;
import com.perl.blackout.defensive.motion.HeadMotionTurretAim;

import javax.annotation.Nonnull;

public class BuilderHeadMotionTurretAim extends BuilderHeadMotionAim {
    @Nonnull
    public HeadMotionTurretAim build(@Nonnull BuilderSupport builderSupport) {
        return new HeadMotionTurretAim(this, builderSupport);
    }
}
