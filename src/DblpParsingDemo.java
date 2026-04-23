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
                long count = 0;
                HashMap<String, String> tree = new HashMap<>();
                HashMap<String, Integer> sizes = new HashMap<>();

                try (DblpPublicationGenerator gen = new DblpPublicationGenerator(xmlPath, dtdPath, 256)) {
                    // Boucle de consommation : on traite les publications une par une,
                    // jusqu'à atteindre la limite (si fournie) ou la fin du fichier.
                    while (pubCount < limit) {
                        if (count == 10000) {
                            System.out.println("Le nombre de communautés est de " + sizes.size());
                            sizes.entrySet().stream()
                                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                                    .limit(10)
                                    .forEach(e -> System.out.println("La communauté de " + e.getKey() + " contient " + e.getValue() + " auteurs"));
                            count = 0;
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
                        if (others.isEmpty()) {
                            boolean author1AlreadyExist = tree.containsKey(author1);

                            if (!author1AlreadyExist) {
                                tree.put(author1, author1);
                                sizes.put(author1, 1);
                            }
                        }
                        for (String author2 : others) {
                            union(author1, author2, tree, sizes);
                        }
                        count++;
                    }
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

                        for (String other : others) {
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
                    relations.values().forEach(voisins -> voisins.entrySet().removeIf(entry -> entry.getValue() < 6));
                    relations.entrySet().removeIf(entry -> entry.getValue().isEmpty());

                    HashMap<String, List<String>> graph = new HashMap<>();

                    for (Map.Entry<String, HashMap<String, Integer>> entry : relations.entrySet()) {
                        String A = entry.getKey();
                        graph.putIfAbsent(A, new ArrayList<>());

                        for (String B : entry.getValue().keySet()) {
                            graph.get(A).add(B);
                        }
                    }
                    List<List<String>> result = kosaraju(graph);
                    System.out.println(result);
                }
        }
    }

    public static void fillOrder(String author, Set<String> alreadyVisited, Stack<String> stack, HashMap<String, List<String>> graph) {
        alreadyVisited.add(author);

        // regarde les voisins de l'auteur pour continuer le dfs récursivement
        for (String s : graph.getOrDefault(author, List.of())) {
            if (!alreadyVisited.contains(s)) {
                fillOrder(s, alreadyVisited, stack, graph);
            }
        }

        stack.push(author);
    }

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

    public static List<List<String>> kosaraju(HashMap<String, List<String>> graph) {

        Stack<String> stack = new Stack<>();
        Set<String> visited = new HashSet<>();

        //dfs sur tous les auteurs
        for (String node : graph.keySet()) {
            if (!visited.contains(node)) {
                fillOrder(node, visited, stack, graph);
            }
        }

        HashMap<String, List<String>> reversed = reverseGraph(graph);

        visited.clear();
        List<List<String>> sccs = new ArrayList<>();

        while (!stack.isEmpty()) {
            String node = stack.pop();

            if (!visited.contains(node)) {
                List<String> component = new ArrayList<>();
                dfs(node, visited, reversed, component);
                sccs.add(component);
            }
        }
        //Permet de retirer les communautés de taille 1 pour plus de visibilité
        sccs.removeIf(scc -> scc.size() == 1);
        return sccs;
    }

    public static String find(String i, HashMap<String, String> tree, HashMap<String, Integer> sizes) {
        boolean alreadyExist = tree.containsKey(i);

        if (!alreadyExist) {
            tree.put(i, i);
            sizes.put(i, 1);
            return i;
        }
        String current = i;
        // Tant que l'on n'est pas à la racine on continue à remonter l'arbre
        while (!tree.get(current).equals(current)) {
            current = tree.get(current);
        }
        String root = current;

        // Permet de ne plus devoir faire tout le chemin pour remonter l'arbre la prochaine fois
        tree.replace(i, root);
        return root;
    }

    public static void union(String author1, String author2, HashMap<String, String> tree, HashMap<String, Integer> sizes) {
        if (!tree.containsKey(author2)) {
            String root1 = find(author1, tree, sizes);

            tree.put(author2, root1);
            sizes.put(root1, sizes.get(root1) + 1);

            return;
        }

        if (!tree.containsKey(author1)) {
            String root2 = find(author2, tree, sizes);

            tree.put(author1, root2);
            sizes.put(root2, sizes.get(root2) + 1);

            return;
        }

        String root1 = find(author1, tree, sizes);
        String root2 = find(author2, tree, sizes);

        if (!root1.equals(root2)) {
            int size1 = sizes.get(root1);
            int size2 = sizes.get(root2);

            if (size1 >= size2) {
                tree.put(root2, root1);
                sizes.put(root1, size1+size2);
                sizes.remove(root2);
            } else {
                tree.put(root1, root2);
                sizes.put(root2, size1+size2);
                sizes.remove(root1);
            }
        }
    }

    public static void exporterCSV(HashMap<String, Integer> tailles) throws IOException {
        PrintWriter writer = new PrintWriter(new File("data.csv"));
        writer.println("Auteur,NbCollaborations"); // Entête attendue par ton script

        // Trier les racines par taille décroissante et prendre les 10 premières
        tailles.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .forEach(e -> writer.println(e.getKey() + "," + e.getValue()));

        writer.close();
    }
}