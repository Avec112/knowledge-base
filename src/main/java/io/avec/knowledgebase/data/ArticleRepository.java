package io.avec.knowledgebase.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ArticleRepository extends JpaRepository<Article, Long> {

    List<Article> findAllByOrderByUpdatedAtDesc();

    List<Article> findByPublishedTrueOrderByUpdatedAtDesc();

    Optional<Article> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
