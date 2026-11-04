// Declara o pacote que contém as referências em expressões.
package parser.ast.expression;

// Importa o contrato genérico dos visitors da AST.
import parser.ast.ASTVisitor;

// Representa o uso de uma variável em uma expressão Jaca.
public class VariableNode extends ExprNode {
    // Guarda o identificador que será resolvido pelo visitor.
    public String name;

    // Cria uma referência para o identificador reconhecido pelo scanner.
    public VariableNode(String name) {
        // Conserva o nome original para mensagens e busca de valores.
        this.name = name;
    }

    // Encaminha a visita para o método de variável do visitor ativo.
    @Override
    public <T> T accept(ASTVisitor<T> visitor) {
        // O visitor decide se lê um valor ou gera uma instrução LOAD.
        return visitor.visit(this);
    }
}
