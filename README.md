# Projet Algorithmique 2 - Détection de communautés dans DBLP

Ce projet porte sur l'analyse de la base de données DBLP afin d'y identifier des communautés de chercheurs via deux approches différentes, traitées en flux (online).

## Structure du projet

Les fichiers se trouvent principalement dans le répertoire `src/` :

- `DblpParsingDemo.java` : Le programme principal contenant l'implémentation des Tâches 1 (Détection par co-publication via algorithme Union-Find) et 2 (Communautés orientées via l'algorithme de Kosaraju).
- `script.py` : Script Python générant un histogramme interactif de la taille des communautés de la Tâche 1.
- `script2.py` : Script Python générant un histogramme interactif de la taille des communautés de la Tâche 2.

## Comment compiler le projet

Ouvrez un terminal, déplacez-vous dans le répertoire contenant les sources (soit le dossier `src`), et exécutez la commande `javac` pour compiler les fichiers `.java` :

```bash
cd src
javac DblpPublicationGenerator.java DblpParsingDemo.java
```

## Comment exécuter le programme

Le programme requiert 3 arguments obligatoires et 1 argument optionnel sous la syntaxe :
`java DblpParsingDemo <chemin_xml> <chemin_dtd> <tâche> [--limit=N]`

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

### Génération des histogrammes (Scripts Python)
À faire obligatoirement avant de générer les histogrammes

Pour Mac :
```bash
python3 -m pip install pandas
python3 -m pip install plotly
```
Pour Windows :
```bash
pip install pandas
pip install plotly
```
Pour lancer l'analyse graphique et afficher les histogrammes requis (nécessite d'avoir exécuté les tâches correspondantes au préalable pour générer `data.csv` et `data2.csv`, ainsi que les librairies `pandas` et `plotly`) :

Pour Mac :
```bash
python3 script.py   # Affiche l'histogramme pour la Tâche 1
python3 script2.py  # Affiche l'histogramme pour la Tâche 2
```
Pour Windows
```bash
python script.py   # Affiche l'histogramme pour la Tâche 1
python script2.py  # Affiche l'histogramme pour la Tâche 2
```

## Fichiers de sortie produits

- **Tâche 1 :**
  - Produit un affichage standard (dans la console) toutes les 500 000 publications traitées pour démontrer le suivi *online* (nombre total de communautés et le top 10 des plus grandes).
  - **`data.csv`** : Génère ce fichier dans le dossier d'exécution. Il contient la liste complète de toutes les communautés et de leur taille (sert de base pour le premier histogramme).
- **Tâche 2 :**
  - Produit un affichage à l'écran (console) des 10 plus grandes communautés fortement connexes trouvées (avec leur taille, leur diamètre et la liste complète de leurs auteurs).
  - **`data2.csv`** : Génère ce fichier contenant uniquement les tailles de l'ensemble des communautés (sert de base pour le second histogramme).
- **Scripts Python (`script.py` et `script2.py`) :**
  - Lisent respectivement `data.csv` et `data2.csv`. Ils ne produisent pas de fichier permanent, mais génèrent et ouvrent un rendu visuel HTML (histogrammes) temporaire dans le navigateur Web par défaut.