package com.lottotrip.place.repository;

import com.lottotrip.place.entity.PlaceMedia;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 장소 이미지 저장소.
 *
 * <p>응답의 {@code thumbnailUrl}로 어떤 이미지를 고를지(첫 장인지, 대표 플래그를 둘지)가
 * 아직 정해지지 않아 조회 메서드를 두지 않았다. 6-6에서 확정한다.
 */
public interface PlaceMediaRepository extends JpaRepository<PlaceMedia, Long> {
}
