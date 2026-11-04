// Declara o pacote que contém os nós de repetição Jaca.
package parser.ast.statement;

// Importa a classe base dos nós da AST.
import parser.ast.ASTNode;
// Importa o contrato genérico dos visitors.
import parser.ast.ASTVisitor;
// Importa o tipo da expressão de condição.
import parser.ast.expression.ExprNode;

// Representa um laço que reavalia a condição antes de cada repetição.
public class WhileNode extends ASTNode {
    // Guarda a condição que controla a entrada e a continuação do laço.
    public ExprNode condition;
    // Guarda os comandos que serão repetidos.
    public BlockNode block;

    // Constrói o nó while com condição e corpo.
    public WhileNode(ExprNode cond, BlockNode block) {
        // Armazena a condição para avaliação pelo visitor.
        this.condition = cond;
        // Armazena o corpo que será executado enquanto a condição for verdadeira.
        this.block = block;
    }

    // Encaminha a visita para o método de repetição do visitor.
    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        // O visitor interpreta as repetições ou gera rótulos e saltos.
        return visitor.visit(this);
    }
}
