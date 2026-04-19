package com.etcmc.etcworlds.model;

/**
 * Definición de un portal custom basado en bloque.
 * Cuando un jugador pisa el bloque en (triggerX,triggerY,triggerZ) del triggerWorld
 * es teletransportado al destinationWorld (en destX/Y/Z si están definidas, o al spawn del mundo).
 */
public class CustomPortal {

    /** Nombre único del portal (clave en portals.yml). */
    public String name;

    // === Bloque disparador ===
    public String triggerWorld;
    public int triggerX, triggerY, triggerZ;

    // === Destino ===
    public String destinationWorld;
    /** Si destX == Double.MAX_VALUE → usar el spawn configurado del mundo destino. */
    public double destX = Double.MAX_VALUE;
    public double destY = Double.MAX_VALUE;
    public double destZ = Double.MAX_VALUE;
    public float  destYaw  = 0f;
    public float  destPitch = 0f;

    /** Permiso requerido para usar el portal. Vacío = cualquier jugador. */
    public String permission = "";

    /** Mensaje que se muestra al entrar al portal. Vacío = ninguno. */
    public String enterMessage = "";

    public boolean hasCustomDest() {
        return destX != Double.MAX_VALUE;
    }
}
