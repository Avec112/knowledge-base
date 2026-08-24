package io.avec.knowledgebase.data;

import static org.assertj.core.api.Assertions.assertThat;

import io.avec.data.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest(showSql = false)
@ActiveProfiles("demo")
class DemoDataInitializationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ArticleRepository articleRepository;

    @Test
    void demoProfileSeedsSampleData() {
        assertThat(userRepository.findByUsername("user")).isPresent();
        assertThat(userRepository.findByUsername("admin")).isPresent();
        assertThat(categoryRepository.count()).isEqualTo(5);
        assertThat(articleRepository.count()).isEqualTo(8);
    }
}
