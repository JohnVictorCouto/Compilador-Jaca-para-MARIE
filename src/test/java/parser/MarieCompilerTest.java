// Declara o pacote para acessar o parser e os símbolos gerados pelo CUP.
package parser;

// Importa leitores em memória para analisar programas Jaca pequenos.
import java.io.StringReader;
// Importa arquivos para validar os exemplos mantidos junto ao projeto.
import java.nio.file.Files;
// Importa caminhos para localizar os exemplos durante mvn test.
import java.nio.file.Path;
// Importa listas para configurar entradas e saídas esperadas.
import java.util.List;
// Importa a AST produzida pela gramática.
import parser.ast.ASTNode;
// Importa o visitor de compilação Jaca para MARIE.
import parser.ast.MarieCompiler;
// Importa as asserções do JUnit.
import static org.junit.jupiter.api.Assertions.assertEquals;
// Importa a validação de condições booleanas dos testes.
import static org.junit.jupiter.api.Assertions.assertFalse;
// Importa a validação de presença de trechos no assembly.
import static org.junit.jupiter.api.Assertions.assertTrue;
// Importa a verificação de exceções esperadas.
import static org.junit.jupiter.api.Assertions.assertThrows;
// Importa a anotação que executa cada cenário como teste.
import org.junit.jupiter.api.Test;

// Verifica geração de código e comportamento no simulador MARIE de testes.
final class MarieCompilerTest {
    // Confirma precedência, soma, subtração e resultado no acumulador.
    @Test
    void arithmeticAndPrecedenceProduceExpectedValues() throws Exception {
        // Executa várias expressões para detectar inversão de operandos.
        MarieTestMachine.Result result = run("print(1 + 2 * 3); print(7 - 2 + 1);");
        // A multiplicação deve preceder soma e subtração associar à esquerda.
        assertEquals(List.of(7, 6), result.output());
    }

    // Confirma sinais da multiplicação, divisão e resto.
    @Test
    void signedArithmeticMatchesTruncatingIntegerSemantics() throws Exception {
        // Exercita todas as combinações de sinal e mais de um operador.
        String source = "print(-3 * 4); print(3 * -4); print(-3 * -4); "
                + "print(-17 / 5); print(17 / -5); print(-17 / -5); "
                + "print(-17 % 5); print(17 % -5); print(-17 % -5);";
        // Executa o programa e compara seus valores de saída.
        MarieTestMachine.Result result = run(source);
        // Divisão trunca para zero e resto mantém o sinal do dividendo.
        assertEquals(List.of(-12, -12, 12, -3, -3, 3, -2, 2, -2), result.output());
    }

    // Confirma multiplicação quando um fator usa a palavra mínima signed.
    @Test
    void multiplicationHandlesMinimumWordAndWrapsOverflow() throws Exception {
        // O segundo caso executa o contador negativo de magnitude 32768.
        String source = "print(-32768 * 1); print(1 * -32768); print(-32768 * -1);";
        // Executa os laços gerados até HALT.
        MarieTestMachine.Result result = run(source);
        // 32768 não cabe positivo e o resultado se representa como -32768.
        assertEquals(List.of(-32768, -32768, -32768), result.output());
    }

    // Confirma que todos os seis comparadores produzem 1 ou 0.
    @Test
    void comparisonsReturnZeroOrOneForTrueAndFalseCases() throws Exception {
        // Usa operandos menores, maiores, iguais e expressões relacionadas.
        String source = "print(3 < 5); print(5 < 3); print(3 > 5); print(5 > 3); "
                + "print(3 == 3); print(3 != 3); print(3 >= 3); print(2 >= 3); "
                + "print(2 <= 3); print(4 <= 3);";
        // Compila e executa as condições produzidas.
        MarieTestMachine.Result result = run(source);
        // A saída demonstra tanto as comparações verdadeiras quanto as falsas.
        assertEquals(List.of(1, 0, 0, 1, 1, 0, 1, 0, 1, 0), result.output());
    }

    // Confirma comparações de sinais opostos junto aos limites signed de 16 bits.
    @Test
    void comparisonsDoNotOverflowWhenOperandsHaveOppositeSigns() throws Exception {
        // A subtração signed direta transbordaria em alguns destes casos.
        String source = "print(32767 > -1); print(-32768 < 1); "
                + "print(32767 <= -1); print(-32768 >= 1);";
        // Executa os ramos que comparam sinais antes de subtrair.
        MarieTestMachine.Result result = run(source);
        // A ordem signed deve permanecer correta nos extremos representáveis.
        assertEquals(List.of(1, 1, 0, 0), result.output());
    }

    // Confirma if/else, if sem else, while e blocos vazios.
    @Test
    void controlFlowSupportsEmptyBlocksAndLoops() throws Exception {
        // Inclui caminho else, laço crescente e bloco vazio aceito pela gramática.
        String source = "if (1 > 2) then { print(99); } else { print(2); } "
                + "if (1 < 2) then {} else { print(98); } "
                + "if (1 > 2) then { print(97); } while (1 > 2) {} "
                + "i = 1; while (i <= 3) { print(i); i = i + 1; }";
        // Executa a saída de controle de fluxo gerada.
        MarieTestMachine.Result result = run(source);
        // O caminho verdadeiro vazio não imprime e o laço produz 1, 2 e 3.
        assertEquals(List.of(2, 1, 2, 3), result.output());
    }

    // Confirma temporários distintos em subexpressões encaixadas.
    @Test
    void nestedExpressionsDoNotOverwriteIntermediateValues() throws Exception {
        // Os dois lados da multiplicação calculam somas com temporários próprios.
        MarieTestMachine.Result result = run("print((1 + 2) * (3 + 4));");
        // A expressão deve resultar em vinte e um.
        assertEquals(List.of(21), result.output());
    }

    // Confirma que o exemplo de fatorial do repositório compila e executa.
    @Test
    void documentedFactorialExampleProducesOneHundredTwenty() throws Exception {
        // Lê a fonte real com comentários Jaca da pasta examples.
        String source = Files.readString(Path.of("examples/fatorial.jaca"));
        // Compila e executa o arquivo usando o mesmo pipeline dos demais testes.
        MarieTestMachine.Result result = run(source);
        // O fatorial de cinco deve imprimir cento e vinte.
        assertEquals(List.of(120), result.output());
    }

    // Confirma leitura numérica e escrita do valor lido.
    @Test
    void readStoresNumericInputInTheNamedVariable() throws Exception {
        // Compila um programa cuja entrada ocorre antes da impressão.
        String assembly = compile("read(numero); print(numero);");
        // Fornece 42 como entrada MARIE e verifica a saída.
        assertEquals(List.of(42), MarieTestMachine.execute(assembly, List.of(42)).output());
    }

    // Confirma truncamento de literais e o menor inteiro representável.
    @Test
    void numericLiteralsTruncateTowardZeroWithinMarieRange() throws Exception {
        // Inclui decimais positivos e negativos e o limite mínimo signed.
        MarieTestMachine.Result result = run("print(3.9); print(-3.9); print(-32768);");
        // Os literais são truncados para inteiro antes de serem declarados.
        assertEquals(List.of(3, -3, -32768), result.output());
    }

    // Confirma que um literal positivo fora da faixa é rejeitado.
    @Test
    void positiveLiteralOutsideSignedWordRangeIsRejected() throws Exception {
        // A magnitude 32768 só é válida sob menos unário para formar -32768.
        String source = "print(32768);";
        // O visitor deve falhar explicitamente em vez de gerar DEC inválido.
        assertThrows(IllegalArgumentException.class, () -> compile(source));
    }

    // Confirma que erros recuperáveis do CUP não produzem compilação parcial.
    @Test
    void syntaxErrorsAreNotAcceptedAsPartialPrograms() {
        // Atribuição sem expressão é inválida, mesmo se CUP tentar recuperar.
        assertThrows(Exception.class, () -> parse("x = ;"));
    }

    // Confirma divisão e resto nos casos extremos da palavra MARIE.
    @Test
    void divisionHandlesMinimumWordAndNegativeDivisor() throws Exception {
        // Exercita divisor mínimo, quociente que transborda e resto exato.
        String source = "print(-32768 / -32768); print(-32768 % -32768); "
                + "print(-32768 / -1); print(-32767 / -32768);";
        // Executa inclusive os laços longos de subtrações sucessivas.
        MarieTestMachine.Result result = run(source);
        // O overflow de 32768 volta a -32768 na palavra signed MARIE.
        assertEquals(List.of(1, 0, -32768, 0), result.output());
    }

    // Confirma divisor zero como encerramento controlado, sem laço infinito.
    @Test
    void divisionByZeroBranchesToHaltTrap() throws Exception {
        // Usa divisor guardado em variável para exercitar verificação em runtime.
        String assembly = compile("d = 0; print(10 / d);");
        // O assembly deve conter o destino comum de parada por erro.
        assertTrue(assembly.contains("DIVZERO,"));
        // A rotina encerra sem emitir um valor de resultado.
        assertTrue(MarieTestMachine.execute(assembly, List.of()).output().isEmpty());
    }

    // Confirma que a mesma instância do compilador não conserva estado antigo.
    @Test
    void compilerCanBeReusedWithoutStaleLabelsOrData() throws Exception {
        // Reutiliza uma única instância do visitor em duas árvores diferentes.
        MarieCompiler compiler = new MarieCompiler();
        // Compila uma expressão que exige constante, variável e trap de divisão.
        String first = compiler.compile(parse("x = 2; print(10 / x);"));
        // Compila uma expressão sem divisão nem variável.
        String second = compiler.compile(parse("print(7);"));
        // A primeira compilação precisa ter o trap e a variável.
        assertTrue(first.contains("DIVZERO,"));
        // A segunda compilação não pode herdar dados ou rótulos da primeira.
        assertFalse(second.contains("DIVZERO,"));
        // A segunda compilação deve reservar somente a constante correspondente a sete.
        assertTrue(second.contains("C0,"));
        // Uma execução independente da segunda saída confirma o valor.
        assertEquals(List.of(7), MarieTestMachine.execute(second, List.of()).output());
    }

    // Confirma que a aritmética MARIE em runtime opera em palavras de 16 bits.
    @Test
    void arithmeticWrapsAsSignedSixteenBitWords() throws Exception {
        // Soma um à maior palavra signed.
        MarieTestMachine.Result result = run("print(32767 + 1);");
        // O padrão de complemento de dois passa ao menor inteiro.
        assertEquals(List.of(-32768), result.output());
    }

    // Analisa, compila e executa uma fonte Jaca sem usar arquivos temporários.
    private static MarieTestMachine.Result run(String source) throws Exception {
        // Gera assembly usando uma instância nova do compilador.
        String assembly = compile(source);
        // Executa o assembly no simulador auxiliar de testes.
        return MarieTestMachine.execute(assembly, List.of());
    }

    // Converte o texto Jaca em assembly usando exatamente o parser do projeto.
    private static String compile(String source) throws Exception {
        // Cria um compiler isolado para o programa de teste.
        MarieCompiler compiler = new MarieCompiler();
        // Converte a AST completa para o programa MARIE.
        return compiler.compile(parse(source));
    }

    // Analisa uma string para obter a raiz da AST do programa Jaca.
    private static ASTNode parse(String source) throws Exception {
        // Cria o scanner léxico sobre a string fornecida pelo teste.
        Scanner scanner = new Scanner(new StringReader(source));
        // Cria o parser que constrói os nós da AST.
        Parser parser = new Parser(scanner);
        // Executa as regras CUP e suas ações para obter a árvore.
        parser.parse();
        // Impede que testes aceitem AST parcial após recuperação de erro.
        if (parser.getSyntaxErrorCount() > 0) {
            // Inclui a causa original reportada pelo parser no diagnóstico.
            throw new IllegalArgumentException("Erro de sintaxe no programa de teste.");
        }
        // Recupera a raiz construída pela gramática.
        ASTNode root = parser.getAST();
        // Falha claramente se o parser não produzir uma árvore.
        if (root == null) {
            // A exceção explica que o teste recebeu uma fonte sem AST.
            throw new IllegalStateException("Parser nao produziu AST para o teste.");
        }
        // Devolve a raiz para os testes de compilação.
        return root;
    }
}
