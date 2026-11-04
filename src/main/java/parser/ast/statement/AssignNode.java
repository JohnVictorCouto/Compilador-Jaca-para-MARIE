// Declara o pacote que contém os nós de comandos Jaca.
package parser.ast.statement;

// Importa a classe base da árvore.
import parser.ast.ASTNode;
// Importa o contrato dos visitors.
import parser.ast.ASTVisitor;
// Importa o tipo base das expressões usadas no lado direito.
import parser.ast.expression.ExprNode;

// Representa uma atribuição no formato identificador igual expressão.
public class AssignNode extends ASTNode {
    // Guarda o identificador que recebe o valor calculado.
    public String id;
    // Guarda a expressão avaliada antes de atualizar a variável.
    public ExprNode expression;

    // Constrói o nó de atribuição com destino e expressão.
    public AssignNode(String id, ExprNode expr) {
        // Armazena o identificador de destino.
        this.id = id;
        // Armazena a árvore da expressão do lado direito.
        this.expression = expr;
    }

    // Encaminha a visita para o método de atribuição do visitor.
    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        // O interpretador ou compilador define como executar a atribuição.
        return visitor.visit(this);
    }
}
