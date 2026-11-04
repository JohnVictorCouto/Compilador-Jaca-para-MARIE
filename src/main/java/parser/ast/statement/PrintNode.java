// Declara o pacote que contém os nós de saída Jaca.
package parser.ast.statement;

// Importa a classe base dos nós da AST.
import parser.ast.ASTNode;
// Importa o contrato dos visitors.
import parser.ast.ASTVisitor;
// Importa o tipo das expressões impressas.
import parser.ast.expression.ExprNode;

// Representa o comando que imprime o resultado de uma expressão.
public class PrintNode extends ASTNode {
    // Guarda a expressão que será avaliada antes da saída.
    public ExprNode expr;

    // Constrói um nó de impressão com sua expressão.
    public PrintNode(ExprNode expr) {
        // Armazena a expressão para interpretação ou tradução posterior.
        this.expr = expr;
    }

    // Encaminha a visita para o método de impressão do visitor.
    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        // O visitor avalia a expressão e imprime ou emite OUTPUT.
        return visitor.visit(this);
    }
}