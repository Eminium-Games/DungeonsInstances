# DungeonInstances

Plugin Minecraft Spigot/Bukkit pour gérer des donjons instanciés, avec système de party, invitations cliquables, commandes d'administration et HUD scoreboard en jeu.

--

## Présentation

`DungeonInstances` permet de créer des mondes-donjon copiés depuis des templates, d'y inviter des joueurs en party, et d'afficher un scoreboard dynamique lorsque vous êtes dans une instance. Le plugin vise une administration simple (création/suppression/édition de templates, setspawn) et une UX conviviale (messages colorés, boutons cliquables pour accepter/refuser une invitation).

## Fonctions principales
- Création d'instances basées sur des templates (dossiers sous `templates-dungeons/`).
- Système de party avec invitations cliquables (`[Accepter]`, `[Refuser]`).
- Commandes d'administration : charger/éditer/sauvegarder/purger des instances, définir des spawn points.
- Scoreboard visible dans les mondes d'instance (`instance_<nom>_<uuid>`) affichant :
  - Membres du party (leader marqué par `♛`, vous marqué par `★`).
  - Flèches directionnelles indiquant la position relative des membres.
  - Santé courante en couleur (vert/jaune/rouge).
  - Membres hors-ligne affichés strikethrough.

## Installation

1. Build :

```bash
cd /path/to/DungeonsInstances
mvn clean package -DskipTests -T 1C
```

ou utilisez le script `./build` si vous l'avez rendu exécutable.

2. Déploiement : copiez le JAR généré dans le dossier `plugins/` de votre serveur Spigot/Paper :

```bash
cp "target/Dungeon Instances-1.0.0.jar" /path/to/server/plugins/
```

3. Redémarrez le serveur.

## Structure des dossiers importants
- `templates-dungeons/` : dossiers de templates (un dossier = un template de donjon). Si le dossier n'existe pas, le plugin créera automatiquement un répertoire et y installera le donjon par défaut *manaria* depuis son archive interne.
- `plugins/DungeonInstances/spawnPoints.json` : points de spawn définis via `/dungeon admin setspawn`.

## Commandes

Toutes les commandes sont sous `/dungeon`.

- `/dungeon instance <template> [difficulté]`
  - Crée une instance depuis le template avec l'un des niveaux de difficulté suivants : **Débutant**, **Normal** (par défaut), **Héroïque**, **Mythique**.
    La difficulté applique des multiplicateurs sur la vie, les dégâts et l'armure des créatures dérivées du template.
  - Le joueur (chef de party) et ses membres reçoivent un message et sont téléportés après quelques secondes.

- `/dungeon leave`
  - Quitte l'instance et restaure l'état normal.

- `/dungeon list`
  - Liste les templates disponibles et/ou instances actives.

- `/dungeon admin <sub>` (permission requise : `dungeon.admin`)
  - `edit <template>` : crée/ouvre une instance d'édition pour un template.
  - `save <instance>` : sauvegarde une instance modifiée (utilisé pour templates d'édition).
  - `purge <instance>` : supprime et décharge une instance vide.
  - `setspawn <template>` : enregistre la position actuelle du joueur comme spawn pour ce template (sauvegardé dans `spawnPoints.json`).

- `/dungeon party <sub>`
- `/dungeon party <sub>`
  - `create <name?>` : crée un nouveau party. Si aucun nom n'est fourni, le plugin utilise le pseudo du créateur (les espaces sont remplacés par des underscores). Les noms doivent être uniques — la création échouera si le nom existe déjà.
  - `invite <player>` : invite un joueur (envoi un message cliquable `[Accepter]`/`[Refuser]`).
  - `accept` : accepte la dernière invitation. L'accept provoque automatiquement la sortie du joueur de toute autre party existante avant de rejoindre la nouvelle.
  - `decline` : décline la dernière invitation.
  - `leave` : quitte le party. Si le joueur se trouve dans une instance (`instance_...`), il sera téléporté automatiquement vers son monde précédent (si connu) ou vers le spawn principal.
  - `kick <player>` : (leader uniquement) expulse un membre du groupe. Si le membre expulsé se trouve dans une instance, il est téléporté vers son monde précédent.
  - `disband` : (leader uniquement) dissout le groupe — notifie tous les membres, supprime les invitations en attente et téléporte les membres présents en instance vers leur monde précédent.
  - `list` : liste les parties / membres.
  - `members` : affiche les membres du party (Leader/Vous, online/offline).

Notes :
- Les invitations utilisent l'API TextComponent pour fournir des boutons cliquables côté client.
- Les commandes d'administration sont masquées aux joueurs sans permission.

## Scoreboard (HUD)

- Le scoreboard s'active automatiquement pour les joueurs présents dans un monde dont le nom commence par `instance_`.
- Il affiche en sidebar :
  - Le nom du donjon (extrait du nom du monde `instance_<nom>_<uuid>`),
  - Les membres du party avec statut santé, direction relative, leader/star markers,
  - Séparateurs uniformes (header/footer) de couleur cohérente.

## Spawn points

- Définissez un spawn par template avec :

```
/dungeon admin setspawn <template>
```

- Les spawn sont persistés dans `plugins/DungeonInstances/spawnPoints.json`.

## Templates & Instances

- Placez vos templates de donjon dans `templates-dungeons/<nom_template>/`.
- Les instances sont créées par copie et chargées sous un nom `instance_<nom_template>_<uuid>`.
- Les instances vides sont automatiquement déchargées et supprimées.

## Permissions

- `dungeon.admin` : accès aux commandes d'administration (`/dungeon admin ...`).

Autres commandes n'ont pas de permissions spécifiques par défaut (vous pouvez ajouter des checks si nécessaire).

## Dépannage

- Si une instance ne se charge pas, vérifiez les permissions d'écriture sur le dossier world container (copie de dossier template).
- Si le scoreboard n'apparaît pas : assurez-vous d'être dans un monde `instance_...` et d'appartenir à un party.
- Si les noms de joueurs hors-ligne s'affichent mal, le plugin utilise `Bukkit.getOfflinePlayer(UUID).getName()` comme fallback.

## Loot tables personnalisées

La version récente de DungeonInstances introduit un système de loot tables par donjon/difficulté.  Chaque mob peut être marqué
avec un **alias de pool** qui détermine la table de butin utilisée lorsque le mob meurt dans
une instance.  L'alias est stocké en NBT (PersistentDataContainer) et est conservé lors des
sauvegardes/chargements de mob.

### Configuration

Le fichier de configuration se trouve dans `plugins/DungeonInstances/lootTables.json`.  Si le
module est chargé pour la première fois, un fichier vide `{}` est créé afin que les
administrateurs puissent le remplir à la main.  La structure attendue est la suivante :

```json
{
  "<nom_template>": {
    "<difficulty>": {
      "<alias>": {
        "iterations": <nombre de lancers>,
        "loots": [
          {
            "item": "minecraft:diamond_sword",
            "nbt": { "item_name": "Épée légendaire" },
            "count": 1,
            "chance": 0.1
          },
          {
            "item": "minecraft:iron_nugget",
            "nbt": { "item_name": "Écu" },
            "count": 1,
            "chance": 0.4
          }
        ]
      }
    }
  }
}
```

Pour illustrer l’usage des quatre difficultés, voici un exemple complet pour
un template `manaria` et un alias `default` :

```json
{
  "manaria": {
    "BEGINNER": {
      "default": {
        "iterations": 2,
        "loots": [
          {"item":"minecraft:emerald","nbt":{},"count":1,"chance":0.2},
          {"item":"minecraft:gold_nugget","nbt":{},"count":2,"chance":0.5}
        ]
      }
    },
    "NORMAL": {
      "default": {
        "iterations": 3,
        "loots": [
          {"item":"minecraft:emerald","nbt":{},"count":1,"chance":0.4},
          {"item":"minecraft:gold_ingot","nbt":{},"count":1,"chance":0.25},
          {"item":"minecraft:iron_nugget","nbt":{},"count":3,"chance":0.6}
        ]
      }
    },
    "HEROIC": {
      "default": {
        "iterations": 4,
        "loots": [
          {"item":"minecraft:diamond","nbt":{},"count":1,"chance":0.05},
          {"item":"minecraft:gold_ingot","nbt":{},"count":2,"chance":0.4},
          {"item":"minecraft:iron_ingot","nbt":{},"count":5,"chance":0.8}
        ]
      }
    },
    "MYTHIC": {
      "default": {
        "iterations": 5,
        "loots": [
          {"item":"minecraft:nether_star","nbt":{},"count":1,"chance":0.01},
          {"item":"minecraft:diamond","nbt":{},"count":2,"chance":0.2},
          {"item":"minecraft:gold_ingot","nbt":{},"count":4,"chance":0.5}
        ]
      }
    }
  }
}
```

Dans cet exemple la difficulté croissante augmente à la fois le nombre
`iterations` et les chances de gagner des objets précieux ; le lecteur pourra
bien sûr adapter ces chiffres à ses propres besoins.


- `iterations` : nombre de fois où l'on visite la pool ; chaque itération tire un item
  au hasard parmi la liste `loots`.
- `loots` : tableau d'entrées possibles. Pour chaque entrée le champ `chance` est comparer
  à un réel aléatoire (`0.0`–`1.0`) ; si le tirage est réussi, l'item est ajouté aux drops.
- `item` : identifiant de matériau Bukkit/Spigot (`DIAMOND_SWORD`, `IRON_NUGGET`, etc.).
- `nbt` : mappage libre permettant de définir un nom (`item_name`), une lore, etc.  Seules
  quelques clés simples sont gérées pour l'instant (voir code source).
- `count` : quantité d'items à générer lorsque l'entrée est choisie.

### Assignation d'alias

Pendant l'édition d'un template vous pouvez regarder un mob et
exécuter :

```
/dungeon admin alias <alias>
```

Le plugin indiquera l'alias actuel si aucun argument n'est fourni.  Pour effacer l'alias,
utilisez `none` :

```
/dungeon admin alias none
```

L'alias est sauvegardé avec le mob et restauré automatiquement dans les instances.

### Reload de la configuration

Après avoir édité `lootTables.json` sur le disque, chargez les changements en jeu avec :

```
/dungeon admin reloadloot
```

### Comportement en jeu

Lorsqu'un mob portant un alias meurt dans une instance, les drops vanilla sont supprimés et
remplacés par les objets calculés à partir de la pool correspondante (template +
difficulté + alias).  Si aucune table n'est trouvée, le comportement vanilla reste inchangé.

## Personnalisation & suggestions

- Vous pouvez modifier la fréquence de mise à jour du scoreboard (`DungeonScoreboardManager`) et les formats de messages dans le code.
- Améliorations possibles : traductions multi-langues, configuration via `config.yml`, options pour cacher certains éléments du HUD.
