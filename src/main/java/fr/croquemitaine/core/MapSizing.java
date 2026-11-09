package fr.croquemitaine.core;

/** Taille de la world border selon le nombre de joueurs (interpolation linéaire bornée). */
public final class MapSizing {
    private MapSizing() {
    }

    public static int size(int players, int playersMin, int playersMax, int sizeMin, int sizeMax) {
        if (playersMax <= playersMin) return sizeMin;
        int p = Math.clamp(players, playersMin, playersMax);
        double t = (double) (p - playersMin) / (playersMax - playersMin);
        return (int) Math.round(sizeMin + t * (sizeMax - sizeMin));
    }
}
