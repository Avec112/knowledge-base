package io.avec.knowledgebase.data;

import static org.assertj.core.api.Assertions.assertThat;

import io.avec.data.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest(showSql = false)
class DefaultDataInitializationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ArticleRepository articleRepository;

    @Test
    void defaultProfileDoesNotSeedApplicationData() {
        assertThat(userRepository.count()).isZero();
        assertThat(categoryRepository.count()).isZero();
        assertThat(articleRepository.count()).isZero();
    }
}
