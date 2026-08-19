package com.lottotrip.place.tourapi;

import com.lottotrip.place.entity.TravelCategory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * TourAPI 중분류(`cat2`)를 우리 {@link TravelCategory}로 옮긴다. (roadmap 6-15, 결정 16)
 *
 * TourApi에서 제공하는 `cat2` 기준으로 래핑함
 */
@Component
public class TravelCategoryMapper {

    /**
     * `cat2` 코드로 바로 찾을 수 있게 만들어 둔 표.
     *
     * 매번 `values()`를 훑어도 24개라 느리지 않지만, 표를 한 번만 만들어 두면
     * 조회가 상수 시간이고 "코드가 겹치면 안 된다"는 성질도 여기서 드러난다
     */
    private static final Map<String, TravelCategory> BY_CAT2 = Arrays.stream(TravelCategory.values())
            .filter(category -> !category.getCat2Code().isEmpty())
            .collect(Collectors.toUnmodifiableMap(
                    TravelCategory::getCat2Code, Function.identity()));

    /**
     * `cat2`를 우리 분류로 바꾼다.
     *
     * 모르는 값이나 빈 값은 {@link TravelCategory#UNKNOWN}이다.
     *
     * @param cat2 TourAPI 목록 응답의 `cat2`. null이어도 된다
     * @return 절대 null이 아니다
     */
    public TravelCategory map(String cat2) {
        if (cat2 == null || cat2.isBlank()) {
            return TravelCategory.UNKNOWN;
        }
        // 대소문자·공백 차이로 매칭이 어긋나지 않게 맞춘다.
        return BY_CAT2.getOrDefault(cat2.trim().toUpperCase(), TravelCategory.UNKNOWN);
    }
}
