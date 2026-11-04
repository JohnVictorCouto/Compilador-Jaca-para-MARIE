// Declara o pacote que contém os nós de comandos compostos.
package parser.ast.statement;

// Importa o tipo de lista que mantém a ordem dos comandos.
import java.util.List;
// Importa a classe base dos nós da AST.
import parser.ast.ASTNode;
// Importa o contrato genérico dos visitors.
import parser.ast.ASTVisitor;

// Representa uma sequência ordenada de comandos, inclusive bloco vazio.
public class BlockNode extends ASTNode {
    // Guarda os comandos na ordem em que devem ser visitados.
    public List<ASTNode> statements;

    // Cria um bloco a partir da lista produzida pela gramática CUP.
    public BlockNode(List<ASTNode> stmts) {
        // Mantém a lista de nós recebida sem reordená-la.
        this.statements = stmts;
    }

    // Encaminha a visita ao método de bloco do visitor.
    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        // O visitor percorre os comandos ou concatena suas instruções.
        return visitor.visit(this);
    }
}
