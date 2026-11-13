package ro.saga.transfer.common;

public enum Bank {
    ALPHA("Alpha Bank"),
    BETA("Beta Bank");

    private final String displayName;

    Bank(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
