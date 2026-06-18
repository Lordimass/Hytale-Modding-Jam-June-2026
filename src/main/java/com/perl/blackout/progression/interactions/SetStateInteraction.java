package com.perl.blackout.progression.interactions;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import javax.annotation.Nonnull;

public class SetStateInteraction extends SimpleInstantInteraction {

  public static final BuilderCodec<SetStateInteraction> CODEC = BuilderCodec.builder(
          SetStateInteraction.class, SetStateInteraction::new, SimpleInstantInteraction.CODEC
      )
      .documentation("Sets the held item to a named State variant.")
      .appendInherited(
          new KeyedCodec<>("State", Codec.STRING),
          (interaction, value) -> interaction.state = value,
          interaction -> interaction.state,
          (interaction, parent) -> interaction.state = parent.state
      )
      .addValidator(Validators.nonNull())
      .add()
      .build();

  protected String state;

  @Nonnull
  @Override
  public WaitForDataFrom getWaitForDataFrom() {
    return WaitForDataFrom.Server;
  }

  @Override
  protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context,
      @Nonnull CooldownHandler cooldownHandler) {
    ItemStack held = context.getHeldItem();
    if (held == null || held.getItem().getItemIdForState(this.state) == null) {
      context.getState().state = InteractionState.Failed;
      return;
    }

    ItemStack next = held.withState(this.state);
    ItemStackSlotTransaction transaction = context.getHeldItemContainer()
        .setItemStackForSlot(context.getHeldItemSlot(), next);
    if (!transaction.succeeded()) {
      context.getState().state = InteractionState.Failed;
      return;
    }

    context.setHeldItem(next);
  }

  @Nonnull
  @Override
  public String toString() {
    return "SetStateInteraction{state='" + this.state + "'} " + super.toString();
  }
}
