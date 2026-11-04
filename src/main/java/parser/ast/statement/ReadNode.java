// Declara o pacote que contém os nós de entrada Jaca.
package parser.ast.statement;

// Importa a classe base dos nós da árvore.
import parser.ast.ASTNode;
// Importa o contrato dos visitors.
import parser.ast.ASTVisitor;

// Representa uma leitura para um identificador Jaca.
public class ReadNode extends ASTNode {
    // Guarda o nome da variável que recebe o valor digitado.
    public String id;

    // Cria um nó de leitura para o identificador indicado.
    public ReadNode(String id) {
        // Conserva o nome para busca ou declaração da variável.
        this.id = id;
    }

    // Encaminha a visita para o método específico de leitura.
    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        // O visitor escolhe entre ler um valor ou emitir INPUT e STORE.
        return visitor.visit(this);
    }
}
