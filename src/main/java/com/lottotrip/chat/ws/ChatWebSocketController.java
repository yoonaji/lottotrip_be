package com.lottotrip.chat.ws;

import com.lottotrip.chat.dto.ChatMessageBroadcast;
import com.lottotrip.chat.dto.ChatSendRequest;
import com.lottotrip.chat.service.ChatService;
import com.lottotrip.common.error.ApiException;
import com.lottotrip.common.error.ErrorCode;
import com.lottotrip.common.response.ApiError;
import java.security.Principal;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class ChatWebSocketController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatWebSocketController(ChatService chatService, SimpMessagingTemplate messagingTemplate) {
        this.chatService = chatService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chat/rooms/{roomId}")
    public void sendMessage(@DestinationVariable Long roomId, ChatSendRequest request, Principal principal) {
        Long userId = resolveUserId(principal);
        try {
            ChatMessageBroadcast broadcast = chatService.send(userId, roomId, request.messageText());
            messagingTemplate.convertAndSend("/sub/chat/rooms/" + roomId, broadcast);
        } catch (ApiException e) {
            messagingTemplate.convertAndSendToUser(
                    principal.getName(),
                    "/queue/errors",
                    new ApiError(e.getErrorCode().getCode(), e.getErrorCode().getMessage())
            );
        }
    }

    private Long resolveUserId(Principal principal) {
        try {
            return Long.valueOf(principal.getName());
        } catch (NumberFormatException e) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
    }
}
