# Minecraft-Clone
resources/textures are from Mojang, rights reserved to Mojang

PS : Globalement mauvais, mais ce projet est là surtout pour tester le dev OpenGL ;)

## ✨ Fonctionnalités

- 🕸️ Griddy meshing
- ⚡ Génération parallèle des chunks
- 🏃‍♂️ Mouvements des joueurs en parallèle
- 🖼️ Rendu des maillages en parallèle
- 📦 Compression des chunks sur le disque
- 🔺 **NOUVEAU**: Rendu par traversée de rayons voxel (Voxel Ray Renderer)

## 🎮 Rendu par Rayons Voxel

Ce projet inclut maintenant un nouveau système de rendu expérimental qui utilise un algorithme de traversée de rayons 3D (DDA - Digital Differential Analyzer) pour échantillonner directement les blocs voxel sans générer de maillages triangulaires.

### 🎯 Objectifs

- **Réduction du coût CPU**: Éliminer la phase de génération de mesh
- **Simplification**: Logique de mise à jour des chunks dynamiques plus simple
- **Expérimentation**: Rendu potentiellement plus cohérent visuellement
- **Comparaison**: Garder le rendu mesh traditionnel pour comparer les performances

### 🔧 Utilisation

1. **Basculer entre les renderers**: Appuyez sur **F6** pendant le jeu
2. **Mode par défaut**: Mesh Renderer (traditionnel)
3. **Mode expérimental**: Voxel Ray Renderer (nouveau)

### 📊 Fonctionnalités Implémentées

#### Voxel Ray Renderer
- ✅ Algorithme DDA 3D pour la traversée de rayons
- ✅ Éclairage Lambert simple avec teinte directionnelle  
- ✅ Atténuation de brouillard/distance simple
- ✅ Rendu CPU avec buffer de couleurs plein écran
- ✅ Hot-swap en temps réel avec le mesh renderer
- ✅ Instrumentation de performance (logs ms/frame)

#### Configuration
- ✅ Classe `Config` pour paramètres runtime
- ✅ Distance maximale des rayons configurable
- ✅ Taille de pas des rayons ajustable
- ✅ Option saut des blocs d'air pour performance
- ✅ Brouillard activable/désactivable

### 🎛️ Contrôles

| Touche | Action |
|--------|--------|
| F6     | Basculer Mesh ↔ Ray Renderer |
| F3+G   | Bordures de chunks (mesh seulement) |

### ⚡ Performance

Le logging de performance est activé par défaut et affiche:
- Temps de frame en millisecondes
- FPS moyen
- Type de renderer actif

### 🚧 Limitations Actuelles

- **GPU**: Implémentation CPU seulement (TODO: version GPU/shader)
- **Occlusion**: Pas d'occlusion avancée ou d'ombres
- **Transparence**: Pas de support des blocs transparents/fluides
- **Multi-threading**: Pas de parallélisation des rayons
- **Textures**: Couleurs de blocs simplifiées (pas de lookup texture complète)
- **Particules**: Système de particules non implémenté pour le ray renderer

### 🔮 TODOs Futurs

#### Optimisations
- [ ] **GPU Compute**: Implémentation shader/compute pour parallélisation massive
- [ ] **Packet rays**: Rayons cohérents groupés pour efficacité
- [ ] **Early exit**: Plans de sortie anticipée et volumes englobants
- [ ] **Multi-threading**: Parallélisation CPU des rayons

#### Rendu Avancé
- [ ] **Occlusion**: Voxel shadow mapping ou cone tracing
- [ ] **Transparence**: Support des fluides et blocs transparents  
- [ ] **Textures**: Lookup complet dans l'atlas de textures
- [ ] **Éclairage**: Système d'éclairage plus sophistiqué
- [ ] **Post-processing**: Effets visuels (bloom, anti-aliasing, etc.)

#### Systèmes
- [ ] **Particules**: Système de particules compatible ray renderer
- [ ] **UI**: Rendu UI optimisé pour le ray renderer
- [ ] **Audio**: Intégration audio spatiale avec ray tracing

### 🏗️ Architecture

```
IRenderer (interface)
├── MeshRenderer (traditionnel)
│   ├── Génération de mesh greedy/non-greedy
│   ├── Rendu OpenGL avec VAO/VBO
│   └── Cache de mesh par chunk
│
└── VoxelRayRenderer (nouveau)
    ├── Algorithme DDA 3D pour traversée
    ├── Buffer de couleurs CPU
    ├── Rendu quad plein écran avec texture
    └── Éclairage et brouillard simple

RendererFactory
├── Gestion hot-swap des renderers
├── Transfert d'état (camera, world, hotbar)
└── Nettoyage de ressources

Config (statique)
├── Flag useVoxelRayRenderer
├── Paramètres ray renderer
└── Options de performance
```