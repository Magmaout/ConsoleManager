package ru.magmaout.cm;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.io.InputStreamReader;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.Stack;
import java.util.regex.Pattern;
import static java.lang.foreign.ValueLayout.*;

public final class ConsoleManager {
    private final InputStreamReader reader = new InputStreamReader(System.in, StandardCharsets.UTF_8);
    private final Stack<IFormat[]> formatStack = new Stack<>();
    private String replacementChars = "{}";
    private String listSeparator = ",";
    private String listMarker = Markers.BULLET;

    /** Objects output */
    public void out(Object... out) {
        for (int i = 0; i < out.length; ++i) {
            if (i > 0) System.out.print(listSeparator + ' ');
            System.out.print(out[i]);
        }
    }

    /** Objects output and line break */
    public void outln(Object... out) {
        if (out.length == 0) System.out.println();
        else if (out.length == 1) System.out.println(out[0]);
        else for (Object obj : out) System.out.println(listMarker + ' ' + obj);
    }

    /** Objects output and requesting user input */
    public String outin(Object out) { return outin(out, null, -1, false); }
    public String outin(Object out, Pattern allowedPattern) { return outin(out, allowedPattern, -1, false); }
    public String outin(Object out, Pattern allowedPattern, int maxLength) { return outin(out, allowedPattern, maxLength, false); }
    public String outin(Object out, Pattern allowedPattern, int maxLength, boolean hiddenInput) {
        String msg = String.valueOf(out);
        String[] parts = msg.split(Pattern.quote(replacementChars), -1);
        if (parts.length == 1) { out(msg); return in(allowedPattern, maxLength, hiddenInput, ""); }
        StringBuilder inputs = new StringBuilder();
        out(parts[0]);
        for (int i = 1; i < parts.length; ++i) {
            if (i > 1) inputs.append(listSeparator);
            inputs.append(in(allowedPattern, maxLength, hiddenInput, parts[i]));
        }
        return inputs.toString();
    }

    /**User input request */
    public String in() { return in(null, -1, false, ""); }
    public String in(Pattern allowedPattern) { return in(allowedPattern, -1, false, ""); }
    public String in(Pattern allowedPattern, int maxLength) { return in(allowedPattern, maxLength, false, ""); }
    public String in(Pattern allowedPattern, int maxLength, boolean hiddenInput) { return in(allowedPattern, maxLength, hiddenInput, ""); }
    private String in(Pattern allowedPattern, int maxLength, boolean hiddenInput, String nextPart) {
        StringBuilder input = new StringBuilder();
        int cursor = 0, selectionAnchor = -1;
        int cr = nextPart.indexOf('\r'), lf = nextPart.indexOf('\n');
        int lineBreak = cr < 0 ? lf : lf < 0 ? cr : Math.min(cr, lf);
        String thisPart = lineBreak < 0 ? nextPart : nextPart.substring(0, lineBreak);
        String suffixAfterEnter = lineBreak < 0 ? "" : nextPart.substring(lineBreak);
        int suffixLength = excludeANSI(thisPart).length();
        System.out.print(thisPart + moveLeft(suffixLength));
        try {
            while (true) {
                int code = reader.read();
                if (code == -1) break;
                char symbol = (char) code;

                if (symbol == '\001') { // Ctrl+A — выделить всё
                    selectionAnchor = 0;
                    cursor = input.length();
                    renderInput(input, cursor, cursor, selectionAnchor, thisPart, suffixLength, hiddenInput);
                    System.out.flush();
                    continue;
                }
                if (symbol == '\003') { // Ctrl+C — копировать
                    int[] sel = selectionRange(cursor, selectionAnchor);
                    if (sel != null) {
                        setClipboard(input.substring(sel[0], sel[1]));
                        renderInput(input, cursor, cursor, selectionAnchor, thisPart, suffixLength, hiddenInput);
                        System.out.flush();
                    }
                    continue;
                }
                if (symbol == '\030') { // Ctrl+X — вырезать
                    int[] sel = selectionRange(cursor, selectionAnchor);
                    if (sel != null) {
                        setClipboard(input.substring(sel[0], sel[1]));
                        int oldCursor = cursor;
                        input.delete(sel[0], sel[1]);
                        cursor = sel[0];
                        selectionAnchor = -1;
                        renderInput(input, oldCursor, cursor, selectionAnchor, thisPart, suffixLength, hiddenInput);
                        System.out.flush();
                    }
                    continue;
                }
                if (symbol == '\026') { // Ctrl+V — вставить
                    String clip = getClipboard();
                    if (!clip.isEmpty()) {
                        int oldCursor = cursor;
                        int[] sel = selectionRange(cursor, selectionAnchor);
                        if (sel != null) {
                            input.delete(sel[0], sel[1]);
                            cursor = sel[0];
                            selectionAnchor = -1;
                        }
                        input.insert(cursor, clip);
                        cursor += clip.length();
                        renderInput(input, oldCursor, cursor, selectionAnchor, thisPart, suffixLength, hiddenInput);
                        System.out.flush();
                    }
                    continue;
                }

                if (symbol == '\r' || symbol == '\n') {
                    System.out.print(moveRight(suffixLength + input.length() - cursor) + suffixAfterEnter);
                    System.out.flush();
                    break;
                }

                if (symbol == '\033') {
                    Key key = readEscapeKey();
                    int oldCursor = cursor;
                    int[] selection = selectionRange(cursor, selectionAnchor);
                    switch (key) {
                        case LEFT -> {
                            if (selection != null) cursor = selection[0];
                            else if (cursor > 0) --cursor;
                            selectionAnchor = -1;
                        }
                        case RIGHT -> {
                            if (selection != null) cursor = selection[1];
                            else if (cursor < input.length()) ++cursor;
                            selectionAnchor = -1;
                        }
                        case CTRL_LEFT -> {
                            if (selection != null) cursor = selection[0];
                            else cursor = prevWordBoundary(input, cursor);
                            selectionAnchor = -1;
                        }
                        case CTRL_RIGHT -> {
                            if (selection != null) cursor = selection[1];
                            else cursor = nextWordBoundary(input, cursor);
                            selectionAnchor = -1;
                        }
                        case SHIFT_LEFT -> {
                            if (selectionAnchor < 0) selectionAnchor = cursor;
                            if (cursor > 0) --cursor;
                        }
                        case SHIFT_RIGHT -> {
                            if (selectionAnchor < 0) selectionAnchor = cursor;
                            if (cursor < input.length()) ++cursor;
                        }
                        case CTRL_SHIFT_LEFT -> {
                            if (selectionAnchor < 0) selectionAnchor = cursor;
                            cursor = prevWordBoundary(input, cursor);
                        }
                        case CTRL_SHIFT_RIGHT -> {
                            if (selectionAnchor < 0) selectionAnchor = cursor;
                            cursor = nextWordBoundary(input, cursor);
                        }
                        case HOME -> { cursor = 0; selectionAnchor = -1; }
                        case END  -> { cursor = input.length(); selectionAnchor = -1; }
                        case DELETE -> {
                            if (selection != null) {
                                input.delete(selection[0], selection[1]);
                                cursor = selection[0];
                                selectionAnchor = -1;
                            } else if (cursor < input.length()) input.deleteCharAt(cursor);
                        }
                    }
                    renderInput(input, oldCursor, cursor, selectionAnchor, thisPart, suffixLength, hiddenInput);
                    continue;
                }

                if (symbol == '\b' || code == 127) {
                    int oldCursor = cursor;
                    int[] selection = selectionRange(cursor, selectionAnchor);
                    if (selection != null) {
                        input.delete(selection[0], selection[1]);
                        cursor = selection[0];
                        selectionAnchor = -1;
                    } else if (cursor > 0) input.deleteCharAt(--cursor);
                    else continue;
                    renderInput(input, oldCursor, cursor, selectionAnchor, thisPart, suffixLength, hiddenInput);
                    System.out.flush();
                    continue;
                }

                int oldCursor = cursor;
                int[] selection = selectionRange(cursor, selectionAnchor);
                int inputLengthAfterSelectionDelete = selection == null ? input.length() : input.length() - (selection[1] - selection[0]);
                if (
                    (maxLength >= 0 && inputLengthAfterSelectionDelete >= maxLength) || (
                        Patterns.DECIMAL.equals(allowedPattern) && (symbol == '.' || symbol == ',') &&
                        (input.isEmpty() || input.indexOf(String.valueOf(symbol)) > 0)
                    ) || (
                        Patterns.EMAIL.equals(allowedPattern) && (
                            (symbol == '@' && (input.isEmpty() || input.indexOf(String.valueOf(symbol)) > 0)) ||
                            (symbol == '.' && (input.isEmpty() || input.charAt(input.length() - 1) == symbol))
                        )
                    )
                ) continue;

                if (allowedPattern == null || allowedPattern.matcher(String.valueOf(symbol)).matches()) {
                    if (selection != null) {
                        input.delete(selection[0], selection[1]);
                        cursor = selection[0];
                        selectionAnchor = -1;
                    }
                    input.insert(cursor++, symbol);
                    renderInput(input, oldCursor, cursor, selectionAnchor, thisPart, suffixLength, hiddenInput);
                    System.out.flush();
                }
            }
        } catch (Throwable ignored) {}
        return Patterns.DECIMAL.equals(allowedPattern) ? input.toString().replace(',', '.') : input.toString();
    }

    private void renderInput(StringBuilder input, int oldCursor, int cursor, int selectionAnchor, String thisPart, int suffixLength, boolean hiddenInput) {
        int[] selection = selectionRange(cursor, selectionAnchor);
        StringBuilder visibleInput = new StringBuilder();
        boolean selectionEnabled = false;
        for (int i = 0; i < input.length(); ++i) {
            boolean selected = selection != null && i >= selection[0] && i < selection[1];
            if (selected && !selectionEnabled) { visibleInput.append("\033[7m"); selectionEnabled = true; }
            else if (!selected && selectionEnabled) { visibleInput.append("\033[27m"); selectionEnabled = false; }
            visibleInput.append(hiddenInput ? '*' : input.charAt(i));
        }
        if (selectionEnabled) visibleInput.append("\033[27m");
        System.out.print(moveLeft(oldCursor) + visibleInput + thisPart + "\033[0J" + moveLeft(suffixLength + input.length() - cursor));
    }

    private static int[] selectionRange(int cursor, int selectionAnchor) {
        if (selectionAnchor < 0 || selectionAnchor == cursor) return null;
        return new int[]{Math.min(cursor, selectionAnchor), Math.max(cursor, selectionAnchor)};
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
    private static int prevWordBoundary(StringBuilder input, int cursor) {
        if (cursor <= 0) return 0;
        int i = cursor - 1;
        while (i > 0 && !isWordChar(input.charAt(i))) i--;
        while (i > 0 && isWordChar(input.charAt(i - 1))) i--;
        return i;
    }
    private static int nextWordBoundary(StringBuilder input, int cursor) {
        int len = input.length();
        if (cursor >= len) return len;
        int i = cursor;
        while (i < len && isWordChar(input.charAt(i))) i++;
        while (i < len && !isWordChar(input.charAt(i))) i++;
        return i;
    }

    private Key readEscapeKey() throws Exception {
        if (reader.read() != '[') return Key.UNKNOWN;
        StringBuilder sequence = new StringBuilder();
        while (true) {
            int code = reader.read();
            if (code == -1) return Key.UNKNOWN;
            char symbol = (char) code;
            sequence.append(symbol);
            if ((symbol >= 'A' && symbol <= 'Z') || symbol == '~') break;
        }
        String value = sequence.toString();
        int modifier = 0;
        int semi = value.indexOf(';');
        if (semi >= 0) {
            try { modifier = Integer.parseInt(value.substring(semi + 1, value.length() - 1)); }
            catch (NumberFormatException ignored) {}
        }
        char command = value.charAt(value.length() - 1);
        return switch (command) {
            case 'D' -> switch (modifier) {
                case 2 -> Key.SHIFT_LEFT;
                case 5 -> Key.CTRL_LEFT;
                case 6 -> Key.CTRL_SHIFT_LEFT;
                default -> Key.LEFT;
            };
            case 'C' -> switch (modifier) {
                case 2 -> Key.SHIFT_RIGHT;
                case 5 -> Key.CTRL_RIGHT;
                case 6 -> Key.CTRL_SHIFT_RIGHT;
                default -> Key.RIGHT;
            };
            case 'H' -> Key.HOME;
            case 'F' -> Key.END;
            case '~' -> switch (value) {
                case "1~", "7~" -> Key.HOME;
                case "3~" -> Key.DELETE;
                case "4~", "8~" -> Key.END;
                default -> Key.UNKNOWN;
            };
            default -> Key.UNKNOWN;
        };
    }

    private enum Key {
        UNKNOWN,
        LEFT, RIGHT,
        CTRL_LEFT, CTRL_RIGHT,
        SHIFT_LEFT, SHIFT_RIGHT,
        CTRL_SHIFT_LEFT, CTRL_SHIFT_RIGHT,
        HOME, END, DELETE
    }

    private static String moveLeft(int count)  { return count <= 0 ? "" : "\033[" + count + "D"; }
    private static String moveRight(int count) { return count <= 0 ? "" : "\033[" + count + "C"; }

    private static String getClipboard() {
        try {
            Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
            Object data = cb.getData(DataFlavor.stringFlavor);
            return data == null ? "" : data.toString();
        } catch (Throwable awtErr) {
            try {
                String os = System.getProperty("os.name", "").toLowerCase();
                Process p;
                if (os.contains("win")) p = Runtime.getRuntime().exec(new String[]{"powershell", "-noprofile", "-command", "Get-Clipboard"});
                else if (os.contains("mac")) p = Runtime.getRuntime().exec("pbpaste");
                else p = Runtime.getRuntime().exec(new String[]{"xclip", "-selection", "clipboard", "-o"});
                return new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (Throwable ignored) { return ""; }
        }
    }

    private static void setClipboard(String text) {
        try {
            Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
            cb.setContents(new StringSelection(text), null);
        } catch (Throwable awtErr) {
            try {
                String os = System.getProperty("os.name", "").toLowerCase();
                Process p;
                if (os.contains("win")) p = Runtime.getRuntime().exec("clip.exe");
                else if (os.contains("mac")) p = Runtime.getRuntime().exec("pbcopy");
                else p = Runtime.getRuntime().exec(new String[]{"xclip", "-selection", "clipboard"});
                p.getOutputStream().write(text.getBytes(StandardCharsets.UTF_8));
                p.getOutputStream().close();
                p.waitFor();
            } catch (Throwable ignored) {}
        }
    }

    public void pop() {
        if (formatStack.isEmpty()) return;
        formatStack.pop();
        StringBuilder builder = new StringBuilder("\033[0m");
        for (IFormat[] formats : formatStack)
            for (IFormat format : formats)
                if (format != null) builder.append(format.ansi());
        System.out.print(builder);
    }

    public void push(IFormat... formats) {
        if (formats.length == 0) return;
        for (IFormat format : formats) if (format != null) System.out.print(format.ansi());
        formatStack.push(formats);
    }

    public void listMarker(String marker)  { listMarker = marker; }
    public String listMarker()             { return listMarker; }
    public void listSeparator(String sep)  { listSeparator = sep; }
    public String listSeparator()          { return listSeparator; }
    public String replacementChars()       { return replacementChars; }
    public void replacementChars(String c) { replacementChars = c; }

    private ConsoleManager() {
        if (System.console() != null) try (Arena arena = Arena.ofConfined()) {
            SymbolLookup kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global());
            Linker linker = Linker.nativeLinker();
            int
                STD_INPUT_HANDLE = -10,
                ENABLE_PROCESSED_INPUT = 0x0001,
                ENABLE_LINE_INPUT = 0x0002,
                ENABLE_ECHO_INPUT = 0x0004,
                ENABLE_VIRTUAL_TERMINAL_INPUT = 0x0200;
            MethodHandle
                GET_STD_HANDLE = linker.downcallHandle(kernel32.find("GetStdHandle").orElseThrow(), FunctionDescriptor.of(JAVA_LONG, JAVA_INT)),
                GET_CONSOLE_MODE = linker.downcallHandle(kernel32.find("GetConsoleMode").orElseThrow(), FunctionDescriptor.of(JAVA_INT, JAVA_LONG, ADDRESS)),
                SET_CONSOLE_MODE = linker.downcallHandle(kernel32.find("SetConsoleMode").orElseThrow(), FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_INT));
            long handle = (long) GET_STD_HANDLE.invoke(STD_INPUT_HANDLE);
            MemorySegment modePtr = arena.allocate(JAVA_INT);
            GET_CONSOLE_MODE.invoke(handle, modePtr);
            int consoleMode = modePtr.get(JAVA_INT, 0);
            SET_CONSOLE_MODE.invoke(handle, (consoleMode & ~ENABLE_PROCESSED_INPUT & ~ENABLE_LINE_INPUT & ~ENABLE_ECHO_INPUT) | ENABLE_VIRTUAL_TERMINAL_INPUT);
        } catch (Throwable e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static final class Markers {
        public static final String
            BULLET = "•",               WHITE_BULLET = "◦",
            BLACK_SMALL_SQUARE = "▪",   WHITE_SMALL_SQUARE = "▫",
            BLACK_SQUARE = "■",         WHITE_SQUARE = "□",
            BLACK_DIAMOND = "◆",        WHITE_DIAMOND = "◇",
            TRIANGULAR_BULLET = "‣",
            RIGHT_ARROW = "→",          DOUBLE_RIGHT_ARROW = "⇒",   LEFT_RIGHT_ARROW = "↔",
            EM_DASH = "—",              EN_DASH = "–",              MIDDLE_DOT = "·",
            DEGREE = "°",               PILCROW = "¶",              SECTION = "§";
        private Markers() {}
    }

    public static final class Patterns {
        public static final Pattern
            LATIN = Pattern.compile("^[a-zA-Z ]+$"),
            CYRILLIC = Pattern.compile("^[а-яА-ЯёЁ ]+$"),
            INTEGER = Pattern.compile("^\\d+$"),
            DECIMAL = Pattern.compile("^[\\d.,]+$"),
            MATH = Pattern.compile("^[+\\-=*^%/(){}\\[\\]]+$"),
            SPEC = Pattern.compile("^[!?@\"'#№$;<>,.]+$"),
            EMAIL = Pattern.compile("^[a-zA-Z@.]+$");
        public static Pattern combine(Pattern... patterns) {
            StringBuilder regex = new StringBuilder("^[");
            for (Pattern pattern : patterns) regex.append(pattern);
            regex.append("]+$");
            return Pattern.compile(regex.toString());
        }
        private Patterns() {}
    }

    public static ConsoleManager getInstance() { return instance; }

    private static final Pattern ANSI_CODES = Pattern.compile(
        "\u001B\\[[0-?]*[ -/]*[@-~]|[\n\r\t\b\f]"
    );
    private static String excludeANSI(String str) {
        return ANSI_CODES.matcher(str).replaceAll("");
    }

    private static final ConsoleManager instance = new ConsoleManager();
}