package com.example.csagent.repository;

import com.example.csagent.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, String> {

    List<Message> findByConversationIdOrderBySeqAsc(String conversationId);

    int countByConversationId(String conversationId);

    void deleteByConversationId(String conversationId);

    @Query("SELECT COALESCE(MAX(m.seq), 0) FROM Message m WHERE m.conversationId = :cid")
    int maxSeq(@Param("cid") String cid);
}
