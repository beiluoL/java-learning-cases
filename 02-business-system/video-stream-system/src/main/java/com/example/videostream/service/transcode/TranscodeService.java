package com.example.videostream.service.transcode;

import com.example.videostream.common.BizException;
import com.example.videostream.common.ErrorCode;
import com.example.videostream.common.MediaConstants;
import com.example.videostream.domain.MediaAsset;
import com.example.videostream.domain.TranscodeTask;
import com.example.videostream.dto.message.TranscodeTaskMessage;
import com.example.videostream.repository.MediaAssetRepository;
import com.example.videostream.repository.TranscodeTaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 转码编排服务（控制面）：
 * 1. 为一个媒资的「全部清晰度档位」创建 TranscodeTask 并落库；
 * 2. 将任务消息投递到 Kafka，由无状态的转码 Worker 消费执行（见 TranscodeTaskConsumer）；
 * 3. 提供任务状态聚合与「全部完成则媒资置为 READY」的逻辑。
 */
@Slf4j
@Service
public class TranscodeService {

    private final TranscodeProfileService profileService;
    private final TranscodeTaskRepository taskRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final TranscodeTaskProducer producer;

    public TranscodeService(TranscodeProfileService profileService,
                            TranscodeTaskRepository taskRepository,
                            MediaAssetRepository mediaAssetRepository,
                            TranscodeTaskProducer producer) {
        this.profileService = profileService;
        this.taskRepository = taskRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.producer = producer;
    }

    /**
     * 为某个媒资创建并下发全部清晰度档位的转码任务。
     *
     * @param mediaId         媒资ID
     * @param sourceObjectKey 原始视频对象存储 key
     */
    @Transactional
    public void scheduleTranscode(String mediaId, String sourceObjectKey) {
        if (profileService.isEmpty()) {
            throw new BizException(ErrorCode.TRANSCODE_PROFILE_UNKNOWN, "未配置任何转码档位");
        }
        MediaAsset asset = mediaAssetRepository.findByMediaId(mediaId)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "媒资不存在"));

        List<TranscodeTask> tasks = new ArrayList<>();
        for (var profile : profileService.allOrdered()) {
            String taskId = UUID.randomUUID().toString().replace("-", "");
            String outputPrefix = MediaConstants.OSS_PREFIX_TRANSCODE + mediaId + "/" + profile.getName();
            TranscodeTask task = new TranscodeTask();
            task.setTaskId(taskId);
            task.setMediaId(mediaId);
            task.setProfileName(profile.getName());
            task.setSourceObjectKey(sourceObjectKey);
            task.setOutputPrefix(outputPrefix);
            task.setStatus(MediaConstants.TaskStatus.PENDING);
            task.setAttempt(0);
            tasks.add(task);
        }
        taskRepository.saveAll(tasks);

        // 标记媒资进入转码中
        asset.setStatus(MediaConstants.MediaStatus.TRANSCODING);
        asset.setUpdatedAt(LocalDateTime.now());
        mediaAssetRepository.save(asset);

        // 投递到 MQ（每个档位一条消息，便于分布式并行）
        for (TranscodeTask task : tasks) {
            producer.send(TranscodeTaskMessage.builder()
                    .taskId(task.getTaskId())
                    .mediaId(task.getMediaId())
                    .profileName(task.getProfileName())
                    .sourceObjectKey(task.getSourceObjectKey())
                    .outputPrefix(task.getOutputPrefix())
                    .attempt(task.getAttempt())
                    .build());
        }
        log.info("scheduled {} transcode tasks for mediaId={}", tasks.size(), mediaId);
    }

    /**
     * 某个档位任务完成后回调：更新任务状态；若全部档位完成则聚合生成 master 播放列表并置媒资 READY。
     *
     * @param taskId            任务ID
     * @param playlistKey       该档位 m3u8 在对象存储的 key
     * @param success           是否成功
     * @param errorMessage      失败原因
     */
    @Transactional
    public void onTaskFinished(String taskId, String playlistKey, boolean success, String errorMessage) {
        TranscodeTask task = taskRepository.findByTaskId(taskId)
                .orElseThrow(() -> new BizException(ErrorCode.TRANSCODE_TASK_NOT_FOUND, taskId));
        task.setStatus(success ? MediaConstants.TaskStatus.SUCCESS : MediaConstants.TaskStatus.FAILED);
        task.setOutputPlaylistKey(success ? playlistKey : null);
        task.setErrorMessage(success ? null : errorMessage);
        task.setFinishedAt(LocalDateTime.now());
        taskRepository.save(task);

        if (!success) {
            markMediaFailed(task.getMediaId());
            return;
        }
        // 检查是否全部成功
        List<TranscodeTask> all = taskRepository.findByMediaId(task.getMediaId());
        boolean allDone = all.stream().allMatch(t -> t.getStatus() == MediaConstants.TaskStatus.SUCCESS);
        if (allDone) {
            MediaAsset asset = mediaAssetRepository.findByMediaId(task.getMediaId()).orElseThrow();
            asset.setStatus(MediaConstants.MediaStatus.READY);
            // master 播放列表 key 约定为 transcode/{mediaId}/master.m3u8（由 Worker 聚合生成）
            asset.setMasterPlaylistKey(MediaConstants.OSS_PREFIX_TRANSCODE + task.getMediaId() + "/master.m3u8");
            asset.setUpdatedAt(LocalDateTime.now());
            mediaAssetRepository.save(asset);
            log.info("mediaId={} all profiles transcoded, status=READY", task.getMediaId());
        }
    }

    private void markMediaFailed(String mediaId) {
        mediaAssetRepository.findByMediaId(mediaId).ifPresent(asset -> {
            asset.setStatus(MediaConstants.MediaStatus.FAILED);
            asset.setUpdatedAt(LocalDateTime.now());
            mediaAssetRepository.save(asset);
        });
    }
}
