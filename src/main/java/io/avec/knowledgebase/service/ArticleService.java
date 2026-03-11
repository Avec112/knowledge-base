package io.avec.knowledgebase.service;

import io.avec.knowledgebase.data.Article;
import io.avec.knowledgebase.data.ArticleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ArticleService {

    private final ArticleRepository articleRepository;

    public ArticleService(ArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
    }

    public List<Article> findAll() {
        return articleRepository.findAllByOrderByUpdatedAtDesc();
    }

    public List<Article> findPublished() {
        return articleRepository.findByPublishedTrueOrderByUpdatedAtDesc();
    }

    public Optional<Article> findById(Long id) {
        return articleRepository.findById(id);
    }

    @Transactional
    public Article save(Article article) {
        return articleRepository.save(article);
    }

    @Transactional
    public void delete(Article article) {
        articleRepository.delete(article);
    }
}
