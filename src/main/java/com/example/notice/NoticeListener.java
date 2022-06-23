package com.example.notice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;

import java.util.HashMap;
import java.util.Properties;

public class NoticeListener implements ChannelAwareMessageListener {

    RabbitTemplate rabbitTemplate = SpringUtil.getApplicationContext()
            .getBean(RabbitTemplate.class);

    private static ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void onMessage(Message message, Channel channel) throws Exception {
        //获取用户id，可以通过队列名称获取
        String queueName = message.getMessageProperties().getConsumerQueue();
        String userId = queueName.substring(queueName.lastIndexOf("_") + 1);

        io.netty.channel.Channel wsChannel = UserChannelRel.get(userId);

        //判断用户是否在线
        if (wsChannel != null) {

            RabbitAdmin rabbitAdmin = new RabbitAdmin(rabbitTemplate);
            Properties queueProperties = rabbitAdmin.getQueueProperties(queueName);

            int num = 0;

            if (queueProperties != null) {
                num = (int) queueProperties.get("NOTICE_NUM");
            }
            //如果连接不为空，表示用户在线
            //封装返回数据
            HashMap map = new HashMap();
            map.put("noticeNum", num);
            Result result = ResultGenerator.genSuccessResult(map);

            // 把数据通过WebSocket连接主动推送用户
            wsChannel.writeAndFlush(new TextWebSocketFrame(MAPPER.writeValueAsString(result)));
        }
    }

}
