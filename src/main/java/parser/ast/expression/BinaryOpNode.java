// Declara o pacote que contém as operações binárias da AST.
package parser.ast.expression;

// Importa o contrato genérico dos visitors.
import parser.ast.ASTVisitor;

// Representa dois operandos unidos por um operador binário.
public class BinaryOpNode extends ExprNode {
    // Guarda o identificador numérico do token de operador do CUP.
    public int op;
    // Guarda a expressão que aparece antes do operador.
    public ExprNode left;
    // Guarda a expressão que aparece depois do operador.
    public ExprNode right;

    // Constrói o nó com o token e as duas subárvores de operandos.
    public BinaryOpNode(int op, ExprNode l, ExprNode r) {
        // Preserva o operador para o interpretador ou compilador identificar.
        this.op = op;
        // Armazena o primeiro operando da expressão.
        this.left = l;
        // Armazena o segundo operando da expressão.
        this.right = r;
    }

    // Encaminha a visita ao método que trata BinaryOpNode.
    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        // O visitor interpreta ou traduz os dois operandos conforme o operador.
        return visitor.visit(this);
    }
}
