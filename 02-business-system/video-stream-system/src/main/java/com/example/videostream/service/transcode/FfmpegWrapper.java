package com.example.videostream.service.transcode;

import com.example.videostream.common.MediaConstants;
import com.example.videostream.properties.FfmpegProperties;
import com.example.videostream.properties.TranscodeProperties.TranscodeProfile;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * FFmpeg 封装：负责把单一原始视频转码为「某一个清晰度档位」的 HLS 切片（.m3u8 + .ts）。
 * <p>
 * 设计要点：
 *  - 每个 Worker 每次只处理一个档位，一个媒资的多个档位通过 MQ 分发到不同 Worker 并行，天然分布式；
 *  - 支持 GPU 硬件编码（NVIDIA NVENC）与 CPU 软编码（libx264）切换；
 *  - 通过 ProcessBuilder 调用本地 ffmpeg，捕获退出码与日志，超时强杀防挂死。
 * </p>
 */
@Slf4j
@Component
public class FfmpegWrapper {

    private final FfmpegProperties ffmpegProperties;

    public FfmpegWrapper(FfmpegProperties ffmpegProperties) {
        this.ffmpegProperties = ffmpegProperties;
    }

    @Data
    public static class TranscodeResult {
        private boolean success;
        private String playlistPath;
        private int exitCode;
        private String errorLog;
    }

    /**
     * 转码为单档位 HLS。
     *
     * @param sourceFile 原始视频本地路径（Worker 已从对象存储下载）
     * @param workDir    输出目录（切片与 m3u8 写入此处）
     * @param profile    清晰度档位
     * @return 转码结果
     */
    public TranscodeResult transcodeToHlsVariant(Path sourceFile, Path workDir, TranscodeProfile profile) {
        TranscodeResult result = new TranscodeResult();
        try {
            Files.createDirectories(workDir);
            String variantName = profile.getName();
            String playlist = workDir.resolve(variantName + ".m3u8").toString();
            String segmentPattern = workDir.resolve(variantName + "_%03d.ts").toString();

            List<String> cmd = buildCommand(sourceFile, playlist, segmentPattern, profile);
            log.info("FFmpeg cmd: {}", String.join(" ", cmd));

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(false);
            Process process = pb.start();

            // 捕获 stderr（FFmpeg 日志在 stderr）
            String errorLog = drainError(process.getErrorStream());
            boolean finished = process.waitFor(30, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                result.setSuccess(false);
                result.setErrorLog("FFmpeg 超时（>30min）被强制终止");
                return result;
            }
            result.setExitCode(process.exitValue());
            result.setErrorLog(errorLog);
            result.setPlaylistPath(playlist);
            result.setSuccess(process.exitValue() == 0 && Files.exists(java.nio.file.Path.of(playlist)));
            return result;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            result.setSuccess(false);
            result.setErrorLog(e.getMessage());
            return result;
        }
    }

    private List<String> buildCommand(Path sourceFile, String playlist, String segmentPattern,
                                      TranscodeProfile profile) {
        boolean gpu = ffmpegProperties.isGpuEnabled();
        String videoCodec = gpu ? "h264_nvenc" : profile.getVideoCodec();

        List<String> cmd = new ArrayList<>();
        cmd.add(ffmpegProperties.getPath());
        cmd.add("-y");
        cmd.add("-i");
        cmd.add(sourceFile.toString());
        // 缩放：保持比例，高度为档位高度（-2 保证偶数宽）
        cmd.add("-vf");
        cmd.add("scale=-2:" + profile.getHeight());
        cmd.add("-c:v");
        cmd.add(videoCodec);
        if (gpu) {
            cmd.add("-gpu");
            cmd.add(String.valueOf(ffmpegProperties.getGpuIndex()));
            cmd.add("-preset");
            cmd.add("p4");
        } else {
            cmd.add("-preset");
            cmd.add("veryfast");
        }
        cmd.add("-b:v");
        cmd.add(profile.getVideoBitrate());
        cmd.add("-maxrate");
        cmd.add(profile.getVideoBitrate());
        cmd.add("-bufsize");
        cmd.add(profile.getVideoBitrate());
        cmd.add("-c:a");
        cmd.add("aac");
        cmd.add("-b:a");
        cmd.add(profile.getAudioBitrate());
        cmd.add("-hls_time");
        cmd.add(String.valueOf(MediaConstants.HLS_SEGMENT_SECONDS));
        cmd.add("-hls_playlist_type");
        cmd.add("vod");
        cmd.add("-hls_segment_filename");
        cmd.add(segmentPattern);
        cmd.add(playlist);
        return cmd;
    }

    private String drainError(java.io.InputStream errStream) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(errStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() < 4096) {
                    sb.append(line).append("\n");
                }
            }
        } catch (IOException e) {
            log.warn("read ffmpeg stderr failed", e);
        }
        return sb.toString();
    }
}
