// Declara o pacote que contém os nós de expressão unária.
package parser.ast.expression;

// Importa o contrato genérico dos visitors.
import parser.ast.ASTVisitor;

// Representa a negação aritmética de uma expressão.
public class UnaryMinusNode extends ExprNode {
    // Guarda a subexpressão cujo resultado terá o sinal invertido.
    public ExprNode expr;

    // Constrói o nó unário em torno da expressão filha.
    public UnaryMinusNode(ExprNode expr) {
        // Armazena a subárvore para visita recursiva.
        this.expr = expr;
    }

    // Encaminha a visita para o método de menos unário.
    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        // O visitor calcula o oposto ou gera CLEAR e SUBT.
        return visitor.visit(this);
    }
}