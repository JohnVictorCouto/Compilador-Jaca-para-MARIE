// Declara o pacote que contém os nós numéricos da AST.
package parser.ast.expression;

// Importa o contrato genérico dos visitors da árvore.
import parser.ast.ASTVisitor;

// Representa um literal numérico reconhecido pelo scanner.
public class NumberNode extends ExprNode {
    // Guarda o literal como Double, conforme o contrato do interpretador-base.
    public Double value;

    // Constrói o nó com o valor produzido pelo token NUMBER.
    public NumberNode(Double val) {
        // Preserva o valor para interpretação ou conversão durante compilação.
        this.value = val;
    }

    // Encaminha a visita ao método específico para NumberNode.
    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        // O tipo dinâmico do nó escolhe a sobrecarga correta do visitor.
        return visitor.visit(this);
    }
}