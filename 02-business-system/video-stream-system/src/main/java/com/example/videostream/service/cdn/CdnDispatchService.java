package com.example.videostream.service.cdn;

import com.example.videostream.common.BizException;
import com.example.videostream.common.ErrorCode;
import com.example.videostream.properties.CdnProperties;
import com.example.videostream.service.storage.ObjectStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * CDN 多节点调度：根据观众所在区域选择就近边缘域名，并生成带防盗链签名的播放 URL。
 * 生产可扩展为「按实时质量（RTT/丢包/命中率）动态选路」的多 CDN 调度器。
 */
@Slf4j
@Service
public class CdnDispatchService {

    private final CdnProperties cdnProperties;
    private final ObjectStorageService storage;
    private final UrlSignService urlSignService;

    public CdnDispatchService(CdnProperties cdnProperties,
                              ObjectStorageService storage,
                              UrlSignService urlSignService) {
        this.cdnProperties = cdnProperties;
        this.storage = storage;
        this.urlSignService = urlSignService;
    }

    /** 选择区域对应的 CDN 域名（缺省回退 DEFAULT）。 */
    public String selectDomain(String region) {
        Map<String, String> map = cdnProperties.getRegionDomains();
        String domain = (region != null && map.containsKey(region)) ? map.get(region) : map.get("DEFAULT");
        if (domain == null) {
            throw new BizException(ErrorCode.CDN_NO_AVAILABLE_NODE);
        }
        return domain;
    }

    /**
     * 生成对象在指定区域的签名播放 URL。
     *
     * @param objectKey 对象存储 key
     * @param region    观众区域（AP/EU/US/...）
     * @param ttlSeconds 签名有效期
     */
    public String buildSignedUrl(String objectKey, String region, long ttlSeconds) {
        String domain = selectDomain(region);
        String baseUrl = domain + storage.getObjectBasePath(objectKey);
        return urlSignService.sign(baseUrl, ttlSeconds);
    }
}
