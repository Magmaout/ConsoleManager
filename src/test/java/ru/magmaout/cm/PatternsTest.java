package ru.magmaout.cm;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class PatternsTest {
    @Test
    void latinPatternTest() {
        assertTrue(ConsoleManager.Patterns.LATIN.matcher("Hello").matches());
        assertFalse(ConsoleManager.Patterns.LATIN.matcher("Привет").matches());
    }

    @Test
    void cyrillicPatternTest() {
        assertTrue(ConsoleManager.Patterns.CYRILLIC.matcher("Привет").matches());
        assertFalse(ConsoleManager.Patterns.CYRILLIC.matcher("Hello").matches());
    }

    @Test
    void integerPatternTest() {
        assertTrue(ConsoleManager.Patterns.INTEGER.matcher("123").matches());
        assertFalse(ConsoleManager.Patterns.INTEGER.matcher("12.3").matches());
    }

    @Test
    void decimalPatternTest() {
        assertTrue(ConsoleManager.Patterns.DECIMAL.matcher("12.3").matches());
    }

    @Test
    void emailPatternTest() {
        assertTrue(ConsoleManager.Patterns.EMAIL.matcher("test@mail.com").matches());
    }

    @Test
    void combineTest() {
        Pattern p = ConsoleManager.Patterns.combine(
            ConsoleManager.Patterns.LATIN,
            ConsoleManager.Patterns.INTEGER
        );
        assertNotNull(p);
    }
}