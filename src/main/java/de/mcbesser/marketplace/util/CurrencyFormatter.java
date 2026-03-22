package de.mcbesser.marketplace.util;

public final class CurrencyFormatter {

    private CurrencyFormatter() {
    }

    public static String shortAmount(double amount) {
        return (int) amount + " CT";
    }

    public static String longAmount(double amount) {
        return (int) amount + " CraftTaler";
    }
}
