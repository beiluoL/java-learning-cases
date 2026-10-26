package com.example.videostream.service.transcode;

import com.example.videostream.common.BizException;
import com.example.videostream.common.ErrorCode;
import com.example.videostream.properties.TranscodeProperties.TranscodeProfile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 转码档位查询服务：包装配置中的档位 Map / 有序 List，供编排与播放使用。
 */
@Service
public class TranscodeProfileService {

    private final Map<String, TranscodeProfile> profileMap;
    private final List<TranscodeProfile> orderedProfiles;

    public TranscodeProfileService(Map<String, TranscodeProfile> profileMap,
                                   List<TranscodeProfile> orderedProfiles) {
        this.profileMap = profileMap;
        this.orderedProfiles = orderedProfiles;
    }

    public TranscodeProfile getProfile(String name) {
        TranscodeProfile p = profileMap.get(name);
        if (p == null) {
            throw new BizException(ErrorCode.TRANSCODE_PROFILE_UNKNOWN, name);
        }
        return p;
    }

    /** 全部档位，按清晰度从高到低（tier 升序） */
    public List<TranscodeProfile> allOrdered() {
        return orderedProfiles;
    }

    public boolean isEmpty() {
        return profileMap.isEmpty();
    }
}
