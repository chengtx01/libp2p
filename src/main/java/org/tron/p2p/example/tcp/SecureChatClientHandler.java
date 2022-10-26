package org.tron.p2p.example.tcp;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j(topic = "net")
public class SecureChatClientHandler extends SimpleChannelInboundHandler<String> {
  @Override
  public void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
    //System.err.println(msg);
    log.info("rcv time {}", System.currentTimeMillis());
    log.info("rcv msg size {}", msg.length());
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    cause.printStackTrace();
    ctx.close();
  }
}
