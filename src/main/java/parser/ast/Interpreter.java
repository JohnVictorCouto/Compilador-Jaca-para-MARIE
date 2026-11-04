// Declara o pacote que agrupa a AST e seus visitors.
package parser.ast;

// Importa a tabela de símbolos mantida durante a interpretação.
import java.util.HashMap;
// Importa o nó de operação binária.
import parser.ast.expression.BinaryOpNode;
// Importa o nó de literal numérico.
import parser.ast.expression.NumberNode;
// Importa o nó de menos unário.
import parser.ast.expression.UnaryMinusNode;
// Importa o nó de referência a variável.
import parser.ast.expression.VariableNode;
// Importa o nó de atribuição.
import parser.ast.statement.AssignNode;
// Importa o nó de bloco sequencial.
import parser.ast.statement.BlockNode;
// Importa o nó condicional.
import parser.ast.statement.IfNode;
// Importa o nó de saída.
import parser.ast.statement.PrintNode;
// Importa o nó de leitura.
import parser.ast.statement.ReadNode;
// Importa o nó de repetição.
import parser.ast.statement.WhileNode;
// Importa o leitor de linhas usado por read.
import java.io.BufferedReader;
// Importa o tipo de erro produzido pela leitura da entrada.
import java.io.IOException;
// Importa a entrada padrão do processo.
import java.io.InputStreamReader;
// Importa os identificadores dos tokens gerados pelo CUP.
import parser.sym;

// Executa a AST Jaca e devolve valores apenas para expressões.
public class Interpreter implements ASTVisitor<Value> {
    // Associa nomes Jaca aos valores calculados durante esta sessão.
    private final HashMap<String, Value> symbolTable = new HashMap<>();
    // Lê entradas do usuário sem recriar o leitor para cada comando.
    private final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

    // Executa os comandos de um bloco em ordem.
    @Override
    public Value visit(BlockNode node) {
        // Percorre cada comando que a gramática colocou na lista.
        for (ASTNode stmt : node.statements) {
            // Delega o comando ao visitor de acordo com o tipo concreto do nó.
            stmt.accept(this);
        }
        // Comandos alteram estado ou produzem saída, mas não retornam valor.
        return null;
    }

    // Avalia uma expressão e grava seu valor na tabela de símbolos.
    @Override
    public Value visit(AssignNode node) {
        // Calcula primeiro a expressão que aparece à direita da atribuição.
        Value value = node.expression.accept(this);
        // Cria ou atualiza o valor associado ao identificador de destino.
        symbolTable.put(node.id, value);
        // A atribuição não produz um valor para outra expressão.
        return null;
    }

    // Avalia a condição e executa exatamente um dos blocos do if.
    @Override
    public Value visit(IfNode node) {
        // Avalia a condição antes de decidir qual bloco será visitado.
        Value conditionValue = node.condition.accept(this);
        // Exige uma comparação booleana para selecionar um ramo.
        if (!conditionValue.isBoolean()) {
            // Interrompe a execução com uma mensagem para condição numérica inválida.
            throw new RuntimeException("Erro de Tipo: A condição do IF precisa ser um Booleano.");
        }
        // Executa o ramo then quando a condição é verdadeira.
        if (conditionValue.asBoolean()) {
            // Visita cada comando do ramo verdadeiro.
            node.thenBlock.accept(this);
        } else if (node.elseBlock != null) {
            // Visita o ramo else somente quando ele existe.
            node.elseBlock.accept(this);
        }
        // O comando condicional não produz um valor de expressão.
        return null;
    }

    // Reavalia a condição e executa o bloco enquanto ela permanecer verdadeira.
    @Override
    public Value visit(WhileNode node) {
        // Avalia a condição antes da primeira iteração.
        Value conditionValue = node.condition.accept(this);
        // Continua enquanto o valor avaliado for booleano verdadeiro.
        while (conditionValue.asBoolean()) {
            // Executa o corpo completo do laço.
            node.block.accept(this);
            // Reavalia a condição depois de atualizar o estado do programa.
            conditionValue = node.condition.accept(this);
        }
        // O comando while não retorna um valor de expressão.
        return null;
    }

    // Avalia uma expressão e imprime seu valor na saída padrão.
    @Override
    public Value visit(PrintNode node) {
        // Calcula a expressão que será impressa.
        Value value = node.expr.accept(this);
        // Usa a representação textual do wrapper Value.
        System.out.println(value);
        // O comando print não retorna valor para outra expressão.
        return null;
    }

    // Solicita um valor e o grava na variável indicada pelo nó.
    @Override
    public Value visit(ReadNode node) {
        // Exibe o nome da variável como prompt de entrada.
        System.out.print(node.id + "?: ");
        // Garante que o prompt apareça antes da leitura.
        System.out.flush();
        // Converte a linha digitada para booleano ou número.
        try {
            // Lê uma linha completa da entrada padrão.
            String input = reader.readLine();
            // Não atualiza a variável quando a entrada chega ao fim.
            if (input != null) {
                // Mantém o resultado parseado antes de atualizar a tabela.
                Value valueRead;
                // Aceita true e false sem diferenciar maiúsculas.
                if (input.equalsIgnoreCase("true") || input.equalsIgnoreCase("false")) {
                    // Converte o texto para o tipo Boolean do interpretador.
                    valueRead = new Value(Boolean.parseBoolean(input));
                } else {
                    // Tenta interpretar qualquer outra entrada como Double.
                    try {
                        // Converte o texto numérico para o tipo usado pelo Jaca.
                        valueRead = new Value(Double.parseDouble(input));
                    } catch (NumberFormatException e) {
                        // Rejeita texto que não seja número nem booleano.
                        throw new RuntimeException("Erro em tempo de execução: Entrada inválida para '"
                                + node.id + "'. Esperado número ou booleano.");
                    }
                }
                // Cria ou substitui o valor atual da variável lida.
                symbolTable.put(node.id, valueRead);
            }
        } catch (IOException e) {
            // Converte falha de console em erro de execução com o identificador.
            throw new RuntimeException("Erro em tempo de execução: Erro em readline() para '"
                    + node.id + "'.");
        }
        // O comando read não produz valor de expressão.
        return null;
    }

    // Empacota o literal numérico da AST como um Value Double.
    @Override
    public Value visit(NumberNode node) {
        // Preserva a precisão Double usada pela implementação-base.
        return new Value(node.value);
    }

    // Recupera da tabela o valor associado a uma variável.
    @Override
    public Value visit(VariableNode node) {
        // Detecta uso de variável antes de atribuição ou leitura.
        if (!symbolTable.containsKey(node.name)) {
            // Emite erro semântico com o nome que não foi inicializado.
            throw new RuntimeException("Erro Semântico: Variável '" + node.name + "' não inicializada.");
        }
        // Devolve o valor guardado sem convertê-lo para outro tipo.
        return symbolTable.get(node.name);
    }

    // Avalia operações aritméticas, relacionais e de igualdade.
    @Override
    public Value visit(BinaryOpNode node) {
        // Avalia o operando esquerdo recursivamente.
        Value leftVal = node.left.accept(this);
        // Avalia o operando direito recursivamente.
        Value rightVal = node.right.accept(this);
        // Identifica os cinco operadores aritméticos permitidos.
        if (node.op == sym.PLUS || node.op == sym.MINUS || node.op == sym.TIMES || node.op == sym.DIV
                || node.op == sym.MOD) {
            // Exige que os dois operandos sejam números Double.
            if (!leftVal.isDouble() || !rightVal.isDouble()) {
                // Interrompe a execução quando a operação recebe um booleano.
                throw new RuntimeException("Erro de Tipo: Operadores aritméticos exigem operandos numéricos. "
                        + "Recebido: " + leftVal + " e " + rightVal);
            }
            // Extrai o valor numérico esquerdo após validar o tipo.
            double l = leftVal.asDouble();
            // Extrai o valor numérico direito após validar o tipo.
            double r = rightVal.asDouble();
            // Devolve a soma dos operandos.
            if (node.op == sym.PLUS) {
                // Encapsula o resultado numérico para o visitor.
                return new Value(l + r);
            }
            // Devolve a subtração do lado direito do esquerdo.
            if (node.op == sym.MINUS) {
                // Encapsula o resultado numérico da subtração.
                return new Value(l - r);
            }
            // Devolve o produto dos operandos.
            if (node.op == sym.TIMES) {
                // Encapsula o resultado numérico da multiplicação.
                return new Value(l * r);
            }
            // Devolve o quociente e verifica o caso excepcional de zero.
            if (node.op == sym.DIV) {
                // Rejeita divisão por zero antes da operação de ponto flutuante.
                if (r == 0.0) {
                    // Informa a causa em vez de retornar Infinity ou NaN.
                    throw new RuntimeException("Erro em tempo de execução: Divisão por zero.");
                }
                // Encapsula o resultado numérico da divisão.
                return new Value(l / r);
            }
            // Devolve o resto da divisão numérica.
            if (node.op == sym.MOD) {
                // Encapsula o resultado calculado pelo operador módulo do Java.
                return new Value(l % r);
            }
        }
        // Identifica comparações de magnitude que retornam Boolean.
        if (node.op == sym.GT || node.op == sym.LT || node.op == sym.GEQ || node.op == sym.LEQ) {
            // Exige dois números para comparar sua magnitude.
            if (!leftVal.isDouble() || !rightVal.isDouble()) {
                // Impede comparação relacional entre valores de tipo incompatível.
                throw new RuntimeException("Erro de Tipo: Comparações de magnitude (>, <, >=, <=) exigem números.");
            }
            // Extrai o valor esquerdo para a comparação relacional.
            double l = leftVal.asDouble();
            // Extrai o valor direito para a comparação relacional.
            double r = rightVal.asDouble();
            // Compara se o valor esquerdo é maior.
            if (node.op == sym.GT) {
                // Devolve a comparação como Boolean.
                return new Value(l > r);
            }
            // Compara se o valor esquerdo é menor.
            if (node.op == sym.LT) {
                // Devolve a comparação como Boolean.
                return new Value(l < r);
            }
            // Compara se o valor esquerdo é maior ou igual.
            if (node.op == sym.GEQ) {
                // Devolve a comparação como Boolean.
                return new Value(l >= r);
            }
            // Compara se o valor esquerdo é menor ou igual.
            if (node.op == sym.LEQ) {
                // Devolve a comparação como Boolean.
                return new Value(l <= r);
            }
        }
        // Identifica igualdade e diferença para Double ou Boolean.
        if (node.op == sym.EQ || node.op == sym.NEQ) {
            // Guarda o resultado de igualdade antes de inverter para !=.
            boolean areEqual;
            // Compara números Double do mesmo tipo.
            if (leftVal.isDouble() && rightVal.isDouble()) {
                // Usa igualdade do wrapper Double para os valores numéricos.
                areEqual = leftVal.asDouble().equals(rightVal.asDouble());
            } else if (leftVal.isBoolean() && rightVal.isBoolean()) {
                // Compara valores Boolean quando os dois operandos são booleanos.
                areEqual = leftVal.asBoolean().equals(rightVal.asBoolean());
            } else {
                // Rejeita igualdade entre número e booleano por tipagem estrita.
                throw new RuntimeException("Erro de Tipo: Não é possível comparar a igualdade de tipos diferentes. "
                        + "Tentativa de comparar " + leftVal + " com " + rightVal);
            }
            // Devolve igualdade direta ou seu complemento conforme o token.
            return new Value(node.op == sym.EQ ? areEqual : !areEqual);
        }
        // Interrompe se a AST contiver um identificador de operador desconhecido.
        throw new RuntimeException("Erro: Operador binário desconhecido (ID do Token: " + node.op + ").");
    }

    // Avalia uma negação unária e rejeita booleanos.
    @Override
    public Value visit(UnaryMinusNode node) {
        // Calcula a subexpressão antes de inverter seu sinal.
        Value val = node.expr.accept(this);
        // O menos unário é definido somente para números.
        if (!val.isDouble()) {
            // Informa erro de tipo se o operando for booleano.
            throw new RuntimeException("Erro de Tipo: Operador '-' unário aplicado a um Booleano.");
        }
        // Devolve o número com o sinal invertido.
        return new Value(-val.asDouble());
    }
}
