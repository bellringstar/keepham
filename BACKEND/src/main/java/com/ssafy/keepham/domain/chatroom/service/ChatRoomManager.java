package com.ssafy.keepham.domain.chatroom.service;

import com.ssafy.keepham.common.error.ChatRoomError;
import com.ssafy.keepham.common.error.ErrorCode;
import com.ssafy.keepham.common.exception.ApiException;
import com.ssafy.keepham.domain.chat.entity.Message;
import com.ssafy.keepham.domain.chat.entity.MessageRepository;
import com.ssafy.keepham.domain.chatroom.converter.ChatRoomConverter;
import com.ssafy.keepham.domain.chatroom.dto.ChatRoomResponse;
import com.ssafy.keepham.domain.chatroom.dto.ExtendRequest;
import com.ssafy.keepham.domain.chatroom.dto.KickRequest;
import com.ssafy.keepham.domain.chatroom.dto.NewSuperUser;
import com.ssafy.keepham.domain.chatroom.entity.ChatRoomEntity;
import com.ssafy.keepham.domain.chatroom.entity.RoomUserEntity;
import com.ssafy.keepham.domain.chatroom.entity.enums.ChatRoomStatus;
import com.ssafy.keepham.domain.chatroom.entity.enums.RoomUserStatus;
import com.ssafy.keepham.domain.chatroom.repository.ChatRoomRepository;
import com.ssafy.keepham.domain.chatroom.repository.RoomUserRepository;
import com.ssafy.keepham.domain.user.repository.UserRepository;
import com.ssafy.keepham.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
@PropertySource("classpath:kafka.properties")
public class ChatRoomManager {
    private final UserRepository userRepository;

    private final ChatRoomRepository chatRoomRepository;
    private final KafkaTemplate<String, Message> kafkaTemplate;
    private final MessageRepository messageRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final UserService userService;
    private final ChatRoomConverter chatRoomConverter;
    private final RoomUserRepository roomUserRepository;



    public boolean isPasswordCorrect(Long roomId, String password){
        var room = chatRoomRepository.findFirstByIdAndStatus(roomId, ChatRoomStatus.OPEN);
        log.info("room password : {}", room.getPassword());
        log.info("입력받은 비밀번호 : {}", password);
        return room.getPassword().equals(password);
    }

    public boolean isSecretRoom(Long roomId){
        return chatRoomRepository.findFirstByIdAndStatus(roomId, ChatRoomStatus.OPEN).isLocked();
    }

    // 채팅방에 user 접속하면 해당 방 인원 1 증가
    public Set<String> userJoin(Long roomId, String userNickname){
        // TODO 이미 존재하는 유저 재입장 막기
        Optional<RoomUserEntity> roomUser = roomUserRepository.findFirstByRoomIdAndUserNickName(roomId, userNickname);

        if (roomUser.isPresent()) {
            if (roomUser.get().getUserNickName().equals(userNickname)) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "이미 채팅방에 존재하는 유저입니다.");
            }
            if (roomUser.get()
                    .getStatus()
                    .equals(RoomUserStatus.KICKED)){
            throw new ApiException(ErrorCode.BAD_REQUEST, "추방당한 유저는 입장할 수 없습니다.");}
        }


        Long currentUserCount = getUserCountInChatRoom(roomId);
        int maxUserCount = getMaxUsersInChatRoom(roomId);

        if(currentUserCount >= maxUserCount){
            throw new ApiException(ChatRoomError.BAD_REQUEST);
        }

        var roomUserEntity = RoomUserEntity.builder()
                .roomId(roomId)
                .userNickName(userNickname)
                .status(RoomUserStatus.NORMAL)
                .build();
        roomUserRepository.save(roomUserEntity);

        redisTemplate.opsForSet().add("roomId" + String.valueOf(roomId),userNickname);
        redisTemplate.expire(String.valueOf("roomId" + String.valueOf(roomId)), 3600*3, TimeUnit.SECONDS);


        currentUserCount = getUserCountInChatRoom(roomId);
        log.info("입장 후 현재 인원 {}", currentUserCount);
        log.info("치팅방 최대 {}", maxUserCount);
        log.info("입장 {}", redisTemplate.opsForSet().members("roomId" + String.valueOf(roomId)));

        return redisTemplate.opsForSet().members("roomId" + String.valueOf(roomId));

    }
    // 채팅방에서 user가 떠나면 해당 방 인원 감소
    @Transactional
    public void userLeft(Long roomId, String userNickname, RoomUserStatus status){
        var entity = roomUserRepository.findFirstByRoomIdAndUserNickName(roomId, userNickname)
                .orElseThrow(()-> new ApiException(ErrorCode.BAD_REQUEST,"이미 퇴장한 유저거나 채팅방에 존재하지 않는 유저입니다."));
        entity.setStatus(status);
        roomUserRepository.save(entity);
        log.info("퇴장유저 {}", redisTemplate.opsForSet().members("roomId" + String.valueOf(roomId)));
        redisTemplate.opsForSet().remove("roomId" + String.valueOf(roomId), userNickname);

        Long currentUserCount = getUserCountInChatRoom(roomId);
        int maxUserCount = getMaxUsersInChatRoom(roomId);

        log.info("퇴장후 현재 인원 {}", currentUserCount);
        log.info("채팅방 최대 {}", maxUserCount);
    }

    @Transactional
    public boolean allUserClear(Long roomId){
        List<RoomUserEntity> roomUsers = roomUserRepository.findAllByRoomIdAndStatus(roomId, RoomUserStatus.NORMAL);
        roomUsers.forEach(user -> {
            user.setStatus(RoomUserStatus.EXIT);
            roomUserRepository.save(user);
        });
        redisTemplate.delete("roomId" + String.valueOf(roomId));
        return true;
    }

    public Set<String> getAllUser(Long roomId){
        try{
            return redisTemplate.opsForSet().members("roomId" + String.valueOf(roomId));
        } catch (NullPointerException e){
            roomUserRepository.findAllByRoomIdAndStatus(roomId, RoomUserStatus.NORMAL).stream()
                    .map(it -> {
                        var userNickname = it.getUserNickName();
                        redisTemplate.opsForSet().add("roomId" + String.valueOf(roomId),userNickname);
                        redisTemplate.expire(String.valueOf("roomId" + String.valueOf(roomId)), 3600*3, TimeUnit.SECONDS);
                        return null;
                    });
            return redisTemplate.opsForSet().members("roomId" + String.valueOf(roomId));
        }
    }

    // 채팅방 현재 접속자 수
    public Long getUserCountInChatRoom(Long roomId){
        Long count;
        Long size = redisTemplate.opsForSet().size("roomId" + String.valueOf(roomId));
        if (size != null) {
            count = size;
        } else {
            var currentUser = Optional.ofNullable(roomUserRepository.findAllByRoomIdAndStatus(roomId, RoomUserStatus.NORMAL));
            count = currentUser.isPresent() ? currentUser.get().size() : 0L;
        }
        return count;
    }

    // 채팅방 최대 인원
    public int getMaxUsersInChatRoom(Long roomId){
        var entity = chatRoomRepository.findFirstByIdAndStatus(roomId, ChatRoomStatus.OPEN);
        return Optional.ofNullable(entity)
                .map(ChatRoomEntity::getMaxPeopleNumber)
                .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST,"유효하지 않은 방입니다."));
    }


    public void sendMessageToRoom(@Payload Message message){
        message.setTimestamp(LocalDateTime.now());
        messageRepository.save(message);
        kafkaTemplate.send("kafka-chat", message);
    }

    public Set<String> pickRandomUsers(Set<String> users, int count){
        if (count >= users.size()){
            return new HashSet<>(users);
        }

        List<String> userList = new ArrayList<>(users);
        Set<String> result = new HashSet<>();

        Random random = new Random();
        while (result.size() < count){
            int randomIndex = random.nextInt(userList.size());
            result.add(userList.get(randomIndex));
        }
        return result;
    }

    @Transactional
    public void setSuperUser(NewSuperUser newSuperUser) {
        var roomId = newSuperUser.getRoomId();
        var superUser = newSuperUser.getNewSuperUser();
        userRepository.findFirstByNickName(superUser)
                .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST, "존재하지 않는 유저를 방장으로 임명하고 있습니다."));
        var room = Optional.ofNullable(chatRoomRepository.findFirstByIdAndStatus(roomId, ChatRoomStatus.OPEN))
                .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST, "존재하지 않는 채팅방입니다."));
        var loginUser = userService.getLoginUserInfo().getNickName();
        if (!loginUser.equals(room.getSuperUserId())){
            throw new ApiException(ErrorCode.BAD_REQUEST, "방장 위임을 요청한 유저가 방장이 아닙니다.");
        }
        room.setSuperUserId(superUser);
    }

    @Transactional
    public ChatRoomResponse extendRoomTime(ExtendRequest request){
        var roomId = request.getRoomId();
        var hour = request.getHour();
        var roomEntity = chatRoomRepository.findFirstByIdAndStatus(roomId, ChatRoomStatus.OPEN);
        if (roomEntity.getExtensionNumber() >= 3){
            throw new ApiException(ErrorCode.BAD_REQUEST, "연장 횟수 초과");
        }
        roomEntity.setClosedAt(roomEntity.getClosedAt().plusHours(hour));
        return chatRoomConverter.toResponse(roomEntity);
    }

    @Transactional
    public void kickUser(KickRequest request){
        var requestUser = userService.getLoginUserInfo().getNickName();
        var roomId = request.getRoomId();
        var kickedUserNickName = request.getKickedUserNickName();
        var currentSuperUser = chatRoomRepository.findFirstByIdAndStatus(roomId, ChatRoomStatus.OPEN).getSuperUserId();
        if (!currentSuperUser.equals(requestUser)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "강퇴는 방장만 할 수 있습니다.");
        }
        if (!getAllUser(roomId).contains(kickedUserNickName)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "존재하지 않은 유저를 강퇴할 수 없습니다.");
        }

        if (requestUser.equals(kickedUserNickName)){
            throw new ApiException(ErrorCode.BAD_REQUEST, "자기 자신을 강퇴할 수 없습니다.");
        }
        userLeft(roomId, kickedUserNickName, RoomUserStatus.KICKED);

    }

}
