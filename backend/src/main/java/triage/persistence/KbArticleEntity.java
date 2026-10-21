package triage.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "kb_article")
public class KbArticleEntity {

    @Id
    private String id;
    private String title;
    private String bodyMd;

    protected KbArticleEntity() {}

    public KbArticleEntity(String id, String title, String bodyMd) {
        this.id = id;
        this.title = title;
        this.bodyMd = bodyMd;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getBodyMd() { return bodyMd; }
}
