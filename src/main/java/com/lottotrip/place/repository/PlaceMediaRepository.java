package com.lottotrip.place.repository;

import com.lottotrip.place.entity.PlaceMedia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 장소 이미지 저장소.
 *
 * <p><b>대표 이미지는 "가장 먼저 담긴 것"으로 정했다</b>(6-6). TourAPI 목록이 주는
 * {@code firstimage}가 이미 그 장소의 대표 이미지라 따로 고를 기준이 필요 없다.
 * 나중에 여러 장을 담게 되면 대표 플래그를 두는 쪽으로 바꾼다.
 */
public interface PlaceMediaRepository extends JpaRepository<PlaceMedia, Long> {

    /**
     * 이 장소에 같은 URL의 이미지가 이미 있는지. (5-9 적재)
     *
     * <p>적재는 여러 번 돌아간다(중간 실패 후 재실행, 정기 갱신). 막지 않으면 돌릴 때마다
     * 같은 이미지 행이 하나씩 늘어난다. {@code places}는 {@code content_id} UNIQUE로 막혀 있지만
     * {@code place_media}에는 그런 제약이 없다.
     */
    boolean existsByPlaceIdAndMediaUrl(Long placeId, String mediaUrl);

    /**
     * 이 장소의 대표 이미지. 슬롯 응답의 {@code thumbnailUrl}이 된다. (6-6)
     *
     * <p>실측 채움률이 18%라 <b>대부분의 장소에는 없다.</b> 그래서 {@code Optional}이다.
     */
    Optional<PlaceMedia> findFirstByPlaceIdOrderByIdAsc(Long placeId);
}
