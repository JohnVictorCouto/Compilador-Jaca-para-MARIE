// Documenta os modos de execução sem alterar o nome público da classe principal.
package parser;

// Importa a codificação UTF-8 para ler e gravar exemplos portáveis.
import java.nio.charset.StandardCharsets;
// Importa a API moderna de arquivos e caminhos.
import java.nio.file.Files;
// Importa a representação de caminhos de entrada e saída.
import java.nio.file.Path;
// Importa a classe base da árvore sintática abstrata.
import parser.ast.ASTNode;
// Importa o visitor que interpreta a AST existente.
import parser.ast.Interpreter;
// Importa o visitor que gera assembly MARIE.
import parser.ast.MarieCompiler;

// Implementa a interface de linha de comando do interpretador e do compilador.
public final class Main {
    // Impede que a classe de entrada seja instanciada.
    private Main() {
        // O construtor privado mantém esta classe como ponto de entrada estático.
    }

    // Seleciona o REPL, a interpretação ou a compilação conforme os argumentos.
    public static void main(String[] args) {
        // Trata os erros num único ponto para preservar mensagens úteis e status.
        try {
            // Mantém o REPL original quando nenhum argumento é informado.
            if (args.length == 0) {
                // Inicia a sessão interativa do interpretador Jaca.
                runREPL();
                // Encerra o despacho depois de executar o REPL.
                return;
            }
            // Exibe a ajuda sem precisar carregar o parser.
            if (args.length == 1 && isHelp(args[0])) {
                // Imprime os formatos aceitos pela interface de linha de comando.
                printUsage();
                // Encerra o despacho depois de exibir a ajuda.
                return;
            }
            // Seleciona o fluxo de geração de assembly.
            if (args[0].equals("--compile")) {
                // Compila o arquivo e retorna depois de imprimir ou salvar o resultado.
                compileFile(args);
                // Evita interpretar o mesmo arquivo depois da compilação.
                return;
            }
            // Permite solicitar explicitamente o interpretador.
            if (args[0].equals("--interpret")) {
                // Valida e interpreta o único arquivo informado.
                interpretFile(args);
                // Encerra o despacho após a interpretação.
                return;
            }
            // Preserva o uso anterior em que o primeiro argumento é um arquivo Jaca.
            if (args.length == 1) {
                // Interpreta o arquivo para compatibilidade com o projeto-base.
                executeInterpreter(Path.of(args[0]));
                // Encerra o despacho depois da interpretação.
                return;
            }
            // Recusa formas desconhecidas em vez de ignorar argumentos.
            throw new IllegalArgumentException("Argumentos desconhecidos.");
        } catch (Exception exception) {
            // Envia o erro para stderr para separar diagnósticos de resultados.
            System.err.println("Erro: " + exception.getMessage());
            // Informa falha a scripts e ao shell por meio do código de saída.
            System.exit(1);
        }
    }

    // Confere se o argumento seleciona a página de ajuda.
    private static boolean isHelp(String argument) {
        // Aceita duas formas usuais sem modificar os demais argumentos.
        return argument.equals("--help") || argument.equals("-h");
    }

    // Compila um arquivo Jaca e imprime assembly ou grava-o em um arquivo.
    private static void compileFile(String[] args) throws Exception {
        // Exige pelo menos o nome do arquivo de origem.
        if (args.length < 2) {
            // Informa o formato da opção em vez de falhar com índice inválido.
            throw new IllegalArgumentException("Uso: --compile <arquivo.jaca> [-o <arquivo.mas>]");
        }
        // Usa o primeiro argumento após --compile como entrada.
        Path input = Path.of(args[1]);
        // Começa sem destino para que o assembly seja impresso em stdout.
        Path output = null;
        // Percorre opções adicionais na ordem em que foram fornecidas.
        for (int index = 2; index < args.length; index++) {
            // Reconhece o único parâmetro opcional de arquivo de saída.
            if (args[index].equals("-o") && index + 1 < args.length && output == null) {
                // Captura o caminho que sucede -o.
                output = Path.of(args[++index]);
            } else {
                // Rejeita opções duplicadas, valores ausentes e parâmetros inesperados.
                throw new IllegalArgumentException("Opcao invalida de compilacao: " + args[index]);
            }
        }
        // Faz a análise léxica e sintática e obtém a AST da entrada.
        ASTNode root = parseFile(input);
        // Cria um visitor novo para manter estado de compilação isolado.
        MarieCompiler compiler = new MarieCompiler();
        // Gera o programa MARIE completo a partir da árvore.
        String assembly = compiler.compile(root);
        // Grava o assembly quando um caminho de saída foi fornecido.
        if (output != null) {
            // Cria diretórios intermediários somente se o usuário os pediu no caminho.
            Path parent = output.toAbsolutePath().getParent();
            // Garante que o diretório de saída exista antes de escrever.
            if (parent != null) {
                // Cria toda a hierarquia de diretórios ausente.
                Files.createDirectories(parent);
            }
            // Salva o texto com codificação estável para o simulador.
            Files.writeString(output, assembly, StandardCharsets.UTF_8);
            // Confirma no stderr onde o arquivo foi gravado.
            System.err.println("Assembly MARIE salvo em: " + output);
        } else {
            // Envia assembly para stdout sem adicionar mensagens que contaminem o arquivo.
            System.out.print(assembly);
        }
    }

    // Interpreta o formato explícito --interpret arquivo.jaca.
    private static void interpretFile(String[] args) throws Exception {
        // Exige exatamente um arquivo depois da opção.
        if (args.length != 2) {
            // Explica o formato aceito para evitar argumentos ignorados.
            throw new IllegalArgumentException("Uso: --interpret <arquivo.jaca>");
        }
        // Executa o interpretador sobre a entrada indicada.
        executeInterpreter(Path.of(args[1]));
    }

    // Analisa um arquivo e executa sua AST com o interpretador preexistente.
    private static void executeInterpreter(Path source) throws Exception {
        // Recupera a AST depois da análise léxica e sintática.
        ASTNode root = parseFile(source);
        // Executa comandos sem substituir o comportamento do interpretador-base.
        root.accept(new Interpreter());
    }

    // Cria uma AST a partir do conteúdo UTF-8 de um arquivo.
    private static ASTNode parseFile(Path source) throws Exception {
        // Valida o arquivo antes de iniciar scanner ou parser.
        if (!Files.isRegularFile(source)) {
            // Falha com o caminho procurado em vez de retornar uma AST vazia.
            throw new IllegalArgumentException("Arquivo nao encontrado: " + source);
        }
        // Abre o arquivo em UTF-8 e fecha o leitor ao concluir o parsing.
        try (java.io.Reader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            // Cria o scanner JFlex para converter caracteres em tokens CUP.
            Scanner scanner = new Scanner(reader);
            // Cria o parser CUP que constrói a árvore sintática.
            Parser parser = new Parser(scanner);
            // Executa a análise e propaga qualquer erro léxico ou sintático.
            parser.parse();
            // Recusa árvores parciais após a recuperação automática do CUP.
            if (parser.getSyntaxErrorCount() > 0) {
                // Faz a CLI retornar status de falha em vez de compilar código incompleto.
                throw new IllegalArgumentException("A fonte contem erros de sintaxe; consulte a linha e coluna acima.");
            }
            // Recupera a árvore que as ações da gramática construíram.
            ASTNode root = parser.getAST();
            // Valida que o parser produziu um nó utilizável.
            if (root == null) {
                // Não continua com uma compilação ou interpretação sem AST.
                throw new IllegalStateException("O parser nao gerou uma AST para " + source + ".");
            }
            // Entrega a raiz ao modo de execução selecionado.
            return root;
        }
    }

    // Inicia a sessão interativa original, mantendo estado de variáveis entre blocos.
    public static void runREPL() {
        // Apresenta as instruções básicas da sessão.
        System.out.println("Jaca 1.0 REPL - pressione Enter em uma linha vazia para executar; digite exit para sair.");
        // Cria um leitor para comandos digitados pelo usuário.
        java.io.BufferedReader console = new java.io.BufferedReader(new java.io.InputStreamReader(System.in));
        // Reutiliza um interpretador para preservar variáveis entre blocos do REPL.
        Interpreter interpreter = new Interpreter();
        // Acumula linhas até o usuário enviar uma linha vazia.
        StringBuilder buffer = new StringBuilder();
        // Mantém o REPL ativo até EOF ou o comando exit.
        while (true) {
            // Mostra um prompt diferente quando já há um bloco em construção.
            System.out.print(buffer.length() == 0 ? "> " : "... ");
            // Garante que o prompt apareça antes da leitura.
            System.out.flush();
            // Lê uma linha sem descartar o restante do bloco atual.
            String line;
            // Trata falhas do console sem esconder a causa.
            try {
                // Obtém a próxima linha digitada ou null ao receber EOF.
                line = console.readLine();
            } catch (java.io.IOException exception) {
                // Exibe a falha de entrada e termina a sessão.
                System.err.println("Erro ao ler o console: " + exception.getMessage());
                // Encerra o ciclo do REPL.
                break;
            }
            // Termina de forma normal se a entrada padrão fechar.
            if (line == null) {
                // Não há mais comandos para processar.
                break;
            }
            // Aceita exit independentemente de maiúsculas e espaços externos.
            if (line.trim().equalsIgnoreCase("exit")) {
                // Descarta qualquer trecho ainda não executado ao encerrar.
                break;
            }
            // Executa o bloco quando uma linha vazia finaliza o buffer.
            if (line.trim().isEmpty()) {
                // Evita analisar uma entrada sem comandos.
                if (buffer.length() > 0) {
                    // Copia o bloco antes de limpar o buffer para aceitar mais entrada.
                    String source = buffer.toString();
                    // Limpa o conteúdo antes de executar para recuperar o prompt em erro.
                    buffer.setLength(0);
                    // Analisa o bloco e executa sua AST com a mesma tabela do interpretador.
                    executeREPLBlock(source, interpreter);
                }
                // Aguarda o próximo bloco após a linha vazia.
                continue;
            }
            // Acrescenta a linha e preserva as quebras para o parser.
            buffer.append(line).append('\n');
        }
    }

    // Analisa e executa um bloco do REPL com tratamento de erro recuperável.
    private static void executeREPLBlock(String source, Interpreter interpreter) {
        // Mantém o REPL vivo se houver erro léxico, sintático ou de execução.
        try {
            // Cria scanner para a string acumulada no buffer.
            Scanner scanner = new Scanner(new java.io.StringReader(source));
            // Cria parser para construir a AST deste bloco.
            Parser parser = new Parser(scanner);
            // Analisa o texto digitado.
            parser.parse();
            // Não executa uma AST parcial depois de erro de recuperação sintática.
            if (parser.getSyntaxErrorCount() > 0) {
                // Reporta a falha sem encerrar a sessão interativa.
                throw new IllegalArgumentException("A fonte contem erros de sintaxe.");
            }
            // Recupera a AST produzida pelo parser.
            ASTNode root = parser.getAST();
            // Executa apenas quando a raiz da AST foi construída.
            if (root != null) {
                // Reutiliza o interpretador para manter os valores entre blocos.
                root.accept(interpreter);
            }
        } catch (Exception exception) {
            // Expõe a causa real em vez de apresentar apenas uma mensagem genérica.
            System.err.println("Erro no bloco: " + exception.getMessage());
        }
    }

    // Imprime todos os comandos públicos da ferramenta.
    private static void printUsage() {
        // Lista a forma histórica de interpretar um arquivo.
        System.out.println("Uso:");
        // Documenta o modo explícito de interpretação.
        System.out.println("  java -jar target/jacacc-1.0-jar-with-dependencies.jar --interpret <arquivo.jaca>");
        // Documenta a forma curta mantida por compatibilidade.
        System.out.println("  java -jar target/jacacc-1.0-jar-with-dependencies.jar <arquivo.jaca>");
        // Documenta a geração de assembly em stdout ou arquivo.
        System.out.println("  java -jar target/jacacc-1.0-jar-with-dependencies.jar --compile <arquivo.jaca> [-o <arquivo.mas>]");
        // Documenta como abrir a sessão interativa.
        System.out.println("  java -jar target/jacacc-1.0-jar-with-dependencies.jar");
    }
}
