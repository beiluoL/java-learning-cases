package com.example.videostream.repository;

import com.example.videostream.domain.MediaAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {

    Optional<MediaAsset> findByMediaId(String mediaId);

    Optional<MediaAsset> findByFileHash(String fileHash);
}
