package com.lottotrip.support;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * enum 매핑 규약 테스트. (roadmap 3-6)
 *
 * enum을 DB에 **이름 문자열(VARCHAR)**로 저장하기로 했다. 이 테스트는 그 약속이
 * 앞으로도 지켜지는지 자동으로 검사한다. 특정 Entity를 검사하는 게 아니라
 * `com.lottotrip` 아래의 **모든 Entity를 훑는다.** 새 Entity를 추가해도 자동으로 포함된다.
 *
 * DB가 필요 없는 검사이므로 컨테이너를 띄우지 않는다.
 */
class EntityEnumMappingTest {

    /** `@Column(length)`를 생략했을 때 JPA가 쓰는 기본 길이. */
    private static final int DEFAULT_COLUMN_LENGTH = 255;

    private static List<Class<?>> entityClasses() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));

        Set<BeanDefinition> found = scanner.findCandidateComponents("com.lottotrip");
        return found.stream()
                .<Class<?>>map(definition -> {
                    try {
                        return Class.forName(definition.getBeanClassName());
                    } catch (ClassNotFoundException e) {
                        throw new IllegalStateException(e);
                    }
                })
                .toList();
    }

    private static List<Field> enumFields() {
        return entityClasses().stream()
                .flatMap(entity -> Arrays.stream(entity.getDeclaredFields()))
                .filter(field -> field.getType().isEnum())
                .toList();
    }

    @Test
    @DisplayName("Entity를 하나도 못 찾으면 이 테스트 자체가 무의미하다")
    void findsEntities() {
        // 스캔이 조용히 실패하면 아래 검사들이 "검사할 게 없어서" 통과해 버린다.
        assertThat(entityClasses()).isNotEmpty();
        assertThat(enumFields()).isNotEmpty();
    }

    @Test
    @DisplayName("모든 Entity의 enum 필드는 @Enumerated(STRING)으로 저장한다")
    void everyEnumFieldIsMappedAsString() {
        // @Enumerated를 빠뜨리면 기본값이 ORDINAL(순서 숫자)이다. 그 상태로 운영에 나가면,
        // 나중에 enum 중간에 값을 하나 끼워 넣는 순간 이미 저장된 데이터의 의미가 통째로 뒤바뀐다.
        // 예: COMPLETED가 1로 저장돼 있었는데, 앞에 값이 추가되면 1이 다른 상태를 가리키게 된다.
        assertThat(enumFields()).allSatisfy(field -> {
            Enumerated enumerated = field.getAnnotation(Enumerated.class);

            assertThat(enumerated)
                    .withFailMessage("%s.%s 에 @Enumerated가 없다. 기본값 ORDINAL로 저장되어 위험하다.",
                            field.getDeclaringClass().getSimpleName(), field.getName())
                    .isNotNull();

            assertThat(enumerated.value())
                    .withFailMessage("%s.%s 가 ORDINAL로 매핑돼 있다. STRING으로 바꿔야 한다.",
                            field.getDeclaringClass().getSimpleName(), field.getName())
                    .isEqualTo(EnumType.STRING);
        });
    }

    @Test
    @DisplayName("enum 컬럼 길이는 가장 긴 상수 이름을 담을 수 있어야 한다")
    void everyEnumColumnIsLongEnough() {
        // 컬럼이 짧으면 짧은 값은 저장되고 긴 값만 실패한다. 특정 값을 쓸 때만 터져서 발견이 늦다.
        assertThat(enumFields()).allSatisfy(field -> {
            int longestName = Arrays.stream(field.getType().getEnumConstants())
                    .map(constant -> ((Enum<?>) constant).name().length())
                    .max(Integer::compareTo)
                    .orElse(0);

            Column column = field.getAnnotation(Column.class);
            int columnLength = (column == null) ? DEFAULT_COLUMN_LENGTH : column.length();

            assertThat(columnLength)
                    .withFailMessage("%s.%s 컬럼 길이가 %d인데 가장 긴 상수 이름은 %d자다.",
                            field.getDeclaringClass().getSimpleName(), field.getName(),
                            columnLength, longestName)
                    .isGreaterThanOrEqualTo(longestName);
        });
    }
}
