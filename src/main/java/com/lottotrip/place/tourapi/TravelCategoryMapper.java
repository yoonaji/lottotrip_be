package com.lottotrip.place.tourapi;

import com.lottotrip.place.entity.TravelCategory;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * TourAPI 분류 코드를 우리 {@link TravelCategory}로 옮긴다. (roadmap 5-9)
 *
 * <p><b>왜 따로 두는가.</b> TourAPI의 관광타입은 6종인데 우리 분류는 8종이라 1:1이 아니다.
 * 특히 <b>관광타입 12(관광지) 하나에 자연·해변·역사가 전부 들어 있어</b> 관광타입만으로는 못 나눈다.
 * 이 규칙을 적재 코드 안에 섞어 두면 "무엇을 담을까"와 "어떻게 분류할까"가 엉켜서 나중에 못 고친다.
 *
 * <h2>TourAPI 분류 체계</h2>
 * 코드가 세 겹으로 좁혀진다. {@code cat1}(대분류) → {@code cat2}(중분류) → {@code cat3}(소분류).
 * 앞자리를 공유하므로 {@code A01} > {@code A0101} > {@code A01011200} 순으로 포함 관계다.
 * <ul>
 *   <li>{@code A01} 자연 · {@code A02} 인문(문화·역사) · {@code A03} 레포츠</li>
 *   <li>{@code A04} 쇼핑 · {@code A05} 음식</li>
 * </ul>
 *
 * <p>⚠️ <b>세부 코드는 실물 데이터로 확인이 필요하다.</b> 아래 {@code CAT3_*}·{@code CAT2_*} 상수는
 * 문서 기준이며, 5-9 적재를 실제로 돌려 본 뒤 어긋나면 <b>이 상수들만</b> 고치면 된다.
 * 틀려도 {@link #DEFAULT_CATEGORY}로 떨어질 뿐 적재가 멈추지는 않는다.
 */
@Component
public class TravelCategoryMapper {

    // ---------- 관광타입 (contenttypeid) ----------

    private static final String TYPE_TOURIST_SPOT = "12";
    private static final String TYPE_CULTURAL_FACILITY = "14";
    private static final String TYPE_FESTIVAL = "15";
    private static final String TYPE_COURSE = "25";
    private static final String TYPE_LEISURE = "28";
    private static final String TYPE_LODGING = "32";
    private static final String TYPE_SHOPPING = "38";
    private static final String TYPE_RESTAURANT = "39";

    /**
     * 배치 시절의 적재 대상 필터. <b>⛔ 결정 12·13으로 역할이 끝났다.</b>
     *
     * <p>온디맨드에서는 종류 제한을 <b>요청의 {@code contentTypeId}가 API 단계에서</b> 한다.
     * 우리가 받아서 거를 일이 없다.
     *
     * <p><b>지금 지우지 않는 이유:</b> 아직 {@code PlaceSeeder}가 쓰고 있다.
     * 그 클래스를 제거하는 6-14에서 이 상수와 {@link #isTargetContentType}도 함께 지운다.
     * 먼저 지우면 6-10 단계에서 컴파일이 깨져 "한 단계씩 통과시킨다"는 규칙을 어기게 된다.
     */
    private static final Set<String> TARGET_CONTENT_TYPES =
            Set.of(TYPE_TOURIST_SPOT, TYPE_CULTURAL_FACILITY, TYPE_LEISURE);

    // ---------- 분류 코드 (cat1 ~ cat3) ----------

    private static final String CAT1_NATURE = "A01";
    private static final String CAT1_HUMANITIES = "A02";
    private static final String CAT2_HISTORIC = "A0201";
    private static final String CAT3_BEACH = "A01011200";
    private static final String CAT3_CAFE = "A05020900";

    /**
     * 아무 규칙에도 걸리지 않았을 때의 값.
     *
     * <p>{@code places.category}는 NOT NULL이고, 여기서 null이나 예외가 나오면 <b>장소 하나 때문에
     * 저장이 통째로 실패한다.</b> 그래서 반드시 값을 준다.
     *
     * <p>{@code NATURE}를 고른 이유는 뽑히는 것의 다수가 관광지(12)이고 그중 상당수가 자연이라,
     * 틀렸을 때 가장 덜 어색하기 때문이다.
     *
     * <p>⚠️ <b>여기로 떨어지는 것이 늘면 신호다.</b> 결정 13으로 전 종류가 들어오게 되면서
     * 알려진 관광타입 8종(12·14·15·25·28·32·38·39)은 전부 고유한 분류를 갖는다.
     * 즉 이 기본값은 <b>TourAPI가 새 종류를 추가했을 때만</b> 쓰여야 한다.
     */
    private static final TravelCategory DEFAULT_CATEGORY = TravelCategory.NATURE;

    /**
     * 이 관광타입을 적재할 것인가.
     *
     * <p>분류({@link #map})와 나눠 둔 이유: "담을지 말지"와 "담는다면 무엇으로 볼지"는 다른 판단이다.
     * 나중에 음식점까지 담기로 해도 매핑 규칙은 그대로 쓴다.
     */
    public boolean isTargetContentType(String contentTypeId) {
        return contentTypeId != null && TARGET_CONTENT_TYPES.contains(contentTypeId.trim());
    }

    /**
     * 관광타입과 분류코드를 보고 우리 분류를 정한다.
     *
     * <p>좁은 규칙부터 본다. 해수욕장({@code cat3})이 자연({@code cat1})보다 먼저 걸려야
     * 해변이 자연으로 뭉뚱그려지지 않는다.
     *
     * @return 절대 null이 아니다. 모르는 값이면 {@link #DEFAULT_CATEGORY}
     */
    public TravelCategory map(String contentTypeId, String cat1, String cat2, String cat3) {
        String type = normalize(contentTypeId);
        String c1 = normalize(cat1);
        String c2 = normalize(cat2);
        String c3 = normalize(cat3);

        if (TYPE_CULTURAL_FACILITY.equals(type)) {
            return TravelCategory.CULTURE;
        }
        if (TYPE_FESTIVAL.equals(type)) {
            return TravelCategory.FESTIVAL;
        }
        if (TYPE_COURSE.equals(type)) {
            return TravelCategory.COURSE;
        }
        if (TYPE_LEISURE.equals(type)) {
            return TravelCategory.LEISURE;
        }
        if (TYPE_LODGING.equals(type)) {
            return TravelCategory.LODGING;
        }
        if (TYPE_SHOPPING.equals(type)) {
            return TravelCategory.SHOPPING;
        }
        if (TYPE_RESTAURANT.equals(type)) {
            return CAT3_CAFE.equals(c3) ? TravelCategory.CAFE : TravelCategory.FOOD;
        }
        if (TYPE_TOURIST_SPOT.equals(type)) {
            return mapTouristSpot(c1, c2, c3);
        }
        return DEFAULT_CATEGORY;
    }

    /**
     * 관광지(12)를 분류코드로 나눈다. <b>여기가 이 클래스의 존재 이유다.</b>
     *
     * <p>관광지 하나에 해수욕장·산·유적지가 전부 들어 있어서, 이걸 나누지 않으면
     * 슬롯 응답의 {@code category}가 전부 같은 값으로 나간다.
     */
    private TravelCategory mapTouristSpot(String cat1, String cat2, String cat3) {
        if (CAT3_BEACH.equals(cat3)) {
            return TravelCategory.BEACH;
        }
        if (CAT2_HISTORIC.equals(cat2)) {
            return TravelCategory.HISTORY;
        }
        if (CAT1_NATURE.equals(cat1)) {
            return TravelCategory.NATURE;
        }
        if (CAT1_HUMANITIES.equals(cat1)) {
            return TravelCategory.CULTURE;
        }
        return DEFAULT_CATEGORY;
    }

    /** 대소문자·공백 차이로 매칭이 어긋나지 않게 맞춘다. */
    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }
}
