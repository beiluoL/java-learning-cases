package com.example.videostream.mq;

import com.example.videostream.common.BizException;
import com.example.videostream.common.MediaConstants;
import com.example.videostream.config.KafkaConfig;
import com.example.videostream.domain.TranscodeTask;
import com.example.videostream.dto.message.TranscodeTaskMessage;
import com.example.videostream.properties.TranscodeProperties.TranscodeProfile;
import com.example.videostream.repository.TranscodeTaskRepository;
import com.example.videostream.service.storage.ObjectStorageService;
import com.example.videostream.service.transcode.FfmpegWrapper;
import com.example.videostream.service.transcode.TranscodeProfileService;
import com.example.videostream.service.transcode.TranscodeService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

/**
 * 转码 Worker（消费侧）：在独立节点运行（生产环境通过 Spring Profile 关闭控制面的监听）。
 * 流程：下载源片 → FFmpeg 转单档位 HLS → 上传切片/播放列表 → 生成/更新 master 播放列表 → 回告状态。
 * 采用「手动提交位移」：转码成功并回告后才 ack，失败不 ack 触发重试/死信，保证任务不丢。
 */
@Slf4j
@Component
public class TranscodeTaskConsumer {

    private final ObjectStorageService storage;
    private final FfmpegWrapper ffmpegWrapper;
    private final TranscodeProfileService profileService;
    private final TranscodeTaskRepository taskRepository;
    private final TranscodeService transcodeService;

    public TranscodeTaskConsumer(ObjectStorageService storage,
                                 FfmpegWrapper ffmpegWrapper,
                                 TranscodeProfileService profileService,
                                 TranscodeTaskRepository taskRepository,
                                 TranscodeService transcodeService) {
        this.storage = storage;
        this.ffmpegWrapper = ffmpegWrapper;
        this.profileService = profileService;
        this.taskRepository = taskRepository;
        this.transcodeService = transcodeService;
    }

    @KafkaListener(topics = KafkaConfig.TOPIC_TRANSCODE, containerFactory = "transcodeListenerContainerFactory")
    public void consume(ConsumerRecord<String, Object> record, Acknowledgment ack) {
        TranscodeTaskMessage msg = (TranscodeTaskMessage) record.value();
        log.info("consume transcode task taskId={} profile={}", msg.getTaskId(), msg.getProfileName());
        try {
            TranscodeTask task = taskRepository.findByTaskId(msg.getTaskId()).orElse(null);
            if (task == null) {
                ack.acknowledge();
                return;
            }
            if (task.getStatus() == MediaConstants.TaskStatus.SUCCESS
                    || task.getStatus() == MediaConstants.TaskStatus.FAILED) {
                ack.acknowledge(); // 已终态，直接确认
                return;
            }
            task.setStatus(MediaConstants.TaskStatus.RUNNING);
            task.setStartedAt(LocalDateTime.now());
            task.setAttempt(msg.getAttempt());
            taskRepository.save(task);

            Path tmp = Files.createTempDirectory("transcode-");
            Path source = tmp.resolve("source.mp4");
            storage.downloadFile(msg.getSourceObjectKey(), source);

            TranscodeProfile profile = profileService.getProfile(msg.getProfileName());
            Path outDir = tmp.resolve("out");
            FfmpegWrapper.TranscodeResult result = ffmpegWrapper.transcodeToHlsVariant(source, outDir, profile);

            if (!result.isSuccess()) {
                transcodeService.onTaskFinished(msg.getTaskId(), null, false, result.getErrorLog());
                ack.acknowledge();
                return;
            }

            // 上传该档位全部产物（m3u8 + ts）到 outputPrefix 目录
            String prefix = msg.getOutputPrefix();
            try (Stream<Path> files = Files.list(outDir)) {
                files.filter(Files::isRegularFile).forEach(f -> {
                    String key = prefix + "/" + f.getFileName().toString();
                    storage.uploadFile(key, f);
                });
            }
            String playlistKey = prefix + "/" + profile.getName() + ".m3u8";

            // 生成/更新 master 播放列表（幂等覆盖）
            buildAndUploadMaster(msg.getMediaId(), prefix);

            transcodeService.onTaskFinished(msg.getTaskId(), playlistKey, true, null);
            ack.acknowledge();
        } catch (BizException be) {
            log.warn("transcode biz fail taskId={} msg={}", msg.getTaskId(), be.getBizMessage());
            transcodeService.onTaskFinished(msg.getTaskId(), null, false, be.getBizMessage());
            ack.acknowledge();
        } catch (IOException e) {
            log.error("transcode io error taskId={}", msg.getTaskId(), e);
            transcodeService.onTaskFinished(msg.getTaskId(), null, false, e.getMessage());
            ack.acknowledge();
        } catch (Exception e) {
            // 未知异常：不 ack，交由 Kafka 重试（注意控制重试上限避免死循环）
            log.error("transcode unexpected error taskId={}", msg.getTaskId(), e);
        }
    }

    /** 聚合所有成功档位，生成 HLS master 播放列表（幂等，可多次覆盖）。 */
    private void buildAndUploadMaster(String mediaId, String prefix) throws IOException {
        List<TranscodeTask> done = taskRepository.findByMediaIdAndStatus(
                mediaId, MediaConstants.TaskStatus.SUCCESS);
        if (done.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n");
        for (TranscodeTask t : done) {
            TranscodeProfile p = profileService.getProfile(t.getProfileName());
            int bandwidth = parseKbps(p.getVideoBitrate()) * 1000 + parseKbps(p.getAudioBitrate()) * 1000;
            sb.append("#EXT-X-STREAM-INF:BANDWIDTH=").append(bandwidth)
                    .append(",RESOLUTION=").append(p.getWidth()).append("x").append(p.getHeight())
                    .append(",CODECS=\"avc1.640028,mp4a.40.2\"\n")
                    .append(p.getName()).append(".m3u8\n");
        }
        Path master = Files.createTempFile("master", ".m3u8");
        Files.writeString(master, sb.toString());
        storage.uploadFile(prefix + "/master.m3u8", master);
    }

    private int parseKbps(String v) {
        try {
            return Integer.parseInt(v.replace("k", "").trim());
        } catch (Exception e) {
            return 0;
        }
    }
}
