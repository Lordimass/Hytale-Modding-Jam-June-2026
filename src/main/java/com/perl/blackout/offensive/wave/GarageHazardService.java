package com.perl.blackout.offensive.wave;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3d;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.perl.blackout.world.components.BunkerHatchComponent;

final class GarageHazardService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String DAMAGE_CAUSE_ID = "Environment";
    private static final String DAMAGE_SOURCE = "Deadly Poisonous Gas";
    private static final float LETHAL_DAMAGE = 1.0E9f;
    private static final int HATCH_SCAN_HEIGHT = 3;

    private int damageCauseIndex = Integer.MIN_VALUE;

    void apply(@Nonnull WaveGame game, @Nonnull World world, @Nonnull Store<EntityStore> store,
            @Nonnull WaveConfig config, long attackElapsedMs) {
        WaveConfig.Floor floor = config.findHazardFloor();
        if (floor == null) {
            return;
        }

        long effectStartMs = floor.hazardEffectStartSeconds * 1000L;
        long killStartMs = floor.hazardKillStartSeconds * 1000L;
        int milestone = attackElapsedMs >= killStartMs ? 2 : attackElapsedMs >= effectStartMs ? 1 : 0;

        int last = game.getHazardMilestone();
        if (milestone > last) {
            for (int m = last + 1; m <= milestone; m++) {
                notifyMilestone(world, store, floor, m);
            }
            game.setHazardMilestone(milestone);
        }

        if (milestone < 1) {
            return;
        }

        for (PlayerRef playerRef : world.getPlayerRefs()) {
            if (playerRef == null || !playerRef.isValid()) {
                continue;
            }
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                continue;
            }
            Vector3d pos = WavePlayers.positionOf(store, ref);
            if (pos == null || pos.y < floor.hazardYMin || pos.y > floor.hazardYMax) {
                continue;
            }
            if (isSafe(world, pos)) {
                continue;
            }
            if (milestone >= 2) {
                kill(store, ref);
            } else {
                applyEffect(store, ref, floor.hazardEffectId);
            }
        }
    }

    private void notifyMilestone(World world, Store<EntityStore> store, WaveConfig.Floor floor, int milestone) {
        String title;
        String subtitle;
        NotificationStyle style;
        switch (milestone) {
            case 0 -> {
                title = "GO UNDER GROUND!";
                subtitle = "- Async";
                style = NotificationStyle.Warning;
            }
            case 1 -> {
                title = "It's getting closer";
                subtitle = "- Async";
                style = NotificationStyle.Danger;
            }
            default -> {
                title = "I think this is it";
                subtitle = "- Async";
                style = NotificationStyle.Danger;
            }
        }
        for (PlayerRef playerRef : world.getPlayerRefs()) {
            if (playerRef == null || !playerRef.isValid()) {
                continue;
            }
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                continue;
            }
            Vector3d pos = WavePlayers.positionOf(store, ref);
            if (pos == null || pos.y < floor.hazardYMin || pos.y > floor.hazardYMax) {
                continue;
            }
            NotificationUtil.sendNotification(
                    playerRef.getPacketHandler(), Message.raw(title), Message.raw(subtitle), null, null, style);
        }
    }

    private boolean isSafe(World world, Vector3d pos) {
        int x = (int) Math.floor(pos.x);
        int z = (int) Math.floor(pos.z);
        int headY = (int) Math.floor(pos.y) + 1;
        for (int y = headY; y <= headY + HATCH_SCAN_HEIGHT; y++) {
            BunkerHatchComponent hatch = BlockModule.getComponent(BunkerHatchComponent.getComponentType(), world, x, y, z);
            if (hatch == null) {
                continue;
            }
            BlockType blockType = world.getBlockType(x, y, z);
            if (blockType == null) {
                return false;
            }
            String state = blockType.getStateForBlock(blockType);
            return state == null || !state.contains("Open");
        }
        return false;
    }

    private void applyEffect(Store<EntityStore> store, Ref<EntityStore> ref, String effectId) {
        EntityEffect effect = EntityEffect.getAssetMap().getAsset(effectId);
        if (effect == null) {
            LOGGER.atWarning().log("garage hazard: effect asset %s not found", effectId);
            return;
        }
        EffectControllerComponent controller = store.getComponent(ref, EffectControllerComponent.getComponentType());
        if (controller != null) {
            controller.addEffect(ref, effect, store);
        }
    }

    private void kill(Store<EntityStore> store, Ref<EntityStore> ref) {
        if (damageCauseIndex == Integer.MIN_VALUE) {
            damageCauseIndex = DamageCause.getAssetMap().getIndex(DAMAGE_CAUSE_ID);
        }
        DamageCause cause = damageCauseIndex < 0 ? null : DamageCause.getAssetMap().getAsset(damageCauseIndex);
        if (cause == null) {
            LOGGER.atWarning().log("garage hazard: damage cause %s not found", DAMAGE_CAUSE_ID);
            return;
        }
        Damage damage = new Damage(new Damage.EnvironmentSource(DAMAGE_SOURCE), cause, LETHAL_DAMAGE);
        DamageSystems.executeDamage(ref, store, damage);
    }
}
