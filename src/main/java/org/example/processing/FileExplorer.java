package org.example.processing;

import java.nio.file.*;
import java.util.stream.*;
import java.util.*;

public class FileExplorer {
    public static List<Path> listJavaFiles(Path root) throws Exception {
        try (Stream<Path> s = Files.walk(root)) {

            // renvoie la liste finale des fichiers trouvés
            return s.filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());
        }
    }
}

