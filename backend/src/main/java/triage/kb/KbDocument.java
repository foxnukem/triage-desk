package triage.kb;

public record KbDocument(String id, String title, String body) {

    /** Body without the heading and the retrieval-only "Keywords (UA):" line. */
    public String plainBody() {
        StringBuilder sb = new StringBuilder();
        for (String line : body.split("\n")) {
            if (line.startsWith("# ") || line.startsWith("Keywords (UA):")) {
                continue;
            }
            sb.append(line).append('\n');
        }
        return sb.toString().strip();
    }
}
