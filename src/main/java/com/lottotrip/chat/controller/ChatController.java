package com.lottotrip.chat.controller;

import com.lottotrip.chat.dto.ChatMessageHistoryResponse;
import com.lottotrip.chat.dto.ChatRoomListResponse;
import com.lottotrip.chat.service.ChatService;
import com.lottotrip.common.auth.CurrentUserId;
import com.lottotrip.common.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/rooms")
    public ApiResponse<ChatRoomListResponse> getMyRooms(@CurrentUserId Long userId) {
        return ApiResponse.success(HttpStatus.OK.value(), chatService.getMyRooms(userId));
    }

    @GetMapping("/rooms/{roomId}/messages")
    public ApiResponse<ChatMessageHistoryResponse> getHistory(
            @CurrentUserId Long userId,
            @PathVariable Long roomId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.success(HttpStatus.OK.value(), chatService.getHistory(userId, roomId, cursor, size));
    }
}
