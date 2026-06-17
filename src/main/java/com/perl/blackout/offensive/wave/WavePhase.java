package com.perl.blackout.offensive.wave;

/**
 * Phases of the wave game loop for a single Backrooms instance.
 */
public enum WavePhase {
    /** Initial grace period after entering the instance. Daytime, no enemies. */
    PREP,
    /** Night. Enemies are spawned and attack the objective. */
    ATTACK,
    /** Daytime rest between attacks. Enemies are despawned. */
    REST,
    /** Terminal state once the objective has been destroyed (or the game has stopped). */
    ENDED
}
