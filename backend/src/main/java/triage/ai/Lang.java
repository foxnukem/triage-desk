package triage.ai;

public enum Lang {
    UK("Ukrainian"), EN("English"), OTHER("the customer's language");

    private final String displayName;

    Lang(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
