package com.freshmarket.product.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.freshmarket.product.domain.entity.Category;
import com.freshmarket.product.domain.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class CategoryRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    void 카테고리를_저장하고_조회한다() {
        Category saved = categoryRepository.save(Category.register("수산물"));

        assertThat(categoryRepository.findById(saved.getId())).isPresent();
    }

    @Test
    void 최상위_카테고리끼리_이름이_같으면_중복으로_잡힌다() {
        categoryRepository.save(Category.register("육류"));

        boolean exists = categoryRepository.existsByParentIdIsNullAndName("육류");

        assertThat(exists).isTrue();
    }

    @Test
    void 최상위_카테고리끼리_이름이_다르면_중복이_아니다() {
        categoryRepository.save(Category.register("육류"));

        boolean exists = categoryRepository.existsByParentIdIsNullAndName("채소");

        assertThat(exists).isFalse();
    }

    @Test
    void 같은_상위_카테고리_아래에서_이름_중복을_확인한다() {
        Category parent = categoryRepository.save(Category.register("수산물"));
        categoryRepository.save(Category.register("손질생선", parent.getId()));

        boolean exists = categoryRepository.existsByParentIdAndName(parent.getId(), "손질생선");

        assertThat(exists).isTrue();
    }

    @Test
    void 이름_유니크_제약을_DB가_실제로_강제한다() {
        categoryRepository.saveAndFlush(Category.register("과일"));

        assertThatThrownBy(() -> categoryRepository.saveAndFlush(Category.register("과일")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 존재하지_않는_상위_카테고리를_참조하면_DB가_거부한다() {
        Category invalid = Category.register("불량", 999999L);

        assertThatThrownBy(() -> categoryRepository.saveAndFlush(invalid))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}