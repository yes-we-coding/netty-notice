package com.example.notice;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.concurrent.GlobalEventExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Properties;

/**
 *
 * @Description: 处理消息的handler
 * TextWebSocketFrame： 在netty中，是用于为websocket专门处理文本的对象，frame是消息的载体
 */
public class MyTextWebSocketFrameHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    // 用于记录和管理所有客户端的channle
    public static ChannelGroup users =
            new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    //获取消息监听器容器
    @Autowired
    private SimpleMessageListenerContainer messageListenerContainer;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    private static ObjectMapper MAPPER = new ObjectMapper();

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, TextWebSocketFrame textWebSocketFrame) throws Exception {
        //用户上线时发送自己的id
        String json = textWebSocketFrame.text();
        //解析json数据，获取用户id
        String userId = MAPPER.readTree(json).get("userId").asText();

        //第一次请求的时候，需要建立WebSocket连接
        Channel channel = UserChannelRel.get(userId);
        if (channel == null) {
            //获取WebSocket的连接
            channel = channelHandlerContext.channel();

            //把连接放到容器中
            UserChannelRel.put(userId, channel);
        }

        //只用完成新消息的提醒即可，只需要获取消息的数量
        //获取RabbitMQ的消息内容，并发送给用户
        //拼接获取队列名称
        String queueName = "notice_" + userId;
        //获取Rabbit的Properties容器
        Properties queueProperties = rabbitAdmin.getQueueProperties(queueName);

        //获取消息数量
        int num = 0;
        //判断Properties是否不为空
        if (queueProperties != null) {
            // 如果不为空，获取消息的数量
            num = (int) queueProperties.get("NOTICE_NUM");
        }

        //封装返回的数据
        HashMap map = new HashMap();
        map.put("noticeNum", num);
        Result result = ResultGenerator.genSuccessResult(map);

        //把数据发送给用户
        channel.writeAndFlush(new TextWebSocketFrame(MAPPER.writeValueAsString(result)));

        //把消息从队列里面清空，否则MQ消息监听器会再次消费一次
        if (num > 0) {
            rabbitAdmin.purgeQueue(queueName, true);
        }

        //为用户的消息通知队列注册监听器，便于用户在线的时候，
        //一旦有消息，可以主动推送给用户，不需要用户请求服务器获取数据
        messageListenerContainer.addQueueNames(queueName);
    }
}
