// Identifica o pacote responsável pelo estado auxiliar da geração MARIE.
package parser.ast.compiler;

// Importa estruturas ordenadas para conservar a ordem previsível das declarações.
import java.util.LinkedHashMap;
// Importa o contrato de mapas usado para associar nomes às etiquetas geradas.
import java.util.Map;

// Mantém os mapas e contadores de uma única compilação Jaca para MARIE.
public final class MarieContext {
    // Guarda cada variável Jaca e o rótulo MARIE seguro associado a ela.
    private final Map<String, String> variables = new LinkedHashMap<>();
    // Guarda cada inteiro literal e evita duplicar a constante na memória.
    private final Map<Integer, String> constants = new LinkedHashMap<>();
    // Guarda as declarações na ordem em que surgem durante a visita da AST.
    private final Map<String, String> dataDeclarations = new LinkedHashMap<>();
    // Conta os temporários usados para preservar valores intermediários do AC.
    private int temporaryCounter;
    // Conta rótulos internos usados por saltos, comparações e laços.
    private int labelCounter;
    // Registra se algum operador de divisão ou resto precisa do desvio de erro.
    private boolean divisionTrapRequired;

    // Retorna o rótulo da variável e reserva sua palavra quando necessário.
    public String variable(String sourceName) {
        // Reutiliza o rótulo da variável se o identificador já foi encontrado.
        return variables.computeIfAbsent(sourceName, name -> {
            // Usa um prefixo reservado e um contador para evitar colisões com o usuário.
            String label = "V" + variables.size();
            // Declara as variáveis com zero, como exige a memória da MARIE.
            dataDeclarations.put(label, "DEC 0\t/ variavel " + safeComment(name));
            // Devolve o rótulo novo para a chamada que solicitou a variável.
            return label;
        });
    }

    // Retorna o rótulo de uma constante inteira e compartilha valores repetidos.
    public String constant(int value) {
        // Reutiliza a declaração existente quando o literal já foi compilado.
        return constants.computeIfAbsent(value, number -> {
            // Usa um prefixo diferente do das variáveis e dos temporários.
            String label = "C" + constants.size();
            // Declara o literal como palavra decimal da arquitetura MARIE.
            dataDeclarations.put(label, "DEC " + number + "\t/ constante " + number);
            // Devolve o rótulo que será usado pelos LOAD e ADD gerados.
            return label;
        });
    }

    // Reserva uma palavra temporária distinta para uma expressão intermediária.
    public String temporary() {
        // Cria um nome temporário sem depender dos identificadores da linguagem.
        String label = "T" + temporaryCounter++;
        // Inicializa o espaço para que o programa assembly seja completamente declarado.
        dataDeclarations.put(label, "DEC 0\t/ temporario de compilacao");
        // Devolve o rótulo que pode ser usado por STORE e LOAD.
        return label;
    }

    // Cria um rótulo de controle de fluxo único dentro desta compilação.
    public String label() {
        // Usa uma sequência monotônica para impedir rótulos duplicados.
        return "L" + labelCounter++;
    }

    // Marca a necessidade de criar o destino comum para divisão por zero.
    public void requireDivisionTrap() {
        // A flag é limpa ao construir um novo contexto para cada compilação.
        divisionTrapRequired = true;
    }

    // Informa se o programa final precisa declarar o destino de erro.
    public boolean needsDivisionTrap() {
        // Retorna o estado registrado pelos visitantes de divisão e resto.
        return divisionTrapRequired;
    }

    // Formata uma instrução sem rótulo e acrescenta comentário MARIE seguro.
    public String instruction(String operation, String explanation) {
        // Mantém as instruções alinhadas e torna cada etapa visível ao estudante.
        return "\t" + operation + "\t/ " + safeComment(explanation) + "\n";
    }

    // Formata uma instrução com rótulo na mesma linha.
    public String labeled(String label, String operation, String explanation) {
        // MARIE exige vírgula depois do rótulo e não admite vírgula no comentário.
        return label + ",\t" + operation + "\t/ " + safeComment(explanation) + "\n";
    }

    // Constrói o programa final com código, HALT, trap opcional e dados.
    public String assemble(String body) {
        // Usa um buffer para montar a saída sem criar muitas strings intermediárias.
        StringBuilder assembly = new StringBuilder();
        // Informa ao montador MARIE o endereço inicial do programa.
        assembly.append("ORG 100\t/ inicio do programa Jaca\n");
        // Separa o código executável dos dados na listagem legível.
        assembly.append("\t/ inicio do codigo gerado\n");
        // Acrescenta, na ordem, as instruções produzidas pelo visitor.
        assembly.append(body);
        // Impede que a execução normal continue para a área de dados.
        assembly.append(instruction("HALT", "fim normal do programa"));
        // Declara o destino de erro depois do término normal do programa.
        if (divisionTrapRequired) {
            // O desvio para este rótulo interrompe a execução em divisor zero.
            assembly.append(labeled("DIVZERO", "HALT", "erro em tempo de execucao: divisao por zero"));
        }
        // Adiciona uma seção de dados explícita e fácil de localizar.
        assembly.append("\n\t/ inicio da secao de dados\n");
        // Emite as variáveis, constantes e temporários na ordem de descoberta.
        for (Map.Entry<String, String> declaration : dataDeclarations.entrySet()) {
            // Mantém uma declaração por palavra e inclui um comentário explicativo.
            assembly.append(declaration.getKey()).append(",\t").append(declaration.getValue()).append('\n');
        }
        // Converte o buffer para a saída que será impressa ou salva em arquivo.
        String result = assembly.toString();
        // Impede programas cuja seção de código e dados excederia a memória MARIE.
        verifyMemoryCapacity(result);
        // Devolve o assembly pronto para ser montado no MARIE.js.
        return result;
    }

    // Substitui vírgulas porque a sintaxe MARIE reserva vírgula para os rótulos.
    private String safeComment(String explanation) {
        // Troca somente caracteres que poderiam encerrar ou quebrar uma declaração.
        return explanation.replace(',', ';').replace('\n', ' ').replace('\r', ' ');
    }

    // Conta palavras ocupadas a partir de ORG 100 e valida os 4K endereços.
    private void verifyMemoryCapacity(String assembly) {
        // Cada linha de instrução ou DEC consome uma palavra da memória MARIE.
        int words = 0;
        // Percorre as linhas para ignorar comentários e a diretiva ORG.
        for (String rawLine : assembly.split("\\R")) {
            // Descarta qualquer comentário antes de interpretar a instrução.
            String code = rawLine.split("/", 2)[0].trim();
            // Ignora linhas vazias e a diretiva que define o endereço inicial.
            if (code.isEmpty() || code.startsWith("ORG ")) {
                // Continua para a próxima linha sem consumir memória.
                continue;
            }
            // Conta uma palavra para cada instrução ou declaração de dados.
            words++;
        }
        // ORG 100 deixa 3996 palavras disponíveis antes do limite 4095.
        if (words > 4096 - 100) {
            // Falha explicitamente em vez de gerar um assembly que não cabe na máquina.
            throw new IllegalStateException("Programa excede a memoria MARIE: " + words
                    + " palavras para um limite de " + (4096 - 100) + ".");
        }
    }
}
