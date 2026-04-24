import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.*;
import java.util.*;

/**
 * Usage:
 *   java -Xmx2g DblpParsingDemo <dblp.xml|dblp.xml.gz> <dblp.dtd> [--limit=1000000]
 */
public class DblpParsingDemo {

    static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("""
                Usage:
                  java -Xmx2g DblpParsingDemo <dblp.xml|dblp.xml.gz> <dblp.dtd> [--limit=1000000]

                Exemple:
                  java -Xmx2g DblpParsingDemo dblp.xml.gz dblp.dtd --limit=500000
                """);
            System.exit(2);
        }

        Path xmlPath = Paths.get(args[0]);
        Path dtdPath = Paths.get(args[1]);
        String choice = args[2];

        long limit = Long.MAX_VALUE; // optionnel: s'arrêter après N publications
        for (int i = 2; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--limit=")) limit = Long.parseLong(a.substring("--limit=".length()));
        }

        if (!Files.exists(xmlPath)) throw new FileNotFoundException("XML introuvable: " + xmlPath);
        if (!Files.exists(dtdPath)) throw new FileNotFoundException("DTD introuvable: " + dtdPath);

        // --------------------------------------------------------------------
        // IMPORTANT : limites d'expansion d'entités XML
        // --------------------------------------------------------------------
        // DBLP utilise un DTD qui définit beaucoup d'entités.
        // Le parseur XML de Java impose par défaut une limite sur le nombre
        // d'expansions d'entités pour se protéger d'attaques (type "Billion Laughs").
        //
        // Sur DBLP (fichier légitime), on dépasse souvent la limite par défaut (p.ex. 2500),
        // ce qui déclenche une erreur du type:
        //   JAXP00010001: The parser has encountered more than "2500" entity expansions...
        //
        // Ici, comme on parse un fichier connu + un DTD local (pas de réseau),
        // on désactive ces limites pour éviter l'erreur.
        //
        // À ne pas faire pour des XML non fiables.
        // --------------------------------------------------------------------
        System.setProperty("jdk.xml.entityExpansionLimit", "0");
        System.setProperty("jdk.xml.totalEntitySizeLimit", "0");
        System.setProperty("jdk.xml.maxGeneralEntitySizeLimit", "0");
        System.setProperty("jdk.xml.maxParameterEntitySizeLimit", "0");

        System.out.println("XML: " + xmlPath);
        System.out.println("DTD: " + dtdPath);
        if (limit != Long.MAX_VALUE) System.out.println("Limit: " + limit);

        switch (choice) {
            case "1":
                long pubCount = 0;
                long count = 0; // Compteur local pour déclencher un affichage périodique
                // Structures de données pour gérer l'algorithme Union-Find
                // tree permet de stocker l'arbre des parents (qui appartient à la communauté de qui).
                HashMap<String, String> tree = new HashMap<>();
                // sizes permet de garder en mémoire la taille de la communauté de chaque racine.
                HashMap<String, Integer> sizes = new HashMap<>();

                // --------------------------------------------------------------------
                // On crée le générateur DBLP dans un try-with-resources :
                //   - le constructeur démarre un thread de parsing en arrière-plan ;
                //   - les publications "parsé(e)s" sont déposées dans une file (queue) ;
                //   - gen.nextPublication() consomme cette file au fur et à mesure.
                //
                // Le try-with-resources garantit que gen.close() est appelé à la fin,
                // ce qui permet d'arrêter proprement le thread de parsing et de libérer
                // les ressources.
                // --------------------------------------------------------------------
                try (DblpPublicationGenerator gen = new DblpPublicationGenerator(xmlPath, dtdPath, 256)) {
                    // Boucle de consommation : on traite les publications une par une,
                    // jusqu'à atteindre la limite (si fournie) ou la fin du fichier.
                    while (pubCount < limit) {
                        // Toutes les 10 000 opérations de traitement de co-auteurs,
                        // on affiche des statistiques en temps réel pour suivre l'évolution.
                        if (count == 10000) {
                            System.out.println("Le nombre de communautés est de " + sizes.size());
                            // Trie les communautés par taille décroissante et affiche les 10 plus grandes
                            sizes.entrySet().stream()
                                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                                    .limit(10)
                                    .forEach(e -> System.out.println("La communauté de " + e.getKey() + " contient " + e.getValue() + " auteurs"));
                            count = 0; // Réinitialise le compteur d'affichage
                        }

                        // nextPublication() renvoie :
                        //   - Optional.of(pub) si une publication est disponible ;
                        //   - Optional.empty() si on a atteint la fin du flux (EOF).
                        //
                        // Cela évite d'utiliser null et oblige à gérer explicitement le cas EOF.
                        Optional<DblpPublicationGenerator.Publication> opt = gen.nextPublication();
                        if (opt.isEmpty()) break; // EOF;
                        pubCount++;
                        DblpPublicationGenerator.Publication p = opt.get();

                        List<String> authors = p.authors;
                        if (authors == null || authors.isEmpty()) {
                            continue;
                        }

                        int k = authors.size();
                        // 1er auteur
                        String author1 = authors.get(0);

                        // autres auteurs (peut être vide si k == 1)
                        List<String> others = (k > 1) ? authors.subList(1, k) : List.of();

                        // Cas où l'auteur a publié seul (pas de co-auteurs sur cet article)
                        if (others.isEmpty()) {
                            boolean author1AlreadyExist = tree.containsKey(author1);

                            // S'il n'existe pas encore dans notre structure, on l'ajoute
                            // comme étant la racine d'une nouvelle communauté de taille 1.
                            if (!author1AlreadyExist) {
                                tree.put(author1, author1);
                                sizes.put(author1, 1);
                            }
                        }
                        // S'il y a des co-auteurs, on fusionne la communauté du premier auteur
                        // avec les communautés de tous les autres auteurs de la publication.
                        for (String author2 : others) {
                            union(author1, author2, tree, sizes);
                        }
                        count++; // On incrémente le compteur de traitements pour l'affichage périodique
                    }
                    // À la toute fin du traitement, on sauvegarde les résultats dans un fichier CSV.
                    exporterCSV(sizes);
                }
            case "2":
                long pubCount2 = 0;
                HashMap<String, HashMap<String, Integer>> relations = new HashMap<>();
                try (DblpPublicationGenerator gen = new DblpPublicationGenerator(xmlPath, dtdPath, 256)) {
                    // Boucle de consommation : on traite les publications une par une,
                    // jusqu'à atteindre la limite (si fournie) ou la fin du fichier.
                    while (pubCount2 < limit) {
                        // nextPublication() renvoie :
                        //   - Optional.of(pub) si une publication est disponible ;
                        //   - Optional.empty() si on a atteint la fin du flux (EOF).
                        //
                        // Cela évite d'utiliser null et oblige à gérer explicitement le cas EOF.
                        Optional<DblpPublicationGenerator.Publication> opt = gen.nextPublication();
                        if (opt.isEmpty()) break; // EOF;
                        pubCount2++;
                        DblpPublicationGenerator.Publication p = opt.get();

                        List<String> authors = p.authors;
                        if (authors == null || authors.isEmpty()) {
                            continue;
                        }

                        int k = authors.size();
                        // 1er auteur
                        String author1 = authors.get(0);

                        // autres auteurs (peut être vide si k == 1)
                        List<String> others = (k > 1) ? authors.subList(1, k) : List.of();
                        if (others.isEmpty()) {
                            continue;
                        }

                        getRelations(author1, others, relations);
                    }
                    removeRelationsWithNotEnoughAuthors(relations);
                    HashMap<String, List<String>> graph = createGraph(relations);

                    List<List<String>> result = kosarajuSharir(graph);

                    // Permet de récupérer les 10 premières communautés
                    List<List<String>> resultTop10 = result.stream()
                            .sorted((l1, l2) -> Integer.compare(l2.size(), l1.size()))
                            .limit(10)
                            .toList();

                    for (List<String> community : resultTop10) {
                        int taille = community.size();
                        int diametre = calculerDiametre(community, graph);
                        System.out.println(taille);
                        System.out.println(diametre);
                    }
                }
        }
    }

    /**
     * Méthode qui nous permet de mettre les relations entre les auteurs des différentes publications dans notre structure de données "relations". Ceci se fait de manière "online".
     * @param author1 auteur principal de la publication.
     * @param othersAuthors autres auteurs de la publication.
     * @param relations notre structure de données.
     */
    public static void getRelations(String author1, List<String> othersAuthors, HashMap<String, HashMap<String, Integer>> relations) {
        for (String other : othersAuthors) {
            if (!relations.containsKey(author1)) {
                HashMap<String, Integer> relationWithAuthor = new HashMap<>();
                relationWithAuthor.put(other, 1);
                relations.put(author1, relationWithAuthor);
            } else {
                HashMap<String, Integer> relationsAuthor1 = relations.get(author1);
                int value = relationsAuthor1.getOrDefault(other, 0);
                relationsAuthor1.put(other, value+1);
            }
        }
    }

    /**
     * Méthode qui nous permet de retirer les relations avec les auteurs qui sont apparues moins que 6 fois.
     * @param relations notre structure de données.
     */
    public static void removeRelationsWithNotEnoughAuthors(HashMap<String, HashMap<String, Integer>> relations) {
        relations.values().forEach(voisins -> voisins.entrySet().removeIf(entry -> entry.getValue() < 6));
        relations.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    /**
     * Méthode qui nous de transformer notre sd "relations" en une liste d'adjacence.
     * @param relations notre structure de données.
     * @return la structure de données "graph".
     */
    public static HashMap<String, List<String>> createGraph(HashMap<String, HashMap<String, Integer>> relations) {
        HashMap<String, List<String>> graph = new HashMap<>();

        for (Map.Entry<String, HashMap<String, Integer>> entry : relations.entrySet()) {
            String A = entry.getKey();
            graph.putIfAbsent(A, new ArrayList<>());

            for (String B : entry.getValue().keySet()) {
                graph.get(A).add(B);
            }
        }
        return graph;
    }

    /**
     * Méthode qui nous permet d'effectuer l'algorithme Kosaraju-Sharir.
     * @param graph notre stucture de données.
     * @return la liste de différentes communautés existantes dans une structure de données.
     */
    public static List<List<String>> kosarajuSharir(HashMap<String, List<String>> graph) {
        Deque<String> deque = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();

        //dfs sur tous les auteurs
        for (String node : graph.keySet()) {
            if (!visited.contains(node)) {
                fillOrder(node, visited, deque, graph);
            }
        }

        //Inversion du graphe
        HashMap<String, List<String>> reversed = reverseGraph(graph);

        visited.clear();
        List<List<String>> sccs = new ArrayList<>();

        //On refait un dfs mais pour le graphe inversé. Ce qui permet de savoir s'il y a à la fois une relation auteur1 -> auteur2 et auteur2 -> auteur1.
        while (!deque.isEmpty()) {
            String node = deque.pop();

            if (!visited.contains(node)) {
                List<String> component = new ArrayList<>();
                dfs(node, visited, reversed, component);
                sccs.add(component);
            }
        }
        //On retire les communautés de taille 1 pour plus de visibilité
        sccs.removeIf(scc -> scc.size() == 1);
        return sccs;
    }


    /**
     * Remplit l'ordre de visite via un dfs. C'est la première phase de l'algorithme Kosaraju
     *
     * @param author         Auteur actuel du parcours.
     * @param alreadyVisited Ensemble des auteurs déjà visités.
     * @param stack          Pile stockant les auteurs dans l'ordre.
     * @param graph          Le graphe orienté représenté par une liste d'adjacence.
     */
    public static void fillOrder(String author, Set<String> alreadyVisited, Deque<String> stack, HashMap<String, List<String>> graph) {
        alreadyVisited.add(author);

        // regarde les voisins de l'auteur pour continuer le dfs récursivement (si pas de voisins retourne une liste vide)
        for (String s : graph.getOrDefault(author, List.of())) {
            if (!alreadyVisited.contains(s)) {
                fillOrder(s, alreadyVisited, stack, graph);
            }
        }

        stack.push(author);
    }

    /**
     * Méthode qui permet d'inverser les arcs du graphe.
     * @param graph le graphe à inverser.
     * @return le graphe inversé.
     */
    public static HashMap<String, List<String>> reverseGraph(HashMap<String, List<String>> graph) {
        HashMap<String, List<String>> reversed = new HashMap<>();

        for (String u : graph.keySet()) {
            reversed.putIfAbsent(u, new ArrayList<>());

            for (String v : graph.get(u)) {
                reversed.putIfAbsent(v, new ArrayList<>());
                reversed.get(v).add(u);
            }
        }

        return reversed;
    }

    /**
     * Effectue un parcours DFS pour identifier tous les sommets pour un graphe inversé.
     *
     * @param node      Le sommet actuel du parcours.
     * @param visited   Ensemble des sommets déjà visités.
     * @param graph     Le graphe orienté inversé à parcourir.
     * @param component Liste collectant les membres de la composante fortement connexe.
     */
    public static void dfs(String node, Set<String> visited,
                           HashMap<String, List<String>> graph,
                           List<String> component) {

        visited.add(node);
        component.add(node);

        for (String neighbor : graph.getOrDefault(node, List.of())) {
            if (!visited.contains(neighbor)) {
                dfs(neighbor, visited, graph, component);
            }
        }
    }

    /**
     * Calcule le diamètre d'une communauté spécifique.
     *
     * @param communaute Liste des noms des auteurs de la communauté
     * @param graph      Le graphe orienté.
     * @return La valeur du diamètre qui correspond au nombre d'arêtes du chemin le plus long
     */
    public static int calculerDiametre(List<String> communaute, HashMap<String, List<String>> graph) {
        int maxDiametre = 0;
        // On transforme la liste en Set pour une recherche en O(1)
        Set<String> membres = new HashSet<>(communaute);

        for (String depart : communaute) {
            int d = bfsDistanceMax(depart, membres, graph);
            maxDiametre = Math.max(maxDiametre, d);
        }
        return maxDiametre;
    }

    /**
     * Réalise un parcours BFS pour trouver la distance maximale entre un auteur et un autre de la communauté.
     *
     * @param depart  L'auteur actuel pour le calcul des distances.
     * @param membres L'ensemble des membres de la communauté.
     * @param graph   Le graphe orienté.
     * @return La distance maximale trouvée depuis un auteur vers un autre auteur de la communauté.
     */
    private static int bfsDistanceMax(String depart, Set<String> membres, HashMap<String, List<String>> graph) {
        Queue<String> file = new LinkedList<>();
        Map<String, Integer> distances = new HashMap<>();

        file.add(depart);
        distances.put(depart, 0);
        int distMax = 0;

        while (!file.isEmpty()) {
            String u = file.poll();
            int d = distances.get(u);
            distMax = Math.max(distMax, d);

            //Renvoie une liste vide si on ne trouve pas l'auteur dans le graphe sinon il y a une erreur
            for (String v : graph.getOrDefault(u, List.of())) {
                if (membres.contains(v) && !distances.containsKey(v)) {
                    distances.put(v, d+1);
                    file.add(v);
                }
            }
        }
        return distMax;
    }

    /**
     * Recherche le représentant (la racine) de la communauté à laquelle appartient un auteur.
     * Si l'auteur n'existe pas encore dans la structure, il est créé en tant que racine de sa propre communauté.
     * <p>
     * Cette méthode implémente une optimisation d'aplatissement appelée "Compression de chemin"
     * (Path Compression). Lorsqu'on cherche la racine pour un auteur, on met à jour son parent
     * pour qu'il pointe directement vers cette racine. Cela accélère considérablement les
     * prochaines recherches pour cet auteur.
     * </p>
     *
     * @param i     Le nom de l'auteur dont on cherche la communauté.
     * @param tree  La structure Union-Find représentant l'arbre des parents (clé : auteur, valeur : parent).
     * @param sizes La map qui associe la racine d'une communauté à sa taille (nombre d'auteurs).
     * @return Le nom de l'auteur qui est à la racine de la communauté (le représentant).
     */
    public static String find(String i, HashMap<String, String> tree, HashMap<String, Integer> sizes) {
        boolean alreadyExist = tree.containsKey(i);

        // Cas initial : L'auteur n'est pas encore enregistré.
        // On l'ajoute à la structure comme étant sa propre racine (une communauté de 1 personne).
        if (!alreadyExist) {
            tree.put(i, i);
            sizes.put(i, 1);
            return i;
        }

        String current = i;

        // Parcours de l'arbre vers le haut :
        // Un nœud est une racine si son parent est lui-même.
        // Tant que le parent du nœud courant n'est pas le nœud courant, on continue de monter.
        while (!tree.get(current).equals(current)) {
            current = tree.get(current);
        }

        String root = current; // On a trouvé la racine absolue de la communauté

        // On modifie le parent direct de l'auteur "i" pour pointer directement sur la racine trouvée.
        // Cela permet de ne plus devoir refaire tout le chemin de la branche lors du prochain appel find(i).
        tree.replace(i, root);

        return root;
    }

    /**
     * Fusionne les ensembles (communautés) de deux auteurs en utilisant la structure de données Union-Find.
     * <p>
     * Cette méthode implémente l'optimisation "Union par taille" (Union by size).
     * Lorsqu'elle relie deux ensembles existants, elle attache toujours la racine
     * de l'arbre le plus petit à la racine de l'arbre le plus grand. Cela permet de
     * garder des arbres peu profonds et d'optimiser les futures opérations de recherche.
     * </p>
     *
     * @param author1 Le nom du premier auteur.
     * @param author2 Le nom du deuxième auteur (le collaborateur).
     * @param tree    La structure Union-Find représentant l'arbre des parents (clé : auteur, valeur : parent de l'auteur).
     * @param sizes   La map qui associe la racine d'une communauté au nombre total d'auteurs qu'elle contient.
     */
    public static void union(String author1, String author2, HashMap<String, String> tree, HashMap<String, Integer> sizes) {

        // Cas 1 : L'auteur 2 est un nouvel auteur qui n'est pas encore dans la structure.
        // On l'ajoute directement à la communauté de l'auteur 1 sans avoir à chercher sa propre racine.
        // Cela permet d'éviter de créer une communauté pour l'auteur 2 qui va être détruite directement après car il appartient à l'auteur 1
        if (!tree.containsKey(author2)) {
            String root1 = find(author1, tree, sizes);

            tree.put(author2, root1);
            sizes.put(root1, sizes.get(root1) + 1);

            return;
        }

        // Cas 2 : L'auteur 1 est un nouvel auteur.
        // On fait l'inverse : on l'ajoute directement à la communauté de l'auteur 2.
        // Cela permet d'éviter de créer une communauté pour l'auteur 1 qui va être détruite directement après car il appartient à l'auteur 2
        if (!tree.containsKey(author1)) {
            String root2 = find(author2, tree, sizes);

            tree.put(author1, root2);
            sizes.put(root2, sizes.get(root2) + 1);

            return;
        }

        // Cas 3 : Les deux auteurs existent déjà dans la structure.
        // On cherche le représentant (la racine) de la communauté de chacun.
        String root1 = find(author1, tree, sizes);
        String root2 = find(author2, tree, sizes);

        // Si les deux racines sont différentes, cela signifie qu'ils appartiennent
        // à deux communautés distinctes qu'il faut maintenant fusionner.
        if (!root1.equals(root2)) {
            int size1 = sizes.get(root1);
            int size2 = sizes.get(root2);

            // On accroche l'arbre le plus petit sous la racine du plus grand.
            if (size1 >= size2) {
                tree.put(root2, root1);
                sizes.put(root1, size1 + size2);
                sizes.remove(root2);
            } else {
                tree.put(root1, root2);
                sizes.put(root2, size1 + size2);
                sizes.remove(root1);
            }
        }
    }

    public static void exporterCSV(HashMap<String, Integer> tailles) throws IOException {
        PrintWriter writer = new PrintWriter(new File("data.csv"));
        writer.println("Auteur,NbCollaborations");

        // Trier les racines par taille décroissante et prendre les 10 premières
        tailles.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .forEach(e -> writer.println(e.getKey() + "," + e.getValue()));

        writer.close();
    }
}