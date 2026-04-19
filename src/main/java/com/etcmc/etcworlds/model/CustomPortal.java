package com.etcmc.etcworlds.model;

/**
 * Represents a custom block-based portal that teleports players
 * when they step on a specific block coordinate.
 */
public class CustomPortal {

    public String name;

    /** World where the trigger block is located. */
    public String triggerWorld;
    public int triggerX, triggerY, triggerZ;

    /** Destination world name. */
    public String destinationWorld;

    /** Custom destination coords. If not set, uses the destination world's spawn. */
    public double destX = Double.MAX_VALUE;
    public double destY = Double.MAX_VALUE;
    public double destZ = Double.MAX_VALUE;
    public float destYaw   = 0f;
    public float destPitch = 0f;

    /** Optional permission node required to use this portal. Empty = no permission needed. */
    public String permission = "";

    /** Message sent to the player when they enter the portal. Empty = no message. */
    public String enterMessage = "";

    public boolean hasCustomDest() {
        return destX != Double.MAX_VALUE;
    }
}
