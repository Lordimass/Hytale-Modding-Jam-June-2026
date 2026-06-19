package com.perl.blackout.world.systems;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.modules.block.BlockModule.BlockStateInfo;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.perl.blackout.world.components.CycelStateComponent;
import com.perl.blackout.world.resources.WorldCycleStateResource;

public final class CycleStateRefSystem extends RefSystem<ChunkStore> {

    @Override
    public Query<ChunkStore> getQuery() {
        return Query.and(CycelStateComponent.getComponentType(), BlockStateInfo.getComponentType());
    }

    @Override
    public void onEntityAdded(@Nonnull Ref<ChunkStore> ref, @Nonnull AddReason reason,
                              @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        World world = store.getExternalData().getWorld();
        WorldCycleStateResource phase = world.getEntityStore().getStore().getResource(WorldCycleStateResource.getResourceType());
        if (phase == null) {
            return;
        }

        CycelStateComponent component = commandBuffer.getComponent(ref, CycelStateComponent.getComponentType());
        BlockStateInfo blockStateInfo = commandBuffer.getComponent(ref, BlockStateInfo.getComponentType());
        if (component == null || blockStateInfo == null) {
            return;
        }

        String stateName = component.stateFor(phase.isOn());
        if (stateName == null) {
            return;
        }

        CyclePhase.applyState(commandBuffer, blockStateInfo.getChunkRef(), blockStateInfo.getIndex(), stateName);
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<ChunkStore> ref, @Nonnull RemoveReason reason,
                               @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
    }
}
