package com.perl.blackout.offensive.wave;

/**
 * Phases of the wave game loop for a single Backrooms instance.
 *
 * <p>The loop is endless: {@link #REST} and {@link #ATTACK} alternate on their timers and never
 * resolve to a win or loss. {@link #ENDED} is only reached when the instance is torn down.
 */
public enum WavePhase {
    /** Daytime lull. Entry grace period and the gap between waves; enemies are despawned. */
    REST,
    /** Night. Enemies are spawned and hunt the bench (if placed) or the nearest player. */
    ATTACK,
    /** Terminal state once the game has stopped (instance/world removed). */
    ENDED
}
