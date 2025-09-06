# Manuel de test des options de rendu - Minecraft Clone

## Contrôles des options de rendu

### Ouvrir/Fermer le menu
- **F1** : Ouvre ou ferme l'interface des options de rendu

### Options disponibles (quand le menu est ouvert)
1. **Touche 1** : Augmenter la distance de rendu (2-32 chunks)
2. **Touche 2** : Activer/désactiver l'antialiasing
3. **Touche 3** : Changer le niveau d'antialiasing (2x → 4x → 8x → 16x → 2x)
4. **Touche 4** : Augmenter la distance LOD (+10.0)
5. **Touche 5** : Activer/désactiver le système LOD
6. **Touche 6** : Activer/désactiver la synchronisation verticale (V-Sync)
7. **Touche 7** : Augmenter le budget de meshes par frame

### Raccourcis existants
- **F3+G** : Affichage des bordures de chunks (maintenant configurable via les options)
- **Molette souris** : Navigation dans la hotbar (haut = précédent, bas = suivant)
- **Touches 1-9** : Sélection directe des blocs dans la hotbar (quand menu options fermé)

## Tests recommandés

### Test 1: Distance de rendu
1. Ouvrir le menu des options (F1)
2. Appuyer plusieurs fois sur "1" pour augmenter la distance de rendu
3. Observer l'augmentation du nombre de chunks visibles
4. Vérifier que les performances se dégradent avec des distances élevées

### Test 2: Antialiasing
1. Ouvrir le menu des options (F1)
2. Appuyer sur "2" pour activer l'antialiasing
3. Appuyer sur "3" pour changer les niveaux
4. Observer la qualité visuelle des bords (nécessite une carte graphique compatible)

### Test 3: Système LOD
1. Ouvrir le menu des options (F1)
2. Appuyer sur "4" pour modifier la distance LOD
3. Appuyer sur "5" pour désactiver/réactiver le LOD
4. Observer la différence de rendu entre chunks proches et lointains

### Test 4: V-Sync
1. Ouvrir le menu des options (F1)
2. Appuyer sur "6" pour activer/désactiver le V-Sync
3. Observer les changements de framerate (visible dans le titre de la fenêtre)

### Test 6: Sélection de blocs améliorée
1. Utiliser la molette de souris pour naviguer dans la hotbar
2. Utiliser les touches 1-9 pour sélectionner directement un bloc
3. Vérifier que la sélection se met à jour visuellement dans l'interface

### Test 7: Interface utilisateur
1. Ouvrir le menu avec F1
2. Vérifier que le texte est lisible (rendu avec lignes vectorielles)
3. Fermer le menu et vérifier que les touches 1-9 contrôlent maintenant la hotbar

## Fonctionnalités implémentées

### ✅ Complètement implémenté
- Interface utilisateur des options de rendu avec F1
- Contrôles clavier pour modifier les options (touches 1-7)
- Distance de rendu configurable (2-32 chunks)
- Support de l'antialiasing MSAA avec niveaux configurables
- Système LOD avec distance configurable
- V-Sync configurable en temps réel
- Affichage des bordures de chunks configurable
- Budget de meshes par frame configurable
- Navigation hotbar avec molette souris
- Sélection directe hotbar avec touches 1-9
- Rendu de texte bitmap simple pour l'interface

### 🔄 Partiellement implémenté
- Rendu de texte (système bitmap simple, à améliorer)
- Sauvegarde/chargement des options (TODO)
- Application dynamique de certaines options

### ❌ Non implémenté (TODOs)
- Système de rendu de texte avancé avec polices TrueType
- Sauvegarde persistante des options de rendu
- Modification dynamique du niveau d'antialiasing (nécessite recréation du contexte)
- Interface graphique plus avancée avec boutons cliquables
- Options de qualité d'ombres et d'éclairage
- Options de distance de vue du fog

## Configuration technique

### Classes principales
- `RenderOptions` : Gestion des paramètres de rendu
- `OptionsRenderer` : Interface utilisateur des options
- `Values` : Configuration globale (maintenant avec méthodes dynamiques)

### Paramètres par défaut
- Distance de rendu : 8 chunks
- Antialiasing : désactivé
- Distance LOD : 80.0 unités
- V-Sync : activé
- Budget meshes : 2 par frame

### Limites des paramètres
- Distance de rendu : 2-32 chunks
- Distance LOD : 20.0-500.0 unités
- Niveaux antialiasing : 2x, 4x, 8x, 16x
- Budget meshes : 1-10 par frame