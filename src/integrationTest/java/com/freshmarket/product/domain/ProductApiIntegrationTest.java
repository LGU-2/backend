package com.freshmarket.product.domain;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.transaction.annotation.Transactional;
import com.freshmarket.product.domain.dto.PageTokens;
import com.freshmarket.product.domain.entity.Product;
import com.freshmarket.product.domain.entity.ProductOption;
import com.freshmarket.product.domain.entity.StorageType;
import com.freshmarket.product.domain.repository.CategoryRepository;
import com.freshmarket.product.domain.repository.ProductOptionRepository;
import com.freshmarket.product.domain.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/*
 * 상품 목록 조회를 HTTP 요청부터 DB 까지 전체 경로로 검증한다.
 * SecurityConfig, ProductController, ProductService, ProductQueryRepository 를
 * 실제 스프링 컨텍스트로 띄워 하나로 묶어서 확인한다 (팀 방침: Repository 단위 테스트 대신
 * @SpringBootTest + @AutoConfigureMockMvc 로 작성).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
/*
 * Supplier 엔티티가 아직 없어 JPA 로 공급처를 만들 수 없다.
 * product.supplier_id 가 NOT NULL FK 라 테스트 상품을 만들려면 공급처가 먼저 있어야 하므로
 * SQL 스크립트로 직접 하나 심는다. Supplier 엔티티가 생기면 이 스크립트 대신 정식으로 등록한다.
 */
@Sql("/sql/product-test-supplier.sql")
@Testcontainers
class ProductApiIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductOptionRepository productOptionRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @PersistenceContext
    private EntityManager entityManager;

    // product-test-supplier.sql 이 심어둔 공급처를 그대로 쓴다.
    // auto_increment 드리프트에 흔들리지 않도록 SQL 에서 supplier_id 를 명시적으로 고정했다.
    private static final Long SUPPLIER_ID = 999999L;

    // V2__seed_category.sql 이 심어둔 최상위 카테고리 중 하나를 그대로 쓴다
    private Long fruitCategoryId() {
        return categoryRepository.findAll().stream()
                .filter(c -> c.getName().equals("과일"))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    // 상품 하나와 옵션들을 저장하고 id 를 돌려준다
    private Long saveProductWithOptions(Long categoryId, String name, int... prices) {
        Product product = productRepository.save(
                Product.register("P-" + name, name, categoryId, SUPPLIER_ID,
                        StorageType.COLD, 3));
        for (int price : prices) {
            productOptionRepository.save(
                    ProductOption.register(product.getId(), price + "원대옵션", price));
        }
        return product.getId();
    }

    /*
     * Product 에 소프트딜리트 도메인 메서드가 아직 없다.
     * product_option 이 product 를 FK 로 참조해 하드 삭제는 제약 위반을 일으키고,
     * chk_product_deleted 제약도 deleted_at 이 채워지는 방식을 전제한다.
     * 그래서 하드 삭제 대신 상태만 직접 갱신해 "삭제된 상품"을 재현한다.
     * Product.delete() 같은 도메인 메서드가 생기면 이 메서드 대신 그것을 호출해야 한다.
     */
    private void softDelete(Long productId) {
        entityManager.flush();
        entityManager.createNativeQuery(
                        "UPDATE product SET deleted_at = NOW(6), sale_status = 'OFF_SALE' "
                                + "WHERE product_id = :id")
                .setParameter("id", productId)
                .executeUpdate();
        entityManager.clear();
    }

    @Test
    void 비로그인_상태에서도_상품_목록을_조회할_수_있다() throws Exception {
        // given
        Long categoryId = fruitCategoryId();
        saveProductWithOptions(categoryId, "감귤", 12900);

        // when, then — 인증 헤더 없이 요청해도 401 이 아니라 200 이어야 한다
        mockMvc.perform(get("/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products").isArray());
    }

    @Test
    void 카테고리로_필터링해_응답한다() throws Exception {
        // given
        Long categoryId = fruitCategoryId();
        saveProductWithOptions(categoryId, "감귤", 12900);

        // when, then
        mockMvc.perform(get("/v1/products").param("categoryId", String.valueOf(categoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products", hasSize(1)))
                .andExpect(jsonPath("$.products[0].name").value("감귤"))
                .andExpect(jsonPath("$.products[0].minPrice").value(12900));
    }

    @Test
    void 삭제된_상품은_응답에서_제외된다() throws Exception {
        // given
        Long categoryId = fruitCategoryId();
        Long visibleId = saveProductWithOptions(categoryId, "감귤", 12900);
        Long deletedId = saveProductWithOptions(categoryId, "복숭아", 9900);
        softDelete(deletedId);

        // when, then
        mockMvc.perform(get("/v1/products").param("categoryId", String.valueOf(categoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[*].productId")
                        .value(org.hamcrest.Matchers.hasItem(visibleId.intValue())))
                .andExpect(jsonPath("$.products[*].productId")
                        .value(org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.hasItem(deletedId.intValue()))));
    }

    @Test
    void 여러_옵션_중_최저가가_응답에_내려간다() throws Exception {
        // given
        Long categoryId = fruitCategoryId();
        saveProductWithOptions(categoryId, "감귤세트", 32000, 12900, 48000);

        // when, then
        mockMvc.perform(get("/v1/products").param("categoryId", String.valueOf(categoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[0].minPrice").value(12900));
    }

    @Test
    void 가격_범위_안의_옵션이_있으면_상품이_노출된다() throws Exception {
        // given — 감귤은 1kg=12900원(범위 밖) 이지만 5kg=48000원(범위 안) 옵션도 갖는다
        Long categoryId = fruitCategoryId();
        saveProductWithOptions(categoryId, "감귤", 12900, 48000);

        // when, then — 40000~60000 범위. where 필터라 48000원 옵션 때문에 노출된다
        mockMvc.perform(get("/v1/products")
                        .param("categoryId", String.valueOf(categoryId))
                        .param("minPrice", "40000")
                        .param("maxPrice", "60000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products", hasSize(1)))
                .andExpect(jsonPath("$.products[0].name").value("감귤"))
                /*
                 * where 로 옵션을 먼저 거르므로, 응답 minPrice 는 상품의 절대 최저가(12900)가
                 * 아니라 조건을 만족하는 옵션 중 최저가(48000)다. 의도된 동작이다.
                 */
                .andExpect(jsonPath("$.products[0].minPrice").value(48000));
    }

    @Test
    void 범위_안의_옵션이_전혀_없으면_상품이_제외된다() throws Exception {
        // given
        Long categoryId = fruitCategoryId();
        saveProductWithOptions(categoryId, "저가상품", 5000);

        // when, then — 5000원은 40000~60000 범위 밖. 어떤 옵션도 안 걸리므로 제외된다
        mockMvc.perform(get("/v1/products")
                        .param("categoryId", String.valueOf(categoryId))
                        .param("minPrice", "40000")
                        .param("maxPrice", "60000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products", hasSize(0)));
    }

    @Test
    void 가격_오름차순으로_정렬한다() throws Exception {
        // given
        Long categoryId = fruitCategoryId();
        saveProductWithOptions(categoryId, "가격A", 5000);
        saveProductWithOptions(categoryId, "가격B", 50000);

        // when, then
        mockMvc.perform(get("/v1/products")
                        .param("categoryId", String.valueOf(categoryId))
                        .param("sort", "PRICE_ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[0].name").value("가격A"))
                .andExpect(jsonPath("$.products[1].name").value("가격B"));
    }

    @Test
    void 커서_이후의_상품만_응답한다() throws Exception {
        // given
        Long categoryId = fruitCategoryId();
        Long first = saveProductWithOptions(categoryId, "커서첫상품", 1000);
        Long second = saveProductWithOptions(categoryId, "커서둘째상품", 2000);

        // when, then — second 를 커서로 주면 그보다 먼저 만들어진 first 만 남는다
        mockMvc.perform(get("/v1/products")
                        .param("categoryId", String.valueOf(categoryId))
                        .param("pageToken", PageTokens.encode(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products[*].productId")
                        .value(org.hamcrest.Matchers.hasItem(first.intValue())))
                .andExpect(jsonPath("$.products[*].productId")
                        .value(org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.hasItem(second.intValue()))));
    }

    @Test
    void 페이지_크기만큼만_응답하고_다음_페이지_토큰을_준다() throws Exception {
        // given
        Long categoryId = fruitCategoryId();
        for (int i = 0; i < 3; i++) {
            saveProductWithOptions(categoryId, "페이지상품" + i, 1000 + i);
        }

        // when, then
        mockMvc.perform(get("/v1/products")
                        .param("categoryId", String.valueOf(categoryId))
                        .param("pageSize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products", hasSize(2)))
                .andExpect(jsonPath("$.nextPageToken").exists());
    }

    @Test
    void 잘못된_정렬값이_오면_400을_응답한다() throws Exception {
        // when, then — 화이트리스트에 없는 sort 값. enum 변환 실패가 COMMON-003 으로 처리된다
        mockMvc.perform(get("/v1/products").param("sort", "INVALID_SORT"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 가격_범위가_뒤집히면_400을_응답한다() throws Exception {
        // when, then — minPrice > maxPrice. ProductSearchCondition 생성자 검증이 예외를 던진다
        mockMvc.perform(get("/v1/products")
                        .param("minPrice", "50000")
                        .param("maxPrice", "10000"))
                .andExpect(status().isBadRequest());
    }
}