package com.lottotrip.chat.repository;

import com.lottotrip.chat.entity.ChatUser;
import com.lottotrip.chat.entity.ChatUserId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatUserRepository extends JpaRepository<ChatUser, ChatUserId> {

    boolean existsByRoomIdAndUserId(Long roomId, Long userId);

    long countByRoomId(Long roomId);

    List<ChatUser> findByUserId(Long userId);

    @Query("select cu.roomId as roomId, count(cu) as memberCount from ChatUser cu where cu.roomId in :roomIds group by cu.roomId")
    List<RoomMemberCount> countMembersByRoomIds(@Param("roomIds") List<Long> roomIds);
}
