# Projet Algorithmique 2 - Détection de communautés dans DBLP

Ce projet porte sur l'analyse de la base de données DBLP afin d'y identifier des communautés de chercheurs via deux approches différentes, traitées en flux (online).

## Structure du projet

Les fichiers se trouvent principalement dans le répertoire `src/` :

- `DblpParsingDemo.java` : Le programme principal contenant l'implémentation des Tâches 1 (Détection par co-publication via algorithme Union-Find) et 2 (Communautés orientées via l'algorithme de Kosaraju).
- `script.py` : Script Python correspondant à la tâche bonus. Il génère un graphique interactif permettant de visualiser les tailles des plus grandes communautés.

## Comment compiler le projet

Ouvrez un terminal, déplacez-vous dans le répertoire contenant les sources (soit le dossier `src`), et exécutez la commande `javac` pour compiler les fichiers `.java` :

```bash
cd src
javac DblpPublicationGenerator.java DblpParsingDemo.java
```

## Comment exécuter le programme

Le programme requiert 3 arguments obligatoires et 1 argument optionnel sous la syntaxe :
`java -Xmx2g DblpParsingDemo <chemin_xml> <chemin_dtd> <tâche> [--limit=N]`

- `<chemin_xml>` : Le chemin vers le fichier de données (ex: `dblp-2026-01-01.xml.gz`).
- `<chemin_dtd>` : Le chemin vers le fichier `dblp.dtd`.
- `<tâche>` : Entrez `1` pour exécuter la **Tâche 1**, ou `2` pour exécuter la **Tâche 2**.
- `--limit=N` *(Optionnel)* : Permet d'arrêter le parsing après les `N` premières publications pour des tests plus rapides.

**Exemples d'exécution (depuis le dossier `src/`) :**

Exécuter la tâche 1 sur l'intégralité du fichier :
```bash
java DblpParsingDemo dblp-2026-01-01.xml.gz dblp.dtd 1
```

Exécuter la tâche 2 en se limitant aux 500 000 premières publications :
```bash
java DblpParsingDemo dblp-2026-01-01.xml.gz dblp.dtd 2 --limit=500000
```

### Exécution de la Tâche Bonus
Pour lancer l'analyse graphique des données avec Python, exécutez (assurez-vous d'avoir fait tourner la tâche 1 au préalable et d'avoir installé les librairies `pandas` et `plotly`) :
```bash
python script.py
```

## Fichiers de sortie produits

- **Tâche 1 :** 
  - Produit un affichage standard (dans la console) toutes les 10 000 publications traitées pour démontrer le suivi *online*, ainsi qu'un affichage de synthèse à la fin.
  - **`data.csv`** : Génère ce fichier dans le dossier d'exécution (la racine depuis laquelle la commande Java a été lancée). Il contient la liste des 10 plus grandes communautés avec le nom de leur représentant et leur taille.
- **Tâche 2 :** 
  - Produit l'affichage à l'écran (console) des composantes fortement connexes trouvées à l'issue de l'algorithme de Kosaraju sur le graphe épuré.
- **Tâche Bonus (`script.py`) :** 
  - Lit en entrée le fichier `data.csv`. Il ne produit pas de fichier permanent, mais génère et ouvre un rendu visuel HTML temporaire dans le navigateur Web par défaut.
