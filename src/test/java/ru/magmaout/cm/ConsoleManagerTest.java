package ru.magmaout.cm;

import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

class ConsoleManagerTest {
    @Test
    void singletonTest() {
        assertSame(
            ConsoleManager.getInstance(),
            ConsoleManager.getInstance()
        );
    }

    @Test
    void listMarkerTest() {
        ConsoleManager cm = ConsoleManager.getInstance();

        cm.listMarker(">");
        assertEquals(">", cm.listMarker());
    }

    @Test
    void listSeparatorTest() {
        ConsoleManager cm = ConsoleManager.getInstance();

        cm.listSeparator(";");
        assertEquals(";", cm.listSeparator());
    }

    @Test
    void replacementCharsTest() {
        ConsoleManager cm = ConsoleManager.getInstance();

        cm.replacementChars("[]");
        assertEquals("[]", cm.replacementChars());
    }

    @Test
    void textColorTest() {
        String ansi = IFormat.TextColor(Color.RED).ansi();

        assertNotNull(ansi);
        assertTrue(ansi.contains("255"));
    }

    @Test
    void backgroundColorTest() {
        String ansi = IFormat.BackgroundColor(Color.BLUE).ansi();

        assertNotNull(ansi);
        assertTrue(ansi.contains("255"));
    }

    @Test
    void boldFormatTest() {
        assertEquals("\033[1m", IFormat.BOLD.ansi());
    }

    @Test
    void italicFormatTest() {
        assertEquals("\033[3m", IFormat.ITALIC.ansi());
    }

    @Test
    void underlineFormatTest() {
        assertEquals("\033[4m", IFormat.UNDERLINE.ansi());
    }
}