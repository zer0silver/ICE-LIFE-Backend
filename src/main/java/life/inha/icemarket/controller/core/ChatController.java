package life.inha.icemarket.controller.core;

import life.inha.icemarket.domain.core.Room;
import life.inha.icemarket.domain.core.User;
import life.inha.icemarket.respository.ChatRepository;
import life.inha.icemarket.respository.RoomRepository;
import life.inha.icemarket.respository.UserRepository;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.List;

@Controller
public class ChatController {
    private final RoomRepository roomRepository;
    private final ChatRepository chatRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatController(RoomRepository roomRepository, ChatRepository chatRepository, UserRepository userRepository, SimpMessagingTemplate messagingTemplate) {
        this.roomRepository = roomRepository;
        this.chatRepository = chatRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/room/create")
    @Transactional
    public void createRoom(Principal principal, String message) {
        int userId = Integer.parseInt(principal.getName());
        if (!userRepository.existsById(userId)) {
            // throw no user exception
            return;
        }
        String[] args = message.split(",");
        if (args.length == 0) {
            // throw no arg exception
            return;
        }
        String roomName = args[0];
        Room room = Room.builder()
                .name(roomName)
                .build();
        Room createdRoom = roomRepository.save(room);
        if (args.length >= 2) {
            for (int i = 1; i < args.length; i += 1) {
                int targetUserId = Integer.parseInt(args[i]);
                if (!userRepository.existsById(targetUserId)) {
                    // throw no user exception
                    return;
                }
                roomRepository.joinUser(createdRoom.getId(), userId);
            }
        }
    }

    @MessageMapping("/room/{roomId}")
    @Transactional
    public void chat(Principal principal, @DestinationVariable int roomId, String message) {
        int sourceUserId = Integer.parseInt(principal.getName());
        Room room = roomRepository.findById(roomId).orElseThrow(); // check no room exception
        if (room.getUsers().stream().noneMatch(user -> user.getId() == sourceUserId)) {
            // throw no user in room exception
            return;
        }
        List<User> users = room.getUsers();

        chatRepository.record(roomId, sourceUserId, message);

        for (User targetUser : users) {
            messagingTemplate.convertAndSendToUser(String.valueOf(targetUser.getId()), "/queue/room/" + roomId, message);
        }
    }

    @MessageMapping("/room/{roomId}/join")
    @Transactional
    public void joinRoom(Principal principal, @DestinationVariable int roomId) {
        int userId = Integer.parseInt(principal.getName());
        if (!roomRepository.existsById(roomId)) {
            // throw no room exception
            return;
        } else if (!userRepository.existsById(userId)) {
            // throw no user exception
            return;
        }
        roomRepository.joinUser(roomId, userId);
    }

    @MessageMapping("/room/{roomId}/leave")
    @Transactional
    public void leaveRoom(Principal principal, @DestinationVariable int roomId) {
        int userId = Integer.parseInt(principal.getName());
        if (!userRepository.existsById(userId)) {
            // throw no user exception
            return;
        }
        Room room = roomRepository.findById(roomId).orElseThrow(); // check no room exception
        if (room.getUsers().stream().noneMatch(user -> user.getId() == userId)) {
            // throw no user in room exception
            return;
        }
        roomRepository.leaveUser(roomId, userId);
    }
}
