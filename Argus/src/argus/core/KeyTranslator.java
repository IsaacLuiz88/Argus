package argus.core;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.swt.SWT;

public class KeyTranslator {
    private static final Map<Integer, String> KEY_MAP = new HashMap<>();
    
    static {
        // Teclas especiais - fazendo cast para int
        KEY_MAP.put((int) SWT.BS, "BACKSPACE");
        KEY_MAP.put((int) SWT.TAB, "TAB");
        KEY_MAP.put((int) SWT.CR, "ENTER");
        KEY_MAP.put((int) SWT.ESC, "ESC");
        KEY_MAP.put((int) SWT.SPACE, "ESPAÇO");
        KEY_MAP.put((int) SWT.DEL, "DELETE");
        KEY_MAP.put(SWT.INSERT, "INSERT");
        KEY_MAP.put(SWT.HOME, "HOME");
        KEY_MAP.put(SWT.END, "END");
        KEY_MAP.put(SWT.PAGE_UP, "PAGE_UP");
        KEY_MAP.put(SWT.PAGE_DOWN, "PAGE_DOWN");
        
        // Teclas de função
        KEY_MAP.put(SWT.F1, "F1");
        KEY_MAP.put(SWT.F2, "F2");
        KEY_MAP.put(SWT.F3, "F3");
        KEY_MAP.put(SWT.F4, "F4");
        KEY_MAP.put(SWT.F5, "F5");
        KEY_MAP.put(SWT.F6, "F6");
        KEY_MAP.put(SWT.F7, "F7");
        KEY_MAP.put(SWT.F8, "F8");
        KEY_MAP.put(SWT.F9, "F9");
        KEY_MAP.put(SWT.F10, "F10");
        KEY_MAP.put(SWT.F11, "F11");
        KEY_MAP.put(SWT.F12, "F12");
        
        // Teclas de direção
        KEY_MAP.put(SWT.ARROW_UP, "SETA_CIMA");
        KEY_MAP.put(SWT.ARROW_DOWN, "SETA_BAIXO");
        KEY_MAP.put(SWT.ARROW_LEFT, "SETA_ESQUERDA");
        KEY_MAP.put(SWT.ARROW_RIGHT, "SETA_DIREITA");
        
        // Teclas de controle
        KEY_MAP.put(SWT.SHIFT, "SHIFT");
        KEY_MAP.put(SWT.CTRL, "CTRL");
        KEY_MAP.put(SWT.ALT, "ALT");
        KEY_MAP.put(SWT.CAPS_LOCK, "CAPS_LOCK");
        KEY_MAP.put(SWT.NUM_LOCK, "NUM_LOCK");
        KEY_MAP.put(SWT.SCROLL_LOCK, "SCROLL_LOCK");
        KEY_MAP.put(SWT.COMMAND, "COMMAND");
        
        // Teclado numérico
        KEY_MAP.put(SWT.KEYPAD_0, "NUM0");
        KEY_MAP.put(SWT.KEYPAD_1, "NUM1");
        KEY_MAP.put(SWT.KEYPAD_2, "NUM2");
        KEY_MAP.put(SWT.KEYPAD_3, "NUM3");
        KEY_MAP.put(SWT.KEYPAD_4, "NUM4");
        KEY_MAP.put(SWT.KEYPAD_5, "NUM5");
        KEY_MAP.put(SWT.KEYPAD_6, "NUM6");
        KEY_MAP.put(SWT.KEYPAD_7, "NUM7");
        KEY_MAP.put(SWT.KEYPAD_8, "NUM8");
        KEY_MAP.put(SWT.KEYPAD_9, "NUM9");
        KEY_MAP.put(SWT.KEYPAD_DIVIDE, "NUM_DIVIDE");
        KEY_MAP.put(SWT.KEYPAD_MULTIPLY, "NUM_MULTIPLY");
        KEY_MAP.put(SWT.KEYPAD_SUBTRACT, "NUM_SUBTRACT");
        KEY_MAP.put(SWT.KEYPAD_ADD, "NUM_ADD");
        KEY_MAP.put(SWT.KEYPAD_DECIMAL, "NUM_DECIMAL");
        KEY_MAP.put(SWT.KEYPAD_CR, "NUM_ENTER");
        KEY_MAP.put(SWT.KEYPAD_EQUAL, "NUM_IGUAL");
        
        // Teclas de caracteres (A-Z) - SWT usa os valores ASCII padrão
        for (char c = 'A'; c <= 'Z'; c++) {
            KEY_MAP.put((int) c, String.valueOf(c));
        }
        
        // Teclas numéricas (0-9)
        for (char c = '0'; c <= '9'; c++) {
            KEY_MAP.put((int) c, String.valueOf(c));
        }
        
        // Caracteres especiais comuns
        KEY_MAP.put((int) ',', "VÍRGULA");
        KEY_MAP.put((int) '.', "PONTO");
        KEY_MAP.put((int) ';', "PONTO_VÍRGULA");
        KEY_MAP.put((int) '=', "IGUAL");
        KEY_MAP.put((int) '-', "HÍFEN");
        KEY_MAP.put((int) '/', "BARRA");
        KEY_MAP.put((int) '[', "COLCHETE_ESQ");
        KEY_MAP.put((int) ']', "COLCHETE_DIR");
        KEY_MAP.put((int) '\\', "BARRA_INVERSA");
        KEY_MAP.put((int) '\'', "ASPAS_SIMPLES");
        KEY_MAP.put((int) '`', "ACENTO_GRAVE");
        
        // Caracteres adicionais
        KEY_MAP.put((int) '~', "TIL");
        KEY_MAP.put((int) '!', "EXCLAMAÇÃO");
        KEY_MAP.put((int) '@', "ARROBA");
        KEY_MAP.put((int) '#', "HASHTAG");
        KEY_MAP.put((int) '$', "CIFRÃO");
        KEY_MAP.put((int) '%', "PORCENTO");
        KEY_MAP.put((int) '^', "ACENTO_CIRCUNFLEXO");
        KEY_MAP.put((int) '&', "E_COMERCIAL");
        KEY_MAP.put((int) '*', "ASTERISCO");
        KEY_MAP.put((int) '(', "PARENTESE_ESQ");
        KEY_MAP.put((int) ')', "PARENTESE_DIR");
        KEY_MAP.put((int) '_', "UNDERSCORE");
        KEY_MAP.put((int) '+', "MAIS");
        KEY_MAP.put((int) '{', "CHAVE_ESQ");
        KEY_MAP.put((int) '}', "CHAVE_DIR");
        KEY_MAP.put((int) '|', "PIPE");
        KEY_MAP.put((int) ':', "DOIS_PONTOS");
        KEY_MAP.put((int) '"', "ASPAS_DUPLAS");
        KEY_MAP.put((int) '<', "MENOR_QUE");
        KEY_MAP.put((int) '>', "MAIOR_QUE");
        KEY_MAP.put((int) '?', "INTERROGAÇÃO");
    }

    public static String translate(int keyCode) {
        String keyName = KEY_MAP.get(keyCode);
        if (keyName != null) {
            return keyName;
        }

        // Para códigos não mapeados, tenta converter para caractere
        if (keyCode >= 32 && keyCode <= 126) {
            return String.valueOf((char) keyCode);
        }
        return "DESCONHECIDO(" + keyCode + ")";
    }
}