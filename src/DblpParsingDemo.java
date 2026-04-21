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
//                        System.out.println("Author : " + author1 + " size = " + sizes.get(author1));
//                        System.out.println("Author alone");
                            } else {
//                        System.out.println(author1 + " already exists");
                            }
                        }
                        for (String author2 : others) {
                            union(author1, author2, tree, sizes);
                        }
                        count++;
//                System.out.println(count);
//                System.out.println("------------------------------------");
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
                    long totalEdges = relations.values().stream()
                            .mapToLong(Map::size)
                            .sum();
                    System.out.println("Nombre total de relations orientées identifiées : " + totalEdges);

                    System.out.println("--- Top 10 des collaborateurs les plus fréquents (A -> B) ---");

                    relations.entrySet().stream()
                            .flatMap(entry -> entry.getValue().entrySet().stream()
                                    .map(subEntry -> new AbstractMap.SimpleEntry<>(entry.getKey() + " -> " + subEntry.getKey(), subEntry.getValue())))
                            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                            .limit(10)
                            .forEach(e -> System.out.println(e.getKey() + " : " + e.getValue() + " publications communes"));
                }
        }
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

//            System.out.println("Author " + author2 + " was new, directly attached to " + root1);
//            System.out.println("_______");
            return;
        }

        if (!tree.containsKey(author1)) {
            String root2 = find(author2, tree, sizes);
            tree.put(author1, root2);
            sizes.put(root2, sizes.get(root2) + 1);

//            System.out.println("Author " + author1 + " was new, directly attached to " + root2);
//            System.out.println("_______");
            return;
        }

        String root1 = find(author1, tree, sizes);
        String root2 = find(author2, tree, sizes);
//        System.out.println("Root for " + author1 + "= " + root1);
//        System.out.println("Root for " + author2 + "= " + root2);

        if (!root1.equals(root2)) {
//            System.out.println("Already in the same community");
//            System.out.println("After link");
//            System.out.println("Author : " + root1 + " size : " + sizes.get(root1));
            int size1 = sizes.get(root1);
            int size2 = sizes.get(root2);
//            System.out.println("Before link");
//            System.out.println("Author : " + root1 + " size : " + size1);
//            System.out.println("Author : " + root2 + " size : " + size2);
            if (size1 >= size2) {
                tree.put(root2, root1);
                sizes.put(root1, size1+size2);
                sizes.remove(root2);
            } else {
                tree.put(root1, root2);
                sizes.put(root2, size1+size2);
                sizes.remove(root1);
            }

//            System.out.println("After link");
//            System.out.println("Author : " + root1 + " size : " + sizes.get(root1));
        }
//        System.out.println("_______");
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