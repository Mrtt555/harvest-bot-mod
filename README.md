<<<<<<< HEAD
# 🌾 Harvest Bot Mod - Bot de Farming Automatique pour Minecraft

[![Fabric](https://img.shields.io/badge/Mod%20Loader-Fabric-blue)](https://fabricmc.net/)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.4-green)](https://minecraft.net/)
[![Java](https://img.shields.io/badge/Java-17%2B-orange)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

## 📖 Description

**Harvest Bot** est un mod Fabric sophistiqué qui automatise le farming dans Minecraft avec des mouvements naturels et intelligents. Ce bot intégré directement dans le jeu simule un joueur humain avec des techniques avancées de récolte optimisée.

### ✨ Fonctionnalités Principales

- **🤖 Bot de farming automatique** avec mouvements naturels
- **🔄 Système de zigzag intelligent** pour une récolte optimisée
- **🎯 Balayage de tête dynamique** (±30-35°) pour maximiser la collecte
- **⏰ Drop d'items automatique** avec timer configurable
- **📍 Configuration de champ rectangulaire** via 4 coins
- **💾 Sauvegarde d'état** pour reprendre le farming après une pause
- **🖥️ HUD intégré** avec informations en temps réel
- **⌨️ Contrôles par touches** entièrement configurables
- **🔄 Farming continu** même avec interfaces ouvertes (chat, inventaire)

### 🧠 Intelligence du Bot

- **Mouvements naturels** avec variations aléatoires de vitesse
- **Rotation progressive** avec acceleration/deceleration
- **Head bobbing** subtil simulant un joueur réel
- **Anti-détection** avec micro-variations et timing humain
- **Mining continu** même avec le chat ouvert
- **Gestion des coins** avec rotation précise

## 🎮 Contrôles par Défaut

| Touche | Action |
|--------|--------|
| **F6** | Démarrer/Reprendre le bot |
| **F7** | Mettre en pause le bot |
| **F8** | Définir les coins du champ (cycle 1→2→3→4) |
| **F9** | Basculer l'affichage du HUD |
| **F10** | Augmenter le délai de drop (+1s) |
| **F11** | Diminuer le délai de drop (-1s) |
| **F12** | Reset complet de l'état du bot |

## 🚀 Installation

### Prérequis

- **Java 17+** ([Télécharger](https://adoptium.net/))
- **Minecraft 1.21.4**
- **Fabric Loader** ([Installation](https://fabricmc.net/use/installer/))
- **Fabric API** ([Télécharger](https://modrinth.com/mod/fabric-api))

### Étapes d'installation

1. **Télécharger le mod**
   ```bash
   git clone https://github.com/Mrtt555/harvest-bot-mod.git
   cd harvest-bot-mod
   ```

2. **Compiler le mod**
   ```bash
   ./gradlew build
   ```

3. **Installer le fichier JAR**
   - Copier `build/libs/harvest-bot-mod-*.jar` dans votre dossier `mods/` de Minecraft
   - S'assurer que Fabric API est également présent dans `mods/`

4. **Lancer Minecraft**
   - Démarrer avec le profil Fabric
   - Créer ou rejoindre un monde

## 📋 Guide d'utilisation

### 1. Configuration du champ

1. Se positionner au **premier coin** de votre champ
2. Appuyer sur **F8** (message "Coin 1 défini")
3. Se déplacer au **deuxième coin** et appuyer sur **F8**
4. Répéter pour les coins 3 et 4

> **💡 Astuce** : Définissez les coins dans l'ordre suivant un rectangle pour un farming optimal

### 2. Démarrage du bot

1. S'assurer que les 4 coins sont définis
2. Appuyer sur **F6** pour démarrer le bot
3. Le bot se dirigera automatiquement vers le coin 1 et commencera la récolte

### 3. Configuration avancée

- **Timer de drop** : Utilisez F10/F11 pour ajuster entre 1s et 360s (6 minutes)
- **HUD** : F9 pour afficher/masquer les informations en temps réel
- **Pause/Reprise** : F7 met en pause, F6 reprend exactement où le bot s'était arrêté
- **Reset** : F12 remet à zéro pour recommencer depuis le coin 1

## 🖥️ Interface HUD

Le HUD affiche en temps réel :

- **Statut du bot** (Actif/Pause/Arrêté)
- **État actuel** (Vers coin 1/Récolte/Rotation)
- **Position du joueur** (coordonnées X/Z)
- **Configuration des coins** (nombre définis/4)
- **Timer de drop** avec temps restant
- **Contrôles** (rappel des touches)

## ⚙️ Configuration Technique

### Structure des États

Le bot utilise une machine à états sophistiquée :

- **GOING_TO_START** : Se dirige vers le coin 1
- **HARVESTING_ROW** : Récolte en zigzag vers le coin cible
- **TURNING_AT_CORNER** : Rotation précise au coin atteint
- **STOPPED** : Bot en pause (état sauvegardé)

### Paramètres de Mouvement

- **Vitesse de rotation** : 2-12°/tick (adaptative selon l'angle)
- **Balayage de tête** : 30-35° variables pour optimiser la récolte
- **Zigzag** : Changement de direction toutes les 600ms
- **Mining** : Actions toutes les 100ms (10/sec)

## 🛠️ Développement

### Structure du projet

```
src/
├── client/java/Mrtt555/
│   ├── HarvestBotClient.java     # Logique principale du bot
│   ├── HarvestBotKeybindings.java # Gestion des contrôles
│   └── HarvestBotHud.java        # Interface utilisateur
└── main/resources/
    ├── fabric.mod.json           # Configuration Fabric
    └── assets/harvest-bot-mod/
        └── lang/                 # Traductions
```

### Compilation

```bash
# Développement
./gradlew build

# Tests
./gradlew test

# Nettoyage
./gradlew clean
```

## 🐛 Résolution de problèmes

### Le bot ne démarre pas

- ✅ Vérifier que les 4 coins sont définis
- ✅ S'assurer d'être dans un monde (pas dans un menu)
- ✅ Consulter les logs : `[harvest-bot-mod] Starting Harvest Bot...`

### Le mining ne fonctionne pas

- ✅ Vérifier que vous tenez un outil approprié
- ✅ S'assurer d'être en mode Survie
- ✅ Redémarrer le bot avec F12 puis F6

### Le bot se bloque

- ✅ Appuyer sur F7 pour pause, puis F6 pour reprendre
- ✅ Utiliser F12 pour reset complet si nécessaire
- ✅ Vérifier que le terrain est dégagé entre les coins

## 📊 Logs et Débogage

Messages importants à surveiller :

```
[harvest-bot-mod] HarvestBot initialized
[harvest-bot-mod] Corner X set to (x, y, z)
[harvest-bot-mod] Starting bot from beginning
[harvest-bot-mod] Reached corner1, starting harvest
[harvest-bot-mod] Item dropped after Xs
```

## 🤝 Contribution

Les contributions sont les bienvenues ! Voici comment participer :

1. **Fork** le projet
2. Créer une **branche feature** (`git checkout -b feature/AmazingFeature`)
3. **Commit** vos changements (`git commit -m 'Add AmazingFeature'`)
4. **Push** sur la branche (`git push origin feature/AmazingFeature`)
5. Ouvrir une **Pull Request**

## 📜 Licence

Ce projet est sous licence **MIT**. Voir le fichier [LICENSE](LICENSE) pour plus de détails.

## 👨‍💻 Auteur

**Mrtt555**

- GitHub : [@Mrtt555](https://github.com/Mrtt555)
- Projet : [harvest-bot-mod](https://github.com/Mrtt555/harvest-bot-mod)

## ⚠️ Avertissement

Ce mod est conçu pour un usage personnel et éducatif. L'utilisation de bots peut être contraire aux règles de certains serveurs. Utilisez-le de manière responsable et respectez les règles du serveur sur lequel vous jouez.

---

**Bon farming ! 🌾**
=======
# harvest-bot-mod
Bot de farming automatique pour Minecraft avec mouvements naturels, zigzag intelligent et HUD intégré. Mod Fabric pour récolte optimisée.
>>>>>>> 932d68a472720dfa83b219a87d1c7f00317008d3
