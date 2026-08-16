package com.lottotrip.course.repository;

import com.lottotrip.course.entity.CourseItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** 코스 항목 저장소. */
public interface CourseItemRepository extends JpaRepository<CourseItem, Long> {

    /**
     * 이미 담긴 장소인지 확인한다. 명세의 `409 ALREADY_ADDED` 판정에 쓴다.
     *
     * `existsBy`는 항목을 통째로 읽어 오지 않고 "있는지 없는지"만 확인한다.
     */
    boolean existsByCourseIdAndPlaceId(Long courseId, Long placeId);

    /** 코스에 담긴 항목을 담은 순서대로 조회한다. `GET /course/items` 응답에 쓴다. */
    List<CourseItem> findByCourseIdOrderBySequenceAsc(Long courseId);

    /**
     * 코스의 마지막 순번을 찾는다. 새 항목은 이 값 + 1로 채운다. (tour_api_erd.md 1)
     *
     * 메서드 이름만으로는 만들 수 없는 쿼리라 직접 적었다.
     * 항목이 하나도 없으면 `null`을 돌려준다 — 첫 항목이라는 뜻이다.
     */
    @Query("SELECT MAX(item.sequence) FROM CourseItem item WHERE item.course.id = :courseId")
    Integer findMaxSequence(@Param("courseId") Long courseId);
}
