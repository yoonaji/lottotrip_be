package com.lottotrip.video.repository;

import com.lottotrip.video.entity.JobStatus;
import com.lottotrip.video.entity.ShortformJob;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShortformJobRepository extends JpaRepository<ShortformJob, String> {

    List<ShortformJob> findByStatus(JobStatus status);
}
