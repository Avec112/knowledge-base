package io.avec.knowledgebase.data;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByParentIsNullOrderBySortOrder();

    List<Category> findByParentOrderBySortOrder(Category parent);

    Optional<Category> findBySlug(String slug);
    Optional<Category> findByNameIgnoreCase(String name);

    boolean existsBySlug(String slug);

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.children WHERE c.parent IS NULL ORDER BY c.sortOrder")
    List<Category> findRootCategoriesWithChildren();

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.articles WHERE c.id = :id")
    Optional<Category> findByIdWithArticles(Long id);
}
