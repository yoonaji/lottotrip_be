package com.lottotrip.place.entity;

import com.lottotrip.place.repository.PlaceRepository;
import com.lottotrip.support.PostgresContainerSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code places.category}의 CHECK 제약이 enum과 어긋나지 않는지 확인한다. (roadmap 6-15, 결정 16)
 *
 * <h2>이 테스트의 한계를 먼저 밝힌다</h2>
 * <b>2026-08-15에 겪은 사고를 이 테스트가 재현하지는 못한다.</b> 테스트용 DB는 매번 새로 만들어져
 * 제약이 <b>항상 최신 enum으로</b> 생성되기 때문이다. 사고는 <b>오래 살아 있는 DB</b>에서만 난다 —
 * 테이블은 옛 enum으로 만들어졌는데 코드만 새 값을 갖게 되는 상황이다.
 *
 * <p>그래도 남겨 두는 이유는 <b>Hibernate가 이 제약을 만든다는 사실을 코드에 박아 두기 위해서</b>다.
 * 제약이 있다는 걸 모르면, enum에 값을 더할 때 DB도 함께 손봐야 한다는 생각을 못 한다.
 *
 * <p><b>근본 해결은 마이그레이션 도구(Flyway)다.</b> 스키마 변경을 코드와 함께 버전 관리해야
 * "코드는 새 값을 쓰는데 DB는 옛 제약을 들고 있는" 상태가 생기지 않는다. 배포 전에 정한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PlaceCategoryConstraintTest extends PostgresContainerSupport {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlaceRepository placeRepository;

    @Test
    @DisplayName("CHECK 제약이 있다면 지금 enum의 모든 값을 허용해야 한다")
    void checkConstraintCoversEveryEnumValue() {
        @SuppressWarnings("unchecked")
        List<String> definitions = entityManager.createNativeQuery("""
                        select pg_get_constraintdef(oid)
                        from pg_constraint
                        where contype = 'c' and conrelid = 'places'::regclass
                        """)
                .getResultList();

        String categoryCheck = definitions.stream()
                .filter(definition -> definition.contains("category"))
                .findFirst()
                .orElse(null);

        if (categoryCheck == null) {
            return; // 제약이 없으면 어긋날 일도 없다 — 결정 16이 바라던 상태다
        }

        assertThat(TravelCategory.values()).allSatisfy(category ->
                assertThat(categoryCheck)
                        .withFailMessage("CHECK 제약이 %s를 허용하지 않는다. "
                                + "이 값을 저장하는 순간 실패한다: %s", category, categoryCheck)
                        .contains("'" + category.name() + "'"));
    }

    @Test
    @DisplayName("모든 분류를 실제로 저장할 수 있다")
    void savesEveryCategory() {
        // 제약이든 컬럼 길이든, 막는 것이 있으면 여기서 드러난다.
        // 6-10에서 LODGING이 막혔던 그 지점을 값 전체로 넓힌 것이다.
        assertThat(TravelCategory.values()).allSatisfy(category -> {
            Place saved = placeRepository.save(Place.builder()
                    .contentId("ct-" + category.ordinal())
                    .contentTypeId("12")
                    .name(category.getDisplayName())
                    .category(category)
                    .latitude(37.8021)
                    .longitude(128.8954)
                    .build());

            assertThat(saved.getCategory()).isEqualTo(category);
        });
    }
}
