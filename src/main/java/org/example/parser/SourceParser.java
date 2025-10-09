package org.example.parser;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class SourceParser {

    public static CompilationUnit parseFile(Path javaFile) throws Exception {
        String code = Files.readString(javaFile, StandardCharsets.UTF_8);

        ASTParser parser = ASTParser.newParser(AST.JLS11);
        parser.setSource(code.toCharArray());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setResolveBindings(false);

        return (CompilationUnit) parser.createAST(null);
    }
}
