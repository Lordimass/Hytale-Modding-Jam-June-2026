package com.perl.blackout.world.components;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public final class CycelStateComponent implements Component<ChunkStore> {

    @Nonnull
    public static final BuilderCodec<CycelStateComponent> CODEC =
            BuilderCodec.builder(CycelStateComponent.class, CycelStateComponent::new)
                    .append(new KeyedCodec<>("OnState", Codec.STRING), (c, v) -> c.onState = v, c -> c.onState)
                    .add()
                    .append(new KeyedCodec<>("OffState", Codec.STRING), (c, v) -> c.offState = v, c -> c.offState)
                    .add()
                    .<String>append(new KeyedCodec<>("OnSound", Codec.STRING), (c, v) -> c.onSound = v, c -> c.onSound)
                    .addValidator(SoundEvent.VALIDATOR_CACHE.getValidator())
                    .add()
                    .<String>append(new KeyedCodec<>("OffSound", Codec.STRING), (c, v) -> c.offSound = v, c -> c.offSound)
                    .addValidator(SoundEvent.VALIDATOR_CACHE.getValidator())
                    .add()
                    .build();

    private static ComponentType<ChunkStore, CycelStateComponent> componentType;

    public static void setComponentType(ComponentType<ChunkStore, CycelStateComponent> type) {
        componentType = type;
    }

    public static ComponentType<ChunkStore, CycelStateComponent> getComponentType() {
        return componentType;
    }

    private String onState;
    private String offState;
    private String onSound;
    private String offSound;

    @Nullable
    public String getOnState() {
        return this.onState;
    }

    @Nullable
    public String getOffState() {
        return this.offState;
    }

    @Nullable
    public String stateFor(boolean on) {
        return on ? this.onState : this.offState;
    }

    public int soundIndexFor(boolean on) {
        String id = on ? this.onSound : this.offSound;
        return id == null ? SoundEvent.EMPTY_ID : SoundEvent.getAssetMap().getIndex(id);
    }

    @Nonnull
    @Override
    public Component<ChunkStore> clone() {
        CycelStateComponent copy = new CycelStateComponent();
        copy.onState = this.onState;
        copy.offState = this.offState;
        copy.onSound = this.onSound;
        copy.offSound = this.offSound;
        return copy;
    }
}
