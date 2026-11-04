// Declara o pacote que contém os valores do interpretador.
package parser.ast;

// Encapsula um valor Jaca que pode ser numérico ou booleano.
public class Value {
    // Mantém o valor interno sem conversão implícita entre tipos.
    private final Object value;

    // Cria um wrapper em torno de um número Double ou Boolean.
    public Value(Object value) {
        // Armazena a referência que será consultada pelos métodos de tipo.
        this.value = value;
    }

    // Informa se o valor encapsulado é booleano.
    public boolean isBoolean() {
        // Usa o tipo dinâmico para preservar a distinção de tipos da linguagem.
        return value instanceof Boolean;
    }

    // Informa se o valor encapsulado é numérico Double.
    public boolean isDouble() {
        // O interpretador representa os números Jaca com Double.
        return value instanceof Double;
    }

    // Devolve o valor como Boolean quando a operação já validou seu tipo.
    public Boolean asBoolean() {
        // O cast explícito falha se o chamador ignorar isBoolean.
        return (Boolean) value;
    }

    // Devolve o valor como Double quando a operação já validou seu tipo.
    public Double asDouble() {
        // O cast explícito falha se o chamador ignorar isDouble.
        return (Double) value;
    }

    // Representa o valor com a formatação padrão da JVM.
    @Override
    public String toString() {
        // Converte Boolean ou Double para texto sem regras adicionais.
        return String.valueOf(value);
    }
}