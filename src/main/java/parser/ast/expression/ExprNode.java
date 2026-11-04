// Declara o pacote dos nós de expressão Jaca.
package parser.ast.expression;

// Importa a classe base compartilhada por todos os nós da AST.
import parser.ast.ASTNode;

// Agrupa nós que produzem valores quando visitados.
public abstract class ExprNode extends ASTNode {
    // Mantém o tipo de expressão distinto dos comandos por convenção.
}
