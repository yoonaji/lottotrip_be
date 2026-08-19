package com.lottotrip.place.repository;

import com.lottotrip.place.entity.PlaceMedia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 장소 이미지 저장소.
 *
 * **대표 이미지는 "가장 먼저 담긴 것"으로 정했다**(6-6). TourAPI 목록이 주는
 * `firstimage`가 이미 그 장소의 대표 이미지라 따로 고를 기준이 필요 없다.
 * 나중에 여러 장을 담게 되면 대표 플래그를 두는 쪽으로 바꾼다.
 */
public interface PlaceMediaRepository extends JpaRepository<PlaceMedia, Long> {

    /**
     * 이 장소에 같은 URL의 이미지가 이미 있는지.
     *
     * 랜덤 추첨이라 같은 장소가 여러 번 뽑힌다. 막지 않으면 뽑힐 때마다
     * 같은 이미지 행이 하나씩 늘어난다. `places`는 `content_id` UNIQUE로 막혀 있지만
     * `place_media`에는 그런 제약이 없어 `PlaceUpserter`가 이 메서드로 확인한다.
     */
    boolean existsByPlaceIdAndMediaUrl(Long placeId, String mediaUrl);

    /**
     * 이 장소의 대표 이미지.
     *
     * ⚠️ **과거 배치 방식(결정 10)에서 쓰던 것. 현재 사용 안 함** — 호출처가 main·테스트 모두 없다.
     * 그때는 DB에서 뽑았으므로 썸네일도 `place_media`에서 다시 조회해야 했다.
     * 결정 12에서는 `draw`가 받은 응답의 `firstimage`를 그대로 응답에 실어
     * **다시 조회할 이유가 없다.** 지울지 여부는 사용자에게 확인한다.
     */
    Optional<PlaceMedia> findFirstByPlaceIdOrderByIdAsc(Long placeId);
}
