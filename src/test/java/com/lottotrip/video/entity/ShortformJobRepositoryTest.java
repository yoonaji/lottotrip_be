package com.lottotrip.video.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.lottotrip.video.repository.ShortformJobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class ShortformJobRepositoryTest {

    @Autowired
    private ShortformJobRepository shortformJobRepository;

    @Test
    void job과_클립을_함께_저장하고_순서대로_조회한다() {
        ShortformJob job = ShortformJob.create("render_a1b2c3", 1L, "산신령이 점지해 준...", "sanshilling");
        job.addClip(ShortformClip.of("https://s3.../clip1.mp4", 1, "산신령이 점지해 준"));
        job.addClip(ShortformClip.of("https://s3.../clip2.mp4", 2, null));

        shortformJobRepository.save(job);
        shortformJobRepository.flush();

        ShortformJob found = shortformJobRepository.findById("render_a1b2c3").orElseThrow();
        assertThat(found.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(found.getProgress()).isZero();
        assertThat(found.getClips()).extracting("playOrder").containsExactly(1, 2);
    }
}
