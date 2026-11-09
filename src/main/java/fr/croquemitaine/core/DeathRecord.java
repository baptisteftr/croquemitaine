package fr.croquemitaine.core;

/**
 * Une entrée de l'historique de morts (/death-history).
 * killerName est null quand aucun joueur n'est responsable.
 */
public record DeathRecord(long timestampMillis, String world, int x, int y, int z, String cause, String killerName) {
}
