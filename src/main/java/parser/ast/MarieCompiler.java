// Declara o pacote da AST, onde ficam o visitor e os nós Jaca.
package parser.ast;

// Importa a lista usada para construir trechos de assembly.
import java.util.Objects;
// Importa os nós de expressão que o visitor traduz.
import parser.ast.expression.BinaryOpNode;
// Importa os literais numéricos da árvore.
import parser.ast.expression.NumberNode;
// Importa o nó de negação unária.
import parser.ast.expression.UnaryMinusNode;
// Importa o nó de acesso a variável.
import parser.ast.expression.VariableNode;
// Importa os nós de comando que o visitor traduz.
import parser.ast.statement.AssignNode;
// Importa o bloco com a lista ordenada de comandos.
import parser.ast.statement.BlockNode;
// Importa o comando condicional.
import parser.ast.statement.IfNode;
// Importa o comando de saída.
import parser.ast.statement.PrintNode;
// Importa o comando de entrada.
import parser.ast.statement.ReadNode;
// Importa o comando de repetição.
import parser.ast.statement.WhileNode;
// Importa os símbolos de operadores produzidos pelo CUP.
import parser.sym;
// Importa o estado de dados, rótulos e formatação de assembly.
import parser.ast.compiler.MarieContext;

// Traduz cada nó Jaca para o texto de uma ou mais instruções MARIE.
public final class MarieCompiler implements ASTVisitor<String> {
    // Define o menor inteiro representável como palavra signed MARIE.
    private static final int MIN_VALUE = Short.MIN_VALUE;
    // Define o maior inteiro representável como palavra signed MARIE.
    private static final int MAX_VALUE = Short.MAX_VALUE;
    // Mantém o estado de geração ativo somente durante uma compilação.
    private MarieContext context;

    // Compila uma AST inteira e reinicia o estado para permitir reuso seguro.
    public String compile(ASTNode root) {
        // Rejeita uma árvore ausente com uma mensagem direta.
        Objects.requireNonNull(root, "A raiz da AST nao pode ser nula.");
        // Cria um contexto limpo para que mapas e contadores não vazem entre chamadas.
        context = new MarieContext();
        // Visita a raiz e obtém o corpo executável em assembly.
        String body = root.accept(this);
        // Envolve o corpo com diretiva de origem, HALT e área de dados.
        return context.assemble(body);
    }

    // Gera os comandos de um bloco na mesma ordem em que aparecem na AST.
    @Override
    public String visit(BlockNode node) {
        // Acumula o código dos comandos sem introduzir uma palavra extra.
        StringBuilder code = new StringBuilder();
        // Percorre cada comando do bloco para preservar a execução sequencial.
        for (ASTNode statement : node.statements) {
            // Protege blocos vazios e nós nulos que possam vir de uma AST manual.
            if (statement != null) {
                // Delega cada comando ao seu método visit correspondente.
                code.append(statement.accept(this));
            }
        }
        // Devolve o código concatenado do bloco.
        return code.toString();
    }

    // Avalia a expressão de uma atribuição e grava seu resultado na variável.
    @Override
    public String visit(AssignNode node) {
        // Traduz a expressão antes de armazenar o valor que ficou no AC.
        String expression = node.expression.accept(this);
        // Reserva ou localiza a célula de memória da variável de destino.
        String variable = context.variable(node.id);
        // Executa a expressão e persiste o resultado com STORE.
        return expression + context.instruction("STORE " + variable, "atribui o resultado a " + node.id);
    }

    // Traduz if/else em saltos que testam o booleano 0/1 guardado no AC.
    @Override
    public String visit(IfNode node) {
        // Reserva um endereço para o bloco executado quando a condição for falsa.
        String elseLabel = context.label();
        // Reserva um endereço para continuar depois do comando condicional.
        String endLabel = context.label();
        // Traduz a condição para que seu resultado fique no acumulador.
        StringBuilder code = new StringBuilder(node.condition.accept(this));
        // Se AC não for positivo, pula o salto ao bloco verdadeiro.
        code.append(context.instruction("SKIPCOND 800", "executa o then quando a condicao vale um"));
        // Encaminha a condição falsa para else ou para o fim do if.
        code.append(context.instruction("JUMP " + elseLabel, "desvia quando a condicao e falsa"));
        // Traduz o bloco verdadeiro imediatamente após o teste.
        code.append(node.thenBlock.accept(this));
        // Pula o bloco else após executar o caminho verdadeiro.
        code.append(context.instruction("JUMP " + endLabel, "evita executar o bloco else"));
        // Usa CLEAR como instrução âncora para o rótulo mesmo se o bloco for vazio.
        code.append(context.labeled(elseLabel, "CLEAR", "inicio do caminho else"));
        // Traduz o bloco falso somente quando ele existe na AST.
        if (node.elseBlock != null) {
            // Acrescenta o código do bloco else depois do seu rótulo.
            code.append(node.elseBlock.accept(this));
        }
        // Usa CLEAR para garantir uma instrução válida no rótulo final.
        code.append(context.labeled(endLabel, "CLEAR", "fim do comando if"));
        // Devolve os dois caminhos e os saltos de controle de fluxo.
        return code.toString();
    }

    // Traduz while como teste rotulado, corpo e salto de retorno ao teste.
    @Override
    public String visit(WhileNode node) {
        // Reserva o rótulo que antecede cada avaliação da condição.
        String startLabel = context.label();
        // Reserva o rótulo alcançado quando a condição for falsa.
        String endLabel = context.label();
        // Cria o buffer e coloca a condição sob o rótulo do início do laço.
        StringBuilder code = new StringBuilder();
        // Avalia a condição a cada iteração, não somente antes do laço.
        code.append(context.labeled(startLabel, "CLEAR", "inicio do teste do while"));
        // Gera a condição e deixa 0 ou 1 no acumulador.
        code.append(node.condition.accept(this));
        // Pula o salto de saída somente quando AC for positivo.
        code.append(context.instruction("SKIPCOND 800", "continua enquanto a condicao vale um"));
        // Encerra o laço quando o valor da condição for zero.
        code.append(context.instruction("JUMP " + endLabel, "sai do while quando a condicao e falsa"));
        // Gera o bloco do corpo após o teste verdadeiro.
        code.append(node.block.accept(this));
        // Retorna ao rótulo que reavalia a condição.
        code.append(context.instruction("JUMP " + startLabel, "reavalia a condicao do while"));
        // Ancora o destino de saída em uma instrução MARIE válida.
        code.append(context.labeled(endLabel, "CLEAR", "fim do while"));
        // Devolve o laço completo como uma sequência de instruções.
        return code.toString();
    }

    // Traduz uma expressão de impressão e envia seu valor ao dispositivo de saída.
    @Override
    public String visit(PrintNode node) {
        // Deixa o resultado da expressão no acumulador.
        String expression = node.expr.accept(this);
        // Emite OUTPUT depois de executar o código da expressão.
        return expression + context.instruction("OUTPUT", "imprime o valor do acumulador");
    }

    // Traduz leitura numérica e armazena o valor recebido na variável Jaca.
    @Override
    public String visit(ReadNode node) {
        // Reserva ou localiza a célula de memória do identificador de entrada.
        String variable = context.variable(node.id);
        // INPUT carrega o valor numérico no AC e STORE o persiste.
        return context.instruction("INPUT", "le um inteiro numerico no acumulador")
                + context.instruction("STORE " + variable, "salva a entrada em " + node.id);
    }

    // Carrega a constante inteira correspondente ao literal da AST.
    @Override
    public String visit(NumberNode node) {
        // Converte o literal Double para inteiro e valida o limite de uma palavra.
        int integer = truncateLiteral(node.value);
        // Declara ou reutiliza a constante e carrega seu endereço no AC.
        return context.instruction("LOAD " + context.constant(integer), "carrega a constante " + integer);
    }

    // Carrega no acumulador o conteúdo da célula reservada para uma variável.
    @Override
    public String visit(VariableNode node) {
        // Reserva a variável mesmo quando ela é usada antes de uma atribuição.
        String variable = context.variable(node.name);
        // Devolve o código que lê a palavra correspondente da memória.
        return context.instruction("LOAD " + variable, "carrega a variavel " + node.name);
    }

    // Escolhe o gerador adequado para cada operador binário da linguagem Jaca.
    @Override
    public String visit(BinaryOpNode node) {
        // Traduz soma e subtração com um temporário para o operando direito.
        if (node.op == sym.PLUS || node.op == sym.MINUS) {
            // Mantém a ordem esquerda menos direita em operações não comutativas.
            return translateAdditionOrSubtraction(node);
        }
        // Traduz multiplicação por somas repetidas sem instrução MUL nativa.
        if (node.op == sym.TIMES) {
            // A rotina respeita sinais e usa contador independente.
            return translateMultiplication(node);
        }
        // Traduz divisão e resto por subtrações sucessivas e trata divisor zero.
        if (node.op == sym.DIV || node.op == sym.MOD) {
            // O mesmo algoritmo preserva o resto com o sinal do dividendo.
            return translateDivisionOrRemainder(node);
        }
        // Encaminha operadores de igualdade e magnitude ao gerador booleano.
        if (node.op == sym.EQ || node.op == sym.NEQ || node.op == sym.GT || node.op == sym.LT
                || node.op == sym.GEQ || node.op == sym.LEQ) {
            // Comparações produzem 1 ou 0 no acumulador.
            return translateComparison(node);
        }
        // Interrompe explicitamente diante de um token de operador desconhecido.
        throw new IllegalArgumentException("Operador Jaca desconhecido na AST: " + node.op);
    }

    // Traduz menos unário como zero menos a expressão avaliada.
    @Override
    public String visit(UnaryMinusNode node) {
        // Detecta o único literal positivo cujo oposto é representável no MARIE.
        if (node.expr instanceof NumberNode number && truncatesToMinimumMagnitude(number.value)) {
            // Declara -32768 diretamente para evitar tentar representar +32768.
            return context.instruction("LOAD " + context.constant(MIN_VALUE), "carrega o literal -32768");
        }
        // Reserva uma palavra para preservar a expressão antes de limpar o AC.
        String temporary = context.temporary();
        // Avalia o operando, guarda-o e calcula 0 menos esse valor.
        return node.expr.accept(this)
                + context.instruction("STORE " + temporary, "guarda o operando do menos unario")
                + context.instruction("CLEAR", "prepara zero no acumulador")
                + context.instruction("SUBT " + temporary, "calcula o oposto do operando");
    }

    // Traduz soma e subtração sem perder o valor do primeiro operando no AC.
    private String translateAdditionOrSubtraction(BinaryOpNode node) {
        // Reserva memória para o resultado da subexpressão direita.
        String rightTemporary = context.temporary();
        // Avalia o lado direito primeiro para liberar seu endereço temporário.
        String rightCode = node.right.accept(this);
        // Avalia o lado esquerdo depois de guardar o resultado direito.
        String leftCode = node.left.accept(this);
        // Seleciona a operação MARIE correspondente ao token Jaca.
        String operation = node.op == sym.PLUS ? "ADD " : "SUBT ";
        // Devolve código que deixa esquerda mais/menos direita no AC.
        return rightCode
                + context.instruction("STORE " + rightTemporary, "guarda o operando direito")
                + leftCode
                + context.instruction(operation + rightTemporary,
                        node.op == sym.PLUS ? "soma esquerda e direita" : "subtrai direita de esquerda");
    }

    // Traduz multiplicação com um contador positivo ou negativo.
    private String translateMultiplication(BinaryOpNode node) {
        // Reserva palavras para os operandos e para o acumulador do produto.
        String right = context.temporary();
        // Mantém o primeiro operando intacto durante somas repetidas.
        String left = context.temporary();
        // Armazena a multiplicação parcial em memória.
        String product = context.temporary();
        // Obtém a constante de passo para aproximar o contador de zero.
        String one = context.constant(1);
        // Reserva rótulos para o sinal do contador e os dois laços.
        String positiveLoop = context.label();
        // Reserva o ponto de entrada quando o multiplicador for negativo.
        String negativeLoop = context.label();
        // Reserva o fim compartilhado da operação.
        String end = context.label();
        // Gera código para calcular a direita e preservar seu valor.
        StringBuilder code = new StringBuilder(node.right.accept(this));
        // Guarda o multiplicador, que será usado como contador de repetições.
        code.append(context.instruction("STORE " + right, "guarda o multiplicador como contador"));
        // Calcula e guarda o outro fator antes de iniciar o laço.
        code.append(node.left.accept(this));
        // Preserva o multiplicando na memória.
        code.append(context.instruction("STORE " + left, "guarda o multiplicando"));
        // Inicializa o resultado em zero.
        code.append(context.instruction("CLEAR", "inicializa o produto em zero"));
        // Persiste o produto parcial.
        code.append(context.instruction("STORE " + product, "salva o produto parcial"));
        // Encaminha multiplicadores não negativos ao laço de contagem decrescente.
        code.append(context.instruction("LOAD " + right, "testa o sinal do multiplicador"));
        // O salto é executado se o contador não for negativo.
        code.append(context.instruction("SKIPCOND 000", "detecta multiplicador negativo"));
        // Entra no caso não negativo quando o salto condicional não ocorre.
        code.append(context.instruction("JUMP " + positiveLoop, "seleciona o contador nao negativo"));
        // Entra no caso negativo quando SKIPCOND pula o salto anterior.
        code.append(context.instruction("JUMP " + negativeLoop, "seleciona o contador negativo"));
        // Declara o primeiro teste para multiplicadores zero ou positivos.
        code.append(context.labeled(positiveLoop, "LOAD " + right, "testa o contador positivo"));
        // Se o contador não for positivo, pula a instrução seguinte.
        code.append(context.instruction("SKIPCOND 800", "continua enquanto o contador for positivo"));
        // Sai para contador zero ou negativo.
        code.append(context.instruction("JUMP " + end, "encerra o caso nao negativo em zero"));
        // Soma o multiplicando ao resultado corrente.
        code.append(context.instruction("LOAD " + product, "carrega o produto parcial"));
        // Adiciona uma cópia do multiplicando.
        code.append(context.instruction("ADD " + left, "soma uma copia do multiplicando"));
        // Salva o novo produto.
        code.append(context.instruction("STORE " + product, "salva o novo produto"));
        // Carrega o contador para reduzi-lo em um.
        code.append(context.instruction("LOAD " + right, "carrega o contador positivo"));
        // Subtrai a constante um.
        code.append(context.instruction("SUBT " + one, "reduz o contador em um"));
        // Salva o contador atualizado.
        code.append(context.instruction("STORE " + right, "salva o contador atualizado"));
        // Retorna ao teste de término do caso não negativo.
        code.append(context.instruction("JUMP " + positiveLoop, "repete a soma"));
        // Declara o teste do contador negativo.
        code.append(context.labeled(negativeLoop, "LOAD " + right, "testa o contador negativo"));
        // Pula o salto de saída enquanto o contador for negativo.
        code.append(context.instruction("SKIPCOND 000", "continua enquanto o contador for negativo"));
        // Sai quando o contador chega a zero.
        code.append(context.instruction("JUMP " + end, "encerra o caso negativo em zero"));
        // Carrega o produto parcial.
        code.append(context.instruction("LOAD " + product, "carrega o produto parcial"));
        // Soma o oposto do multiplicando para cada unidade negativa.
        code.append(context.instruction("CLEAR", "prepara zero para inverter o multiplicando"));
        // Calcula o oposto do multiplicando no AC.
        code.append(context.instruction("SUBT " + left, "calcula o oposto do multiplicando"));
        // Soma o produto parcial preservado.
        code.append(context.instruction("ADD " + product, "acumula o fator negativo"));
        // Salva o produto atualizado.
        code.append(context.instruction("STORE " + product, "salva o novo produto"));
        // Carrega o contador negativo.
        code.append(context.instruction("LOAD " + right, "carrega o contador negativo"));
        // Aproxima o contador negativo de zero.
        code.append(context.instruction("ADD " + one, "aumenta o contador negativo em um"));
        // Persiste o novo contador.
        code.append(context.instruction("STORE " + right, "salva o contador atualizado"));
        // Repete até o contador negativo chegar a zero.
        code.append(context.instruction("JUMP " + negativeLoop, "repete a soma negativa"));
        // Carrega o resultado ao final de qualquer um dos dois laços.
        code.append(context.labeled(end, "LOAD " + product, "deixa o produto no acumulador"));
        // Devolve o código completo da multiplicação.
        return code.toString();
    }

    // Traduz divisão inteira e resto com truncamento em direção a zero.
    private String translateDivisionOrRemainder(BinaryOpNode node) {
        // Informa ao contexto que a rotina comum de erro precisa ser emitida.
        context.requireDivisionTrap();
        // Reserva células para divisor, resto parcial e quociente absoluto.
        String divisor = context.temporary();
        // Guarda o dividendo e vai sendo atualizado por subtrações sucessivas.
        String remainder = context.temporary();
        // Conta quantas subtrações inteiras couberam no dividendo.
        String quotient = context.temporary();
        // Guarda cada candidato antes de verificar se ele cruzou zero.
        String candidate = context.temporary();
        // Guarda o resultado final para que todos os caminhos retornem ao AC.
        String result = context.temporary();
        // Obtém a constante de incremento do quociente.
        String one = context.constant(1);
        // Reserva rótulos para a análise de sinal e cada combinação de operandos.
        String nonNegativeDividend = context.label();
        // Reserva o ramo em que o dividendo é negativo.
        String negativeDividend = context.label();
        // Reserva o laço de positivo dividido por positivo.
        String positivePositive = context.label();
        // Reserva o laço de positivo dividido por negativo.
        String positiveNegative = context.label();
        // Reserva o laço de negativo dividido por positivo.
        String negativePositive = context.label();
        // Reserva o laço de negativo dividido por negativo.
        String negativeNegative = context.label();
        // Reserva um rótulo de continuação para cada laço.
        String ppContinue = context.label();
        // Reserva o próximo rótulo de continuação.
        String pnContinue = context.label();
        // Reserva o próximo rótulo de continuação.
        String npContinue = context.label();
        // Reserva o último rótulo de continuação.
        String nnContinue = context.label();
        // Reserva rótulos de término correspondentes aos quatro sinais.
        String ppEnd = context.label();
        // Reserva o término de positivo dividido por negativo.
        String pnEnd = context.label();
        // Reserva o término de negativo dividido por positivo.
        String npEnd = context.label();
        // Reserva o término de negativo dividido por negativo.
        String nnEnd = context.label();
        // Reserva o ponto comum que recarrega o resultado no acumulador.
        String end = context.label();
        // Começa avaliando o divisor e guardando-o antes de gerar o dividendo.
        StringBuilder code = new StringBuilder(node.right.accept(this));
        // Salva o divisor em memória para os testes e subtrações seguintes.
        code.append(context.instruction("STORE " + divisor, "guarda o divisor"));
        // Carrega o divisor para testar o caso zero.
        code.append(context.instruction("LOAD " + divisor, "verifica se o divisor e zero"));
        // Se AC for zero, pula o salto de continuação.
        code.append(context.instruction("SKIPCOND 400", "detecta divisao por zero"));
        // Continua a tradução em tempo de execução se o divisor não for zero.
        code.append(context.instruction("JUMP " + nonNegativeDividend, "continua com divisor valido"));
        // Desvia para a parada comum quando o divisor for zero.
        code.append(context.instruction("JUMP DIVZERO", "interrompe a divisao por zero"));
        // Avalia o dividendo somente depois de validar o divisor.
        code.append(context.labeled(nonNegativeDividend, "CLEAR", "ancora a continuacao da divisao"));
        // Gera o código da expressão esquerda.
        code.append(node.left.accept(this));
        // Inicializa o resto parcial com o valor do dividendo.
        code.append(context.instruction("STORE " + remainder, "inicializa o resto parcial"));
        // Inicializa a contagem do quociente em zero.
        code.append(context.instruction("CLEAR", "inicializa o quociente"));
        // Persiste o contador do quociente.
        code.append(context.instruction("STORE " + quotient, "salva o quociente inicial"));
        // Decide qual rotina usar verificando se o dividendo é negativo.
        code.append(context.instruction("LOAD " + remainder, "testa o sinal do dividendo"));
        // Se o resto for negativo, pula o salto para o caminho não negativo.
        code.append(context.instruction("SKIPCOND 000", "detecta dividendo negativo"));
        // Escolhe o caminho de dividendo zero ou positivo.
        code.append(context.instruction("JUMP " + nonNegativeDividend + "A", "seleciona dividendo nao negativo"));
        // Escolhe o caminho de dividendo negativo.
        code.append(context.instruction("JUMP " + negativeDividend, "seleciona dividendo negativo"));
        // Testa o sinal do divisor no caminho de dividendo não negativo.
        code.append(context.labeled(nonNegativeDividend + "A", "LOAD " + divisor, "testa o sinal do divisor"));
        // Se divisor for negativo, pula o salto para o laço positivo.
        code.append(context.instruction("SKIPCOND 000", "detecta divisor negativo"));
        // Entra no laço de sinais positivo e positivo.
        code.append(context.instruction("JUMP " + positivePositive, "seleciona sinais positivos"));
        // Entra no laço de dividendo positivo e divisor negativo.
        code.append(context.instruction("JUMP " + positiveNegative, "seleciona divisor negativo"));
        // Testa o sinal do divisor no caminho de dividendo negativo.
        code.append(context.labeled(negativeDividend, "LOAD " + divisor, "testa o divisor negativo"));
        // Se o divisor for negativo, pula o salto ao ramo de sinais opostos.
        code.append(context.instruction("SKIPCOND 000", "compara os sinais dos operandos"));
        // Entra no laço de dividendo negativo e divisor positivo.
        code.append(context.instruction("JUMP " + negativePositive, "seleciona sinais diferentes"));
        // Entra no laço de sinais negativos.
        code.append(context.instruction("JUMP " + negativeNegative, "seleciona sinais negativos"));
        // Emite o laço positivo/positivo, no qual subtrair divisor reduz o resto.
        code.append(divisionLoop(positivePositive, ppContinue, ppEnd, remainder, divisor,
                quotient, candidate, one, "SUBT", "000", true));
        // Emite o laço positivo/negativo, no qual somar divisor reduz o resto positivo.
        code.append(divisionLoop(positiveNegative, pnContinue, pnEnd, remainder, divisor,
                quotient, candidate, one, "ADD", "000", true));
        // Emite o laço negativo/positivo, no qual somar divisor aproxima o resto de zero.
        code.append(divisionLoop(negativePositive, npContinue, npEnd, remainder, divisor,
                quotient, candidate, one, "ADD", "800", false));
        // Emite o laço negativo/negativo, no qual subtrair divisor aproxima de zero.
        code.append(divisionLoop(negativeNegative, nnContinue, nnEnd, remainder, divisor,
                quotient, candidate, one, "SUBT", "800", false));
        // Finaliza o caminho positivo/positivo com quociente não negativo.
        code.append(divisionResult(ppEnd, end, result, quotient, remainder, node.op, false));
        // Finaliza o caminho positivo/negativo com sinal de quociente negativo.
        code.append(divisionResult(pnEnd, end, result, quotient, remainder, node.op, true));
        // Finaliza o caminho negativo/positivo com sinal de quociente negativo.
        code.append(divisionResult(npEnd, end, result, quotient, remainder, node.op, true));
        // Finaliza o caminho negativo/negativo com quociente não negativo.
        code.append(divisionResult(nnEnd, end, result, quotient, remainder, node.op, false));
        // Recarrega o resultado escolhido no acumulador.
        code.append(context.labeled(end, "LOAD " + result, "deixa o quociente ou resto no acumulador"));
        // Devolve a rotina completa de divisão ou resto.
        return code.toString();
    }

    // Gera um dos quatro laços de subtração que divide sem perder o sinal do resto.
    private String divisionLoop(String loop, String continuation, String finish, String remainder,
            String divisor, String quotient, String candidate, String one, String operation,
            String skipCondition, boolean candidateMustBeNonNegative) {
        // Cria um buffer para a rotina selecionada pelo sinal dos operandos.
        StringBuilder code = new StringBuilder();
        // Marca o topo do laço para voltar a testar o resto parcial.
        code.append(context.labeled(loop, "LOAD " + remainder, "testa o resto parcial"));
        // Se o resto já chegou a zero, pula o salto de continuação.
        code.append(context.instruction("SKIPCOND 400", "encerra quando o resto e exatamente zero"));
        // Continua o laço enquanto ainda houver resto.
        code.append(context.instruction("JUMP " + continuation, "continua avaliando o divisor"));
        // Sai do laço quando a divisão é exata.
        code.append(context.instruction("JUMP " + finish, "encerra a divisao exata"));
        // Guarda o resultado de uma subtração tentativa antes de avaliar o sinal.
        code.append(context.labeled(continuation, "LOAD " + remainder, "carrega o resto para subtrair"));
        // Ajusta o resto na direção de zero sem calcular valores absolutos.
        code.append(context.instruction(operation + " " + divisor, "calcula o proximo resto candidato"));
        // Salva o candidato para poder testá-lo sem perder o valor.
        code.append(context.instruction("STORE " + candidate, "guarda o resto candidato"));
        // Carrega o candidato para testar se ele cruzou zero.
        code.append(context.instruction("LOAD " + candidate, "testa se o divisor ainda cabe"));
        // Usa o sinal apropriado à combinação dos operandos.
        code.append(context.instruction("SKIPCOND " + skipCondition, "verifica o limite do resto"));
        // Pula o salto de continuação quando o candidato excede o limite.
        code.append(context.instruction("JUMP " + continuation + "ACCEPT", "mantem o candidato dentro do limite"));
        // Desvia para o término quando o sinal indica excesso.
        code.append(context.instruction("JUMP " + finish, "encerra quando o candidato cruza zero"));
        // Aceita o candidato e atualiza o resto parcial.
        code.append(context.labeled(continuation + "ACCEPT", "LOAD " + candidate, "aceita a subtracao"));
        // Persiste o resto reduzido.
        code.append(context.instruction("STORE " + remainder, "atualiza o resto parcial"));
        // Carrega o quociente antes de incrementar.
        code.append(context.instruction("LOAD " + quotient, "carrega o quociente parcial"));
        // Soma um ao número de subtrações completas.
        code.append(context.instruction("ADD " + one, "incrementa o quociente"));
        // Persiste o novo quociente.
        code.append(context.instruction("STORE " + quotient, "salva o quociente parcial"));
        // Retorna ao teste para tentar outra subtração.
        code.append(context.instruction("JUMP " + loop, "repete a divisao por subtracoes"));
        // A flag documenta quais combinações aceitam candidatos não negativos.
        if ((candidateMustBeNonNegative && !skipCondition.equals("000"))
                || (!candidateMustBeNonNegative && !skipCondition.equals("800"))) {
            // Interrompe caso uma alteração futura passe uma combinação incompatível.
            throw new IllegalArgumentException("Condicao de sinal inconsistente no laco de divisao.");
        }
        // Devolve o laço que atualiza resto e quociente.
        return code.toString();
    }

    // Move o quociente ou o resto escolhido ao espaço de resultado temporário.
    private String divisionResult(String finish, String end, String result, String quotient,
            String remainder, int operation, boolean negativeQuotient) {
        // Cria um buffer para o caminho de término desta combinação de sinais.
        StringBuilder code = new StringBuilder();
        // Para resto, preserva diretamente o sinal carregado pelo dividendo.
        if (operation == sym.MOD) {
            // Carrega o resto que sobrou das subtrações sucessivas.
            code.append(context.labeled(finish, "LOAD " + remainder, "carrega o resto com sinal do dividendo"));
            // Salva o resultado no temporário comum.
            code.append(context.instruction("STORE " + result, "salva o resto final"));
        } else {
            // Carrega o contador que representa a magnitude do quociente.
            code.append(context.labeled(finish, "LOAD " + quotient, "carrega a magnitude do quociente"));
            // Inverte o quociente nos dois casos de sinais diferentes.
            if (negativeQuotient) {
                // Persiste a magnitude antes de limpar o acumulador.
                code.append(context.instruction("STORE " + quotient, "guarda a magnitude do quociente"));
                // Prepara zero para construir zero menos a magnitude.
                code.append(context.instruction("CLEAR", "prepara zero para o sinal negativo"));
                // Aplica o sinal negativo ao quociente preservado.
                code.append(context.instruction("SUBT " + quotient, "aplica sinal negativo ao quociente"));
            }
            // Salva o quociente com seu sinal final no temporário comum.
            code.append(context.instruction("STORE " + result, "salva o quociente final"));
        }
        // Encaminha os quatro caminhos à instrução que retorna no acumulador.
        code.append(context.instruction("JUMP " + end, "retorna o resultado da divisao"));
        // Devolve o trecho de finalização do ramo.
        return code.toString();
    }

    // Traduz comparações aritméticas para um valor booleano inteiro 1 ou 0.
    private String translateComparison(BinaryOpNode node) {
        // Reserva o endereço em que o operando direito será guardado.
        String right = context.temporary();
        // Reserva memória para o lado esquerdo em comparações sem overflow.
        String left = context.temporary();
        // Reserva espaço para que ambos os caminhos produzam o mesmo retorno.
        String result = context.temporary();
        // Reserva os destinos verdadeiro, falso e fim da comparação.
        String trueLabel = context.label();
        // Reserva o destino do resultado falso.
        String falseLabel = context.label();
        // Reserva um ponto de retorno compartilhado.
        String endLabel = context.label();
        // Escolhe o teste de diferença quando os operandos têm o mesmo sinal.
        String skip = comparisonSkipCondition(node.op);
        // Define se o resultado do SKIPCOND significa que a comparação é verdadeira.
        boolean skipMeansTrue = node.op == sym.LT || node.op == sym.GT || node.op == sym.EQ;
        // Começa avaliando o lado direito e guardando-o em memória.
        StringBuilder code = new StringBuilder(node.right.accept(this));
        // Preserva o valor direito para a subtração da diferença.
        code.append(context.instruction("STORE " + right, "guarda o lado direito da comparacao"));
        // Gera o lado esquerdo da expressão.
        code.append(node.left.accept(this));
        // Preserva o lado esquerdo antes de executar as decisões de sinal.
        code.append(context.instruction("STORE " + left, "guarda o lado esquerdo da comparacao"));
        // Trata igualdade e diferença com o teste de zero da subtração.
        if (node.op == sym.EQ || node.op == sym.NEQ) {
            // Calcula a diferença, cuja igualdade com zero não sofre overflow falso.
            code.append(context.instruction("LOAD " + left, "carrega o lado esquerdo"));
            // Subtrai o lado direito para comparar igualdade.
            code.append(context.instruction("SUBT " + right, "calcula esquerda menos direita"));
            // Encaminha igualdade ou diferença para o rótulo correto.
            appendComparisonBranch(code, "400", skipMeansTrue, trueLabel, falseLabel,
                    "compara igualdade pelo zero");
        } else {
            // Reserva um destino para valores esquerdos negativos.
            String leftNegative = context.label();
            // Reserva um destino para valores esquerdos não negativos.
            String leftNonNegative = context.label();
            // Reserva um destino comum quando ambos os sinais são iguais.
            String sameSign = context.label();
            // Reserva o caso negativo menor que não negativo.
            String negativeToNonNegative = context.label();
            // Reserva o caso não negativo maior que negativo.
            String nonNegativeToNegative = context.label();
            // Testa primeiro o sinal do lado esquerdo.
            code.append(context.instruction("LOAD " + left, "testa o sinal do lado esquerdo"));
            // Se o lado esquerdo for negativo, pula o salto para o caminho não negativo.
            code.append(context.instruction("SKIPCOND 000", "detecta lado esquerdo negativo"));
            // Escolhe o caminho não negativo se a condição não pular.
            code.append(context.instruction("JUMP " + leftNonNegative, "seleciona lado esquerdo nao negativo"));
            // Escolhe o caminho negativo se a condição pular.
            code.append(context.instruction("JUMP " + leftNegative, "seleciona lado esquerdo negativo"));
            // Para lado esquerdo negativo, verifica o sinal do lado direito.
            code.append(context.labeled(leftNegative, "LOAD " + right, "testa o lado direito"));
            // Se o lado direito também for negativo, pula para a comparação segura.
            code.append(context.instruction("SKIPCOND 000", "detecta lado direito negativo"));
            // Quando o lado direito não é negativo, os operandos têm sinais opostos.
            code.append(context.instruction("JUMP " + negativeToNonNegative, "resolve sinais opostos"));
            // Quando ambos são negativos, a subtração não excede a faixa signed.
            code.append(context.instruction("JUMP " + sameSign, "compara operandos de mesmo sinal"));
            // Para lado esquerdo não negativo, também verifica o sinal do direito.
            code.append(context.labeled(leftNonNegative, "LOAD " + right, "testa o sinal do lado direito"));
            // Se o lado direito não for negativo, pula o salto ao caso oposto.
            code.append(context.instruction("SKIPCOND 000", "detecta lado direito negativo"));
            // Quando ambos são não negativos, a subtração é segura.
            code.append(context.instruction("JUMP " + sameSign, "compara operandos de mesmo sinal"));
            // Quando o lado direito é negativo, os operandos têm sinais opostos.
            code.append(context.instruction("JUMP " + nonNegativeToNegative, "resolve sinais opostos"));
            // Decide o caso negativo à esquerda e não negativo à direita sem subtrair.
            code.append(context.labeled(negativeToNonNegative, "JUMP "
                    + ((node.op == sym.LT || node.op == sym.LEQ) ? trueLabel : falseLabel),
                    "resolve a ordem entre sinais diferentes"));
            // Decide o caso não negativo à esquerda e negativo à direita sem subtrair.
            code.append(context.labeled(nonNegativeToNegative, "JUMP "
                    + ((node.op == sym.GT || node.op == sym.GEQ) ? trueLabel : falseLabel),
                    "resolve a ordem entre sinais diferentes"));
            // Com sinais iguais, a diferença cabe na faixa signed e pode ser testada.
            code.append(context.labeled(sameSign, "LOAD " + left, "carrega o lado esquerdo"));
            // Calcula esquerda menos direita sem overflow de sinal.
            code.append(context.instruction("SUBT " + right, "calcula a diferenca com sinais iguais"));
            // Avalia o sinal da diferença ou sua inversão conforme o operador.
            appendComparisonBranch(code, skip, skipMeansTrue, trueLabel, falseLabel,
                    "avalia a comparacao com sinais iguais");
        }
        // Gera 1 no acumulador quando a comparação for verdadeira.
        code.append(context.labeled(trueLabel, "LOAD " + context.constant(1), "resultado verdadeiro"));
        // Guarda o resultado verdadeiro para o ponto comum.
        code.append(context.instruction("STORE " + result, "salva verdadeiro como um"));
        // Pula o caminho falso depois de armazenar o valor.
        code.append(context.instruction("JUMP " + endLabel, "evita sobrescrever o resultado verdadeiro"));
        // Gera 0 no acumulador quando a comparação for falsa.
        code.append(context.labeled(falseLabel, "CLEAR", "resultado falso"));
        // Guarda o resultado falso no mesmo temporário.
        code.append(context.instruction("STORE " + result, "salva falso como zero"));
        // Recarrega o resultado para cumprir a convenção do acumulador.
        code.append(context.labeled(endLabel, "LOAD " + result, "deixa o booleano no acumulador"));
        // Devolve código de comparação com resultado inteiro 1 ou 0.
        return code.toString();
    }

    // Emite dois saltos adjacentes para considerar o SKIPCOND exato da MARIE.
    private void appendComparisonBranch(StringBuilder code, String condition, boolean skipMeansTrue,
            String trueLabel, String falseLabel, String explanation) {
        // Testa sinal ou zero no acumulador e pula exatamente uma instrução.
        code.append(context.instruction("SKIPCOND " + condition, explanation));
        // O primeiro salto ocorre quando SKIPCOND não pula a próxima instrução.
        code.append(context.instruction("JUMP " + (skipMeansTrue ? falseLabel : trueLabel),
                "trata SKIPCOND sem pulo"));
        // O segundo salto ocorre quando SKIPCOND pula o primeiro salto.
        code.append(context.instruction("JUMP " + (skipMeansTrue ? trueLabel : falseLabel),
                "trata SKIPCOND com pulo"));
    }

    // Escolhe o código de SKIPCOND que examina a diferença esquerda menos direita.
    private String comparisonSkipCondition(int operation) {
        // Menor que zero identifica que o operando esquerdo é menor.
        if (operation == sym.LT || operation == sym.GEQ) {
            // O caso GEQ também usa o teste menor, invertendo o destino.
            return "000";
        }
        // Maior que zero identifica que o operando esquerdo é maior.
        if (operation == sym.GT || operation == sym.LEQ) {
            // O caso LEQ também inverte o destino do teste maior.
            return "800";
        }
        // Igualdade e diferença são decididas por uma diferença nula.
        if (operation == sym.EQ || operation == sym.NEQ) {
            // O sentido de EQ e NEQ é tratado pelo destino do salto.
            return "400";
        }
        // Falha de forma explícita se um novo operador não tiver teste definido.
        throw new IllegalArgumentException("Comparacao Jaca desconhecida: " + operation);
    }

    // Converte literais Double por truncamento e valida a faixa MARIE signed 16-bit.
    private int truncateLiteral(Double value) {
        // Rejeita valores ausentes, infinitos ou NaN com diagnóstico claro.
        if (value == null || !Double.isFinite(value)) {
            // O tradutor não pode representar valores não finitos em DEC.
            throw new IllegalArgumentException("Literal MARIE precisa ser um numero finito.");
        }
        // Replica conversão para int em direção a zero sem arredondar.
        double truncated = value < 0 ? Math.ceil(value) : Math.floor(value);
        // Verifica se o inteiro truncado cabe em uma palavra signed de 16 bits.
        if (truncated < MIN_VALUE || truncated > MAX_VALUE) {
            // Explica o limite e o valor que causou a rejeição.
            throw new IllegalArgumentException("Literal " + value + " fora do intervalo MARIE "
                    + MIN_VALUE + " a " + MAX_VALUE + " apos truncamento.");
        }
        // Converte o valor agora validado para a representação inteira Java.
        return (int) truncated;
    }

    // Reconhece a magnitude 32768 que só é representável após menos unário.
    private boolean truncatesToMinimumMagnitude(Double value) {
        // Trata valores não finitos como inválidos antes de examinar o sinal.
        if (value == null || !Double.isFinite(value) || value < 0) {
            // Não classifica números inválidos como o literal mínimo.
            return false;
        }
        // Aplica a mesma regra de truncamento prevista para literais MARIE.
        double truncated = Math.floor(value);
        // Permite +32768 apenas quando o nó pai imediatamente o nega.
        if (truncated == -(double) MIN_VALUE) {
            // A forma -32768 representa o menor valor signed da arquitetura.
            return true;
        }
        // Todos os demais valores seguem o caminho normal de validação.
        return false;
    }
}
