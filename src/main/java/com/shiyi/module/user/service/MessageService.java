package com.shiyi.module.user.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shiyi.module.user.entity.Message;
import com.shiyi.module.user.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageMapper messageMapper;

    /** 站内信:异步链路的最后一公里 */
    public void send(Long userId, String title, String content) {
        Message message = new Message();
        message.setUserId(userId);
        message.setTitle(title);
        message.setContent(content);
        messageMapper.insert(message);
    }

    /** 我的站内信(最新 50 条) */
    public List<Message> listMine() {
        long userId = StpUtil.getLoginIdAsLong();
        return messageMapper.selectList(Wrappers.<Message>lambdaQuery()
                .eq(Message::getUserId, userId)
                .orderByDesc(Message::getCreateTime)
                .last("LIMIT 50"));
    }

    /** 标记已读:属主条件内,不能替别人已读 */
    public void markRead(Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        messageMapper.update(null, Wrappers.<Message>lambdaUpdate()
                .eq(Message::getId, id)
                .eq(Message::getUserId, userId)
                .eq(Message::getIsRead, 0)
                .set(Message::getIsRead, 1));
    }
}
