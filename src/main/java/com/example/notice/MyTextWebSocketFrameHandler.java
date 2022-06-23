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

    // 从Spring容器中获取消息监听器容器,处理订阅消息sysNotice
    SimpleMessageListenerContainer messageListenerContainer = (SimpleMessageListenerContainer) SpringUtil.getApplicationContext()
            .getBean(SimpleMessageListenerContainer.class);

    RabbitTemplate rabbitTemplate = SpringUtil.getApplicationContext()
            .getBean(RabbitTemplate.class);

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
        RabbitAdmin rabbitAdmin = new RabbitAdmin(rabbitTemplate);
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

    /**
     * 当客户端连接服务端之后（打开连接）
     * 获取客户端的channle，并且放到ChannelGroup中去进行管理
     */
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        users.add(ctx.channel());
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {

        String channelId = ctx.channel().id().asShortText();
        System.out.println("客户端被移除，channelId为：" + channelId);

        // 当触发handlerRemoved，ChannelGroup会自动移除对应客户端的channel
        users.remove(ctx.channel());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        // 发生异常之后关闭连接（关闭channel），随后从ChannelGroup中移除
        ctx.channel().close();
        users.remove(ctx.channel());
    }
}
