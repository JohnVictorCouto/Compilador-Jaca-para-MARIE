// Declara o pacote que contém os nós condicionais.
package parser.ast.statement;

// Importa a classe base dos nós da AST.
import parser.ast.ASTNode;
// Importa o contrato dos visitors.
import parser.ast.ASTVisitor;
// Importa o tipo das expressões que produzem a condição.
import parser.ast.expression.ExprNode;

// Representa um comando if com bloco then obrigatório e else opcional.
public class IfNode extends ASTNode {
    // Guarda a expressão que decide qual ramo será executado.
    public ExprNode condition;
    // Guarda o bloco executado quando a condição for verdadeira.
    public BlockNode thenBlock;
    // Guarda o bloco alternativo ou null quando não existe else.
    public BlockNode elseBlock;

    // Constrói o nó condicional com condição e blocos.
    public IfNode(ExprNode cond, BlockNode thenB, BlockNode elseB) {
        // Armazena a condição para avaliação posterior.
        this.condition = cond;
        // Armazena o ramo verdadeiro.
        this.thenBlock = thenB;
        // Armazena o ramo falso opcional.
        this.elseBlock = elseB;
    }

    // Encaminha a visita ao método de seleção condicional.
    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        // O visitor avalia a condição ou gera saltos de controle.
        return visitor.visit(this);
    }
}