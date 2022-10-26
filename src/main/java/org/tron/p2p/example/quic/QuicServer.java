package org.tron.p2p.example.quic;

import io.grpc.ExperimentalApi;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.incubator.codec.quic.*;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;
import org.tron.p2p.example.ExampleConstant;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

@Slf4j(topic = "net")
public class QuicServer {

  private QuicServer() { }

  public static void main(String[] args) throws Exception {
    SelfSignedCertificate selfSignedCertificate = new SelfSignedCertificate();
    QuicSslContext context = QuicSslContextBuilder.forServer(
        selfSignedCertificate.privateKey(), null, selfSignedCertificate.certificate())
        .applicationProtocols("http/0.9").build();
    NioEventLoopGroup group = new NioEventLoopGroup(1);
    ChannelHandler codec = new QuicServerCodecBuilder().sslContext(context)
        .maxIdleTimeout(50000, TimeUnit.MILLISECONDS)
        // Configure some limits for the maximal number of streams (and the data) that we want to handle.
        .initialMaxData(10000000)
        .initialMaxStreamDataBidirectionalLocal(1000000)
        .initialMaxStreamDataBidirectionalRemote(1000000)
        .initialMaxStreamsBidirectional(100)
        .initialMaxStreamsUnidirectional(100)

        // Setup a token handler. In a production system you would want to implement and provide your custom
        // one.
        .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
        // ChannelHandler that is added into QuicChannel pipeline.
        .handler(new ChannelInboundHandlerAdapter() {
          @Override
          public void channelActive(ChannelHandlerContext ctx) {
            QuicChannel channel = (QuicChannel) ctx.channel();
            log.info("QuicChannel active {}", channel.remoteAddress());
            // Create streams etc..
          }

          public void channelInactive(ChannelHandlerContext ctx) {
            ((QuicChannel) ctx.channel()).collectStats().addListener(f -> {
              if (f.isSuccess()) {
                log.info("Connection closed: {}", f.getNow());
              }
            });
          }

          @Override
          public boolean isSharable() {
            return true;
          }
        })
        .streamHandler(new ChannelInitializer<QuicStreamChannel>() {
          @Override
          protected void initChannel(QuicStreamChannel ch)  {
            // Add a LineBasedFrameDecoder here as we just want to do some simple HTTP 0.9 handling.
            ch.pipeline().addLast(new LineBasedFrameDecoder(256 * 1024))
                .addLast(new ChannelInboundHandlerAdapter() {
                  @Override
                  public void channelRead(ChannelHandlerContext ctx, Object msg) {
                    ByteBuf byteBuf = (ByteBuf) msg;
                    try {
                      if (byteBuf.toString(CharsetUtil.US_ASCII).trim().equals("GET /")) {
                        ByteBuf buffer = ctx.alloc().directBuffer();
                        String sendMsg = ExampleConstant.EXAMPLE_MSG + ExampleConstant.EXAMPLE_MSG
                            + ExampleConstant.EXAMPLE_MSG + '\n';
                        buffer.writeCharSequence(sendMsg, CharsetUtil.US_ASCII);
                        log.info("msg size {}", buffer.readableBytes());
                        // Write the buffer and shutdown the output by writing a FIN.
                        log.info("send time {}", System.currentTimeMillis());
                        ctx.writeAndFlush(buffer);//.addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
                      }
                    } finally {
                      byteBuf.release();
                    }
                  }
                });
          }
        }).build();
    try {
      Bootstrap bs = new Bootstrap();
      Channel channel = bs.group(group)
          .channel(NioDatagramChannel.class)
          .handler(codec)
          .bind(new InetSocketAddress(9999)).sync().channel();
      log.info("Quic server started, channel {}:{}", channel.remoteAddress(), 9999);
      channel.closeFuture().sync();
    } finally {
      group.shutdownGracefully();
    }
  }
}
