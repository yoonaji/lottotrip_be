package com.lottotrip.course.repository;

import com.lottotrip.course.entity.TravelCourse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 여행 코스 저장소. */
public interface TravelCourseRepository extends JpaRepository<TravelCourse, Long> {

    /**
     * 회원의 코스를 찾는다.
     *
     * 회원당 코스가 하나인지 여럿인지가 아직 확정되지 않아(코스 생성 API 부재, 7-1에서 결정)
     * `Optional`이 아니라 `List`로 받는다. 하나로 확정되면 그때 좁힌다.
     */
    List<TravelCourse> findByUserId(Long userId);
}
