// Declara o pacote que agrupa os contratos e os visitors da AST.
package parser.ast;

// Importa o nó de operação binária usado por expressões.
import parser.ast.expression.BinaryOpNode;
// Importa o nó para literais numéricos.
import parser.ast.expression.NumberNode;
// Importa o nó para menos unário.
import parser.ast.expression.UnaryMinusNode;
// Importa o nó para referências a variáveis.
import parser.ast.expression.VariableNode;
// Importa o nó para atribuições.
import parser.ast.statement.AssignNode;
// Importa o nó que representa blocos sequenciais.
import parser.ast.statement.BlockNode;
// Importa o nó de seleção condicional.
import parser.ast.statement.IfNode;
// Importa o nó de impressão.
import parser.ast.statement.PrintNode;
// Importa o nó de leitura.
import parser.ast.statement.ReadNode;
// Importa o nó de repetição.
import parser.ast.statement.WhileNode;

// Define os métodos que cada visitor deve implementar para todos os tipos de nó.
public interface ASTVisitor<T> {
    // Visita um bloco e devolve o tipo escolhido pelo visitor.
    T visit(BlockNode node);
    // Visita uma atribuição e devolve o tipo escolhido pelo visitor.
    T visit(AssignNode node);
    // Visita um if/else e devolve o tipo escolhido pelo visitor.
    T visit(IfNode node);
    // Visita um while e devolve o tipo escolhido pelo visitor.
    T visit(WhileNode node);
    // Visita um print e devolve o tipo escolhido pelo visitor.
    T visit(PrintNode node);
    // Visita um read e devolve o tipo escolhido pelo visitor.
    T visit(ReadNode node);
    // Visita um literal e devolve o tipo escolhido pelo visitor.
    T visit(NumberNode node);
    // Visita uma variável e devolve o tipo escolhido pelo visitor.
    T visit(VariableNode node);
    // Visita uma operação binária e devolve o tipo escolhido pelo visitor.
    T visit(BinaryOpNode node);
    // Visita uma negação unária e devolve o tipo escolhido pelo visitor.
    T visit(UnaryMinusNode node);
}
