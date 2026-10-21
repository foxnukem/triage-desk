package triage.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KbArticleRepository extends JpaRepository<KbArticleEntity, String> {

    List<KbArticleEntity> findAllByOrderByIdAsc();
}
