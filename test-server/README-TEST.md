# Serveur de test local (Paper 1.20.1)

Ce dossier contient un serveur Paper 1.20.1 pré-configuré pour tester le plugin sans toucher à un vrai serveur.

## Démarrage rapide

**Windows :** double-cliquez sur `run-test-server.bat` à la racine du projet.
**Linux :** `./run-test-server.sh` (rendez-le exécutable : `chmod +x run-test-server.sh`).

Le script fait tout automatiquement :
1. Vérifie Java 17 (requis pour Minecraft 1.20.1).
2. Télécharge `paper.jar` (build 196) si manquant.
3. Build le plugin (`mvn clean package -DskipTests`).
4. Copie le jar dans `test-server/plugins/`.
5. Lance le serveur sur `localhost:25565`.

## Connexion avec les clients

Comptes de test (mode hors-ligne, déjà OP niveau 4 via `ops.json`) :
- Client 1 : `Fourty3000`
- Client 2 : `ItsRealNocturne`

1. Lancez Minecraft **1.20.1** (launcher officiel, Prism, etc.).
2. Multijoueur → Ajouter un serveur → adresse `localhost:25565`.
3. Connectez-vous avec le pseudo `Fourty3000` sur le premier client, `ItsRealNocturne` sur le second.
4. Le serveur est en `online-mode=false` : les pseudos hors-ligne sont acceptés (pratique pour tester à 2 comptes). Réservé au test local, ne pas exposer sur internet.
5. Les deux comptes sont déjà opérateurs : pas besoin de taper `op` en console (les `/dungeon admin ...` fonctionnent directement).
6. Testez : `/dungeon party create`, `/dungeon instance manaria`, etc.
7. Arrêt propre : tapez `stop` dans la console (jamais en fermant la fenêtre).

## Contenu du dossier

| Fichier | Rôle |
|---|---|
| `paper.jar` | Serveur Paper 1.20.1 (téléchargé, ignoré par git) |
| `server.properties` | Config test : port 25565, hors-ligne, créatif, vols autorisés |
| `eula.txt` | `eula=true` (acceptation pour le test local) |
| `ops.json` | Les 2 comptes de test déjà OP (versionné) |
| `plugins/` | Le jar du plugin y est copié à chaque run |

## Données générées (ignorées par git)

`world/`, `world_nether/`, `world_the_end/`, `logs/`, `cache/`, `templates-dungeons/`, `plugins/DungeonInstances/`, instances `instance_*` / `editmode_*`.

Pour repartir de zéro : arrêtez le serveur (`stop`) puis supprimez `world*` et `templates-dungeons`, ils seront régénérés au prochain démarrage (le template `manaria` par défaut est réinstallé automatiquement).
