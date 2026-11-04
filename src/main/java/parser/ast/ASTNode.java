// Declara o pacote que contém a hierarquia da árvore sintática abstrata.
package parser.ast;

// Representa a classe base comum a comandos e expressões Jaca.
public abstract class ASTNode {
    // Permite que qualquer visitor escolha seu próprio tipo de resultado.
    public abstract <T> T accept(ASTVisitor<T> visitor);
}
