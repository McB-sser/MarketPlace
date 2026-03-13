package de.mcbesser.marketplace.pricing;

public class PriceGuideEntry {

    private final String key;
    private final String displayName;
    private double referencePrice;
    private int samples;

    public PriceGuideEntry(String key, String displayName, double referencePrice, int samples) {
        this.key = key;
        this.displayName = displayName;
        this.referencePrice = referencePrice;
        this.samples = samples;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getReferencePrice() {
        return referencePrice;
    }

    public int getSamples() {
        return samples;
    }

    public void applyObservation(double observedPrice) {
        double weighted = (referencePrice * samples + observedPrice) / (samples + 1);
        referencePrice = Math.max(1, Math.round(weighted));
        samples = Math.min(50, samples + 1);
    }
}


