package com.example.videostream.repository;

import com.example.videostream.domain.TranscodeTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TranscodeTaskRepository extends JpaRepository<TranscodeTask, Long> {

    Optional<TranscodeTask> findByTaskId(String taskId);

    List<TranscodeTask> findByMediaId(String mediaId);

    List<TranscodeTask> findByMediaIdAndStatus(String mediaId,
                                               com.example.videostream.common.MediaConstants.TaskStatus status);
}
