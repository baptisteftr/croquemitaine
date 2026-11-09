# Backlog (hors périmètre V1)

- **Arrivée en cours de partie** : un joueur inconnu qui se connecte pendant une partie est inscrit avec les
  vies de départ. À arbitrer : l'interdire, ou l'inscrire avec le minimum des vies actuelles.
- **Recharges / usages uniques après redémarrage serveur** : l'état interne des pouvoirs (recharge d'Ombre,
  Saut de l'ombre déjà utilisé…) n'est pas persisté ; un redémarrage le remet à zéro.
- **Pouvoir Ombre** : un joueur qui se connecte pendant l'invisibilité du Croque voit son armure jusqu'au
  prochain renvoi (1 s).
- **Éliminés en vocal** : muets pour les vivants (implémenté). Option possible : un groupe vocal « Éliminés » pour qu'ils puissent parler entre eux.
- **Équilibrage** : durées, taux d'œufs, poids des pouvoirs, Sursis hérité (n'ajoute du temps qu'au Croque qui le tire comme
  nouveau pouvoir ; hérité, il est sans effet — à arbitrer) — à revoir après la première vraie partie.
- **Héritage des pouvoirs** : faut-il hériter aussi des pouvoirs des Croques qui ont échoué ? (actuellement oui).
