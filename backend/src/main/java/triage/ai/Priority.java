package triage.ai;

/** P1 is the most urgent. */
public enum Priority {
    P1, P2, P3, P4;

    public boolean isLowerThan(Priority other) {
        return ordinal() > other.ordinal();
    }

    public int distance(Priority other) {
        return Math.abs(ordinal() - other.ordinal());
    }
}
