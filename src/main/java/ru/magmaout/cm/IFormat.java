package ru.magmaout.cm;

public interface IFormat {
    IFormat
        BOLD = () -> "\033[1m",
        DISABLE = () -> "\033[2m",
        ITALIC = () -> "\033[3m",
        UNDERLINE = () -> "\033[4m",
        BLINK = () -> "\033[5m",
        DOUBLEUNDERLINE = () -> "\033[21m",
        REVERSE = () -> "\033[7m",
        STRIKETHROUGH = () -> "\033[9m";

    static IFormat TextColor(java.awt.Color c) {
        return () -> String.format("\033[48;2;%d;%d;%dm", c.getRed(), c.getGreen(), c.getBlue());
    }

    static IFormat BackgroundColor(java.awt.Color c) {
        return () -> String.format("\033[38;2;%d;%d;%dm", c.getRed(), c.getGreen(), c.getBlue());
    }

    String ansi();
}