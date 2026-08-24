package io.avec.knowledgebase.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ArticleRepository extends JpaRepository<Article, Long> {

    List<Article> findAllByOrderByUpdatedAtDesc();

    List<Article> findByStatusOrderByUpdatedAtDesc(ArticleStatus status);

    List<Article> findByCategoryOrderBySortOrder(Category category);

    List<Article> findByCategoryAndStatusOrderBySortOrder(Category category, ArticleStatus status);

    List<Article> findByCategoryIsNullOrderBySortOrder();

    List<Article> findByCategoryIsNullAndStatusOrderBySortOrder(ArticleStatus status);

    Optional<Article> findBySlug(String slug);

    Optional<Article> findBySlugAndStatus(String slug, ArticleStatus status);

    boolean existsBySlug(String slug);

    boolean existsByCategory(Category category);

    @Query("SELECT a FROM Article a WHERE " +
           "LOWER(a.title) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(a.content) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "ORDER BY a.updatedAt DESC")
    List<Article> searchByTitleOrContent(@Param("query") String query);

    @Query("SELECT a FROM Article a WHERE a.status = :status AND (" +
           "LOWER(a.title) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(a.content) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "ORDER BY a.updatedAt DESC")
    List<Article> searchByTitleOrContentAndStatus(@Param("query") String query,
                                                   @Param("status") ArticleStatus status);
}
