package com.lottotrip.place.tourapi;

import com.lottotrip.place.entity.TravelCategory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * TourAPI 중분류({@code cat2})를 우리 {@link TravelCategory}로 옮긴다. (roadmap 6-15, 결정 16)
 *
 * <p><b>⚠️ 6-15에서 기준이 통째로 바뀌었다.</b> 예전에는 관광타입({@code contenttypeid})과
 * {@code cat1}~{@code cat3}를 섞어 우리가 정의한 11종으로 나눴다. 지금은 <b>{@code cat2} 하나만</b> 본다.
 *
 * <p><b>그래서 이 클래스가 아주 얇아졌다.</b> {@link TravelCategory}가 이미 TourAPI 체계와 1:1이라
 * 여기서 할 일은 <b>코드로 찾아 주는 것뿐</b>이다. 분류 규칙이 enum 정의 자체에 담겨 있으므로
 * "어떤 규칙으로 나뉘는가"를 알고 싶으면 enum을 보면 된다.
 *
 * <p><b>그래도 클래스를 없애지 않은 이유:</b> 모르는 값·빈 값을 어떻게 다룰지가 <b>정책</b>이고,
 * 나중에 {@code cat3}(해수욕장·카페)를 되살리려면 그 규칙이 들어올 자리가 여기다.
 */
@Component
public class TravelCategoryMapper {

    /**
     * {@code cat2} 코드로 바로 찾을 수 있게 만들어 둔 표.
     *
     * <p>매번 {@code values()}를 훑어도 24개라 느리지 않지만, <b>표를 한 번만 만들어 두면</b>
     * 조회가 상수 시간이고 "코드가 겹치면 안 된다"는 성질도 여기서 드러난다
     * (같은 코드가 둘이면 {@code toMap}이 예외를 던져 <b>기동할 때</b> 알게 된다).
     */
    private static final Map<String, TravelCategory> BY_CAT2 = Arrays.stream(TravelCategory.values())
            .filter(category -> !category.getCat2Code().isEmpty())
            .collect(Collectors.toUnmodifiableMap(
                    TravelCategory::getCat2Code, Function.identity()));

    /**
     * {@code cat2}를 우리 분류로 바꾼다.
     *
     * <p>모르는 값이나 빈 값은 {@link TravelCategory#UNKNOWN}이다. <b>예외를 던지지 않는 이유:</b>
     * {@code places.category}가 NOT NULL이라 여기서 터지면 <b>장소 하나 때문에 슬롯 전체가 실패</b>한다.
     * 분류를 모르는 것은 뽑기를 실패시킬 만한 일이 아니다.
     *
     * @param cat2 TourAPI 목록 응답의 {@code cat2}. null이어도 된다
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
