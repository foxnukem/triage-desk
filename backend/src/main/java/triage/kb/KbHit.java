package triage.kb;

public record KbHit(String articleId, String title, double score, String text) {}
