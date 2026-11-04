// Declara o pacote de testes da interface de linha de comando Jaca.
package parser;

// Importa uma fila para consumir valores fornecidos a INPUT.
import java.util.ArrayDeque;
// Importa uma lista para capturar valores enviados a OUTPUT.
import java.util.ArrayList;
// Importa uma fila para entradas numéricas determinísticas.
import java.util.Deque;
// Importa um mapa para representar a memória da máquina.
import java.util.HashMap;
// Importa mapas para endereços, palavras e dados inicializados.
import java.util.LinkedHashMap;
// Importa o tipo Map usado pelo simulador mínimo de testes.
import java.util.Map;

// Executa somente o subconjunto de instruções que o compilador Jaca emite.
final class MarieTestMachine {
    // Limita a execução para detectar laços incorretos em vez de travar o Maven.
    private static final int MAX_STEPS = 2_000_000;

    // Impede que o simulador auxiliar seja instanciado.
    private MarieTestMachine() {
        // Os testes chamam o método estático execute.
    }

    // Devolve as saídas produzidas pelo programa assembly para uma entrada dada.
    static Result execute(String assembly, Iterable<Integer> inputValues) {
        // Cria a tabela de rótulos para resolver LOAD, STORE e saltos.
        Map<String, Integer> labels = new HashMap<>();
        // Cria a tabela de palavras carregadas em cada endereço.
        Map<Integer, String> words = new LinkedHashMap<>();
        // Inicia a alocação no endereço padrão caso ORG não apareça.
        int address = 100;
        // Analisa o assembly linha a linha para atribuir endereços.
        for (String rawLine : assembly.split("\\R")) {
            // Remove o comentário MARIE para analisar somente a instrução.
            String code = rawLine.split("/", 2)[0].trim();
            // Ignora linhas em branco e comentários.
            if (code.isEmpty()) {
                // Comentários não ocupam uma palavra da memória.
                continue;
            }
            // Atualiza o endereço inicial ao encontrar a diretiva ORG.
            if (code.startsWith("ORG ")) {
                // Lê o endereço decimal usado pelo programa gerado.
                address = Integer.parseInt(code.substring(4).trim());
                // A diretiva não ocupa memória.
                continue;
            }
            // Associa o rótulo ao endereço da instrução ou dado desta linha.
            if (code.contains(",")) {
                // Separa somente a primeira vírgula reservada à declaração de rótulo.
                int separator = code.indexOf(',');
                // Extrai e valida o nome do rótulo.
                String label = code.substring(0, separator).trim();
                // Registra o endereço atual para resolução posterior.
                if (labels.put(label, address) != null) {
                    // Faz nomes duplicados falharem no teste do assembly.
                    throw new IllegalArgumentException("Rotulo duplicado no teste: " + label);
                }
                // Mantém o conteúdo da instrução ou da declaração.
                code = code.substring(separator + 1).trim();
            }
            // Guarda o conteúdo e reserva uma palavra da memória.
            words.put(address++, code);
        }
        // Inicializa somente as células DEC encontradas na primeira passagem.
        Map<Integer, Integer> memory = new HashMap<>();
        // Examina cada palavra para carregar as declarações de dados.
        for (Map.Entry<Integer, String> word : words.entrySet()) {
            // Separa o nome da operação e seus operandos.
            String[] parts = word.getValue().split("\\s+");
            // Inicializa células declaradas como DEC.
            if (parts[0].equalsIgnoreCase("DEC")) {
                // Converte o valor decimal para a palavra signed de 16 bits.
                memory.put(word.getKey(), toWord(Integer.parseInt(parts[1])));
            }
        }
        // Converte a entrada do teste para uma fila consumível por INPUT.
        Deque<Integer> input = new ArrayDeque<>();
        // Acrescenta valores na mesma ordem em que o programa deve lê-los.
        for (Integer value : inputValues) {
            // Converte cada valor para a representação signed da máquina.
            input.addLast(toWord(value));
        }
        // Guarda os valores produzidos por instruções OUTPUT.
        ListBuilder output = new ListBuilder();
        // Inicia a execução no endereço definido por ORG.
        int programCounter = 100;
        // Inicializa o acumulador como zero.
        int accumulator = 0;
        // Executa até HALT, erro ou limite de passos.
        for (int steps = 1; steps <= MAX_STEPS; steps++) {
            // Recupera a instrução localizada no contador de programa.
            String instruction = words.get(programCounter);
            // Falha claramente se um salto chegar a uma palavra não mapeada.
            if (instruction == null) {
                // Um PC sem instrução indica montagem ou fluxo de controle inválido.
                throw new IllegalStateException("Endereco sem instrucao MARIE: " + programCounter);
            }
            // Avança o PC antes da execução, como no ciclo de busca da MARIE.
            programCounter++;
            // Divide a linha em operação e operandos.
            String[] parts = instruction.split("\\s+");
            // Seleciona a semântica da instrução reconhecida.
            switch (parts[0].toUpperCase()) {
                // Carrega o conteúdo da memória para o acumulador.
                case "LOAD" -> accumulator = memory.getOrDefault(resolve(labels, parts[1]), 0);
                // Armazena o acumulador na célula indicada.
                case "STORE" -> memory.put(resolve(labels, parts[1]), toWord(accumulator));
                // Soma uma palavra da memória com aritmética de 16 bits.
                case "ADD" -> accumulator = toWord(accumulator
                        + memory.getOrDefault(resolve(labels, parts[1]), 0));
                // Subtrai uma palavra da memória com aritmética de 16 bits.
                case "SUBT" -> accumulator = toWord(accumulator
                        - memory.getOrDefault(resolve(labels, parts[1]), 0));
                // Zera o acumulador.
                case "CLEAR" -> accumulator = 0;
                // Consome o próximo inteiro fornecido pelo teste.
                case "INPUT" -> {
                    // Falha se o programa solicitar entrada que o teste não forneceu.
                    if (input.isEmpty()) {
                        // Evita mascarar uma solicitação inesperada de INPUT.
                        throw new IllegalStateException("O teste nao forneceu valor para INPUT.");
                    }
                    // Lê o valor seguinte da fila e carrega-o no AC.
                    accumulator = input.removeFirst();
                }
                // Acrescenta o acumulador à lista de saídas observadas.
                case "OUTPUT" -> output.add(toWord(accumulator));
                // Interrompe a execução e devolve as saídas.
                case "HALT" -> {
                    // Retorna também a quantidade de passos até HALT.
                    return new Result(output.values(), steps);
                }
                // Salta para o endereço do rótulo informado.
                case "JUMP" -> programCounter = resolve(labels, parts[1]);
                // Pula exatamente uma instrução conforme sinal ou zero do AC.
                case "SKIPCOND" -> {
                    // Identifica a condição MARIE representada pelo operando hexadecimal.
                    boolean shouldSkip = switch (parts[1]) {
                        // 000 pula quando o acumulador é negativo.
                        case "000" -> accumulator < 0;
                        // 400 pula quando o acumulador é exatamente zero.
                        case "400" -> accumulator == 0;
                        // 800 pula quando o acumulador é positivo.
                        case "800" -> accumulator > 0;
                        // Rejeita condições não suportadas no assembly testado.
                        default -> throw new IllegalArgumentException("SKIPCOND nao suportado: " + parts[1]);
                    };
                    // Avança por uma instrução quando a condição for verdadeira.
                    if (shouldSkip) {
                        // O incremento adicional implementa o salto MARIE de uma palavra.
                        programCounter++;
                    }
                }
                // Rejeita instruções que não fazem parte do contrato do compilador.
                default -> throw new IllegalArgumentException("Instrucao MARIE nao suportada: " + instruction);
            }
        }
        // Falha se uma rotina gerada nunca terminar dentro do limite de passos.
        throw new IllegalStateException("Programa MARIE excedeu " + MAX_STEPS + " passos.");
    }

    // Resolve um rótulo MARIE e lança erro quando ele não foi declarado.
    private static int resolve(Map<String, Integer> labels, String label) {
        // Obtém o endereço associado ao rótulo.
        Integer address = labels.get(label);
        // Rejeita saltos ou operandos sem destino válido.
        if (address == null) {
            // O diagnóstico informa exatamente qual rótulo não foi encontrado.
            throw new IllegalArgumentException("Rotulo MARIE nao encontrado: " + label);
        }
        // Retorna o endereço para LOAD, STORE ou JUMP.
        return address;
    }

    // Converte aritmética Java para o intervalo signed de 16 bits.
    private static int toWord(int value) {
        // O cast para short aplica o complemento de dois da palavra MARIE.
        return (short) value;
    }

    // Agrupa saídas e passos em um resultado imutável do simulador de teste.
    record Result(java.util.List<Integer> output, int steps) {
        // O record mantém o resultado simples e fácil de comparar nos testes.
    }

    // Acumula saídas em uma lista sem expor mutação ao consumidor.
    private static final class ListBuilder {
        // Guarda os números produzidos pelo programa em ordem.
        private final java.util.List<Integer> values = new ArrayList<>();

        // Acrescenta um número ao resultado observado.
        private void add(int value) {
            // Preserva a ordem das instruções OUTPUT.
            values.add(value);
        }

        // Devolve uma cópia imutável das saídas observadas.
        private java.util.List<Integer> values() {
            // Evita que uma chamada posterior altere o resultado de um teste.
            return java.util.List.copyOf(values);
        }
    }
}
