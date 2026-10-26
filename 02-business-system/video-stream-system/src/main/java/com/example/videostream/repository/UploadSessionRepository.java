package com.example.videostream.repository;

import com.example.videostream.domain.UploadSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UploadSessionRepository extends JpaRepository<UploadSession, Long> {

    Optional<UploadSession> findBySessionId(String sessionId);

    Optional<UploadSession> findByFileHash(String fileHash);
}
