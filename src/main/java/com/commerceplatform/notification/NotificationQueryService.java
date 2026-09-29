package com.commerceplatform.notification;

import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

@Service
public class NotificationQueryService {
    private final NotificationRecordRepository records; private final Clock clock=Clock.systemUTC();
    NotificationQueryService(NotificationRecordRepository records){this.records=records;}
    @Transactional(readOnly=true)
    PageResponse page(UUID user,boolean unread,int page,int size){
        if(page<0||size<1||size>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid page");
        Page<NotificationRecord> found=unread?records.findAllByRecipientIdAndReadAtIsNullOrderByCreatedAtDescIdDesc(user,PageRequest.of(page,size)):
                records.findAllByRecipientIdOrderByCreatedAtDescIdDesc(user,PageRequest.of(page,size));
        return new PageResponse(found.stream().map(NotificationQueryService::response).toList(),page,size,found.getTotalElements(),found.getTotalPages(),found.isFirst(),found.isLast());
    }
    @Transactional(readOnly=true) CountResponse count(UUID user){return new CountResponse(records.countByRecipientIdAndReadAtIsNull(user));}
    @Transactional NotificationResponse read(UUID user,UUID id){NotificationRecord n=records.findByIdAndRecipientId(id,user).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Notification was not found"));n.markRead(clock.instant());return response(n);}
    @Transactional CountResponse readAll(UUID user){for(;;){Page<NotificationRecord> p=records.findAllByRecipientIdAndReadAtIsNullOrderByCreatedAtDescIdDesc(user,PageRequest.of(0,100));if(p.isEmpty())break;p.forEach(n->n.markRead(clock.instant()));if(p.isLast())break;}return count(user);}
    static NotificationResponse response(NotificationRecord n){return new NotificationResponse(n.getId(),n.getType(),n.getTitle(),n.getBody(),n.getResourceType(),n.getResourceId(),n.isRead(),n.getCreatedAt());}
    public record NotificationResponse(UUID id,String type,String title,String body,String resourceType,String resourceId,boolean read,Instant createdAt){}
    public record CountResponse(long unreadCount){}
    public record PageResponse(List<NotificationResponse> content,int page,int size,long totalElements,int totalPages,boolean first,boolean last){}
}
