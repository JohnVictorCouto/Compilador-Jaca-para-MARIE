// Arquivo para o scanner a ser utilizado
package parser;
// Importar classes do cup - classe Symbol
import java_cup.runtime.Symbol;

%%

%class Scanner
%cup
%unicode
%line
%column
%yylexthrow Exception

%{
    // type é a classe do token
    // yyline e yycolumn são variáveis reservadas
    // do JFlex para armazenar a linha e a coluna
    // de um token encontrado na entrada (precisa
    //  usar %line e %column)
    private Symbol symbol(int type) {
        return new Symbol(type, yyline, yycolumn);
    }
    private Symbol symbol(int type, Object value) {
        return new Symbol(type, yyline, yycolumn, value);
    }
%}

%eofval{
    return symbol(sym.EOF);
%eofval}

%eof{
    //System.out.println("Análise léxica terminada com sucesso!");
%eof}

%{
// Macros
%}
delim	= [ \t\n\r\f] | \r\n

ws		= {delim}+

letter	= [_A-Za-z]

digit	= [0-9]

id		= {letter}({letter}|{digit}){0,31}

number	= {digit}+(\.{digit}+)?([Ee][+-]?{digit}+)?

comment = "#"[^\r\n]*

%%

/* Regras e ações */
{ws}		{ /* ignorar */ }
{comment}   { /* ignorar */ }
"if"	    { return symbol(sym.IF); }
"then"	    { return symbol(sym.THEN); }
"else"	    { return symbol(sym.ELSE); }
"while"	    { return symbol(sym.WHILE); }
"print"	    { return symbol(sym.PRINT); }
"read"	    { return symbol(sym.READ); }
{id}		{ return symbol(sym.ID, yytext()); }
{number}	{ return symbol(sym.NUMBER, Double.parseDouble(yytext())); }
"+"         { return symbol(sym.PLUS); }
"-"         { return symbol(sym.MINUS); }
"*"         { return symbol(sym.TIMES); }
"/"         { return symbol(sym.DIV); }
"%"         { return symbol(sym.MOD); }
"=="        { return symbol(sym.EQ); }
"!="        { return symbol(sym.NEQ); }
">"         { return symbol(sym.GT); }
"<"         { return symbol(sym.LT); }
">="        { return symbol(sym.GEQ); }
"<="        { return symbol(sym.LEQ); }
"="         { return symbol(sym.ASSIGN); }
";"         { return symbol(sym.SEMI); }
"("         { return symbol(sym.LPARAM); }
")"         { return symbol(sym.RPARAM); }
"{"         { return symbol(sym.LBRACE); }
"}"         { return symbol(sym.RBRACE); }

/* Qualquer outro - gerar erro */
.		    { throw new Exception("Erro de análise léxica (símbolo ilegal): " 
                + yytext() ); }