package org.example.parser;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class SourceParser {

    public static CompilationUnit parseFile(Path javaFile) throws Exception {
        // Lit le contenu du fichier Java sous forme de texte
        String code = Files.readString(javaFile, StandardCharsets.UTF_8);

        //un parseur JDT configuré pour la version Java 21
        ASTParser parser = ASTParser.newParser(AST.JLS11);

        // Fournit le code source au parseur
        parser.setSource(code.toCharArray());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setResolveBindings(false);

        // Génère l’arbre syntaxique abstrait (AST)
        return (CompilationUnit) parser.createAST(null);
    }
}
