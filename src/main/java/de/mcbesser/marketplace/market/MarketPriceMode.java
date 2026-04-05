package de.mcbesser.marketplace.market;

public enum MarketPriceMode {
    TOTAL("Gesamtpreis"),
    STACK("Stackpreis"),
    SINGLE("Einzelpreis");

    private final String label;

    MarketPriceMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public MarketPriceMode next() {
        MarketPriceMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
