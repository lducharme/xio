package com.xjeffrose.xio.client.loadbalancer;

import com.xjeffrose.xio.SSL.XioSecurityHandlerImpl;
import com.xjeffrose.xio.client.retry.BoundedExponentialBackoffRetry;
import com.xjeffrose.xio.client.retry.RetryLoop;
import com.xjeffrose.xio.client.retry.TracerDriver;
import com.xjeffrose.xio.core.XioIdleDisconnectHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.log4j.Logger;

public class NodeHealthCheck {
  private static final Logger log = Logger.getLogger(NodeHealthCheck.class.getName());
  private final EpollEventLoopGroup epoolEventLoop;
  private final NioEventLoopGroup nioEventLoop;


  public NodeHealthCheck(int workerPoolSize) {
    if (Epoll.isAvailable()) {
      epoolEventLoop = new EpollEventLoopGroup(workerPoolSize);
      nioEventLoop = null;
    } else {
      epoolEventLoop = null;
      nioEventLoop = new NioEventLoopGroup(workerPoolSize);
    }
  }

  public void connect(Node node, Protocol proto, boolean ssl, ECV ecv) {

    ChannelInitializer<SocketChannel> pipeline = new ChannelInitializer<SocketChannel>() {
      @Override
      protected void initChannel(SocketChannel channel) throws Exception {
        ChannelPipeline cp = channel.pipeline();
        if (ssl) {
          cp.addLast("encryptionHandler", new XioSecurityHandlerImpl().getEncryptionHandler());
        }
        if (proto == (Protocol.HTTP)) {
          cp.addLast(new HttpClientCodec());
        }
        if (proto == (Protocol.HTTPS)) {
          cp.addLast(new HttpClientCodec());
        }
        cp.addLast(new XioIdleDisconnectHandler(60, 60, 60));
        cp.addLast(new NodeECV(node, proto, ecv));
      }
    };

    if (Epoll.isAvailable()) {
      connect(node, epoolEventLoop, pipeline);
    } else {
      connect(node, nioEventLoop, pipeline);
    }
  }

  private void connect(Node node, NioEventLoopGroup workerGroup, ChannelInitializer<SocketChannel> pipeline) {

    // Start the connection attempt.
    Bootstrap b = new Bootstrap();
    b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 500)
        .option(ChannelOption.SO_REUSEADDR, true)
        .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
        .option(ChannelOption.WRITE_BUFFER_HIGH_WATER_MARK, 32 * 1024)
        .option(ChannelOption.WRITE_BUFFER_LOW_WATER_MARK, 8 * 1024)
        .option(ChannelOption.TCP_NODELAY, true);

    b.group(workerGroup)
        .channel(NioServerSocketChannel.class)
        .handler(pipeline);

    BoundedExponentialBackoffRetry retry = new BoundedExponentialBackoffRetry(50, 500, 4);

    TracerDriver tracerDriver = new TracerDriver() {

      @Override
      public void addTrace(String name, long time, TimeUnit unit) {
      }

      @Override
      public void addCount(String name, int increment) {
      }
    };

    RetryLoop retryLoop = new RetryLoop(retry, new AtomicReference<>(tracerDriver));
    connect2(node, b, retryLoop);
  }

  private void connect(Node node, EpollEventLoopGroup workerGroup, ChannelInitializer<SocketChannel> pipeline) {

    // Start the connection attempt.
    Bootstrap b = new Bootstrap();
    b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 500)
        .option(ChannelOption.SO_REUSEADDR, true)
        .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
        .option(ChannelOption.WRITE_BUFFER_HIGH_WATER_MARK, 32 * 1024)
        .option(ChannelOption.WRITE_BUFFER_LOW_WATER_MARK, 8 * 1024)
        .option(ChannelOption.TCP_NODELAY, true);

    b.group(workerGroup)
        .channel(EpollServerSocketChannel.class)
        .handler(pipeline);

    BoundedExponentialBackoffRetry retry = new BoundedExponentialBackoffRetry(50, 500, 4);

    TracerDriver tracerDriver = new TracerDriver() {

      @Override
      public void addTrace(String name, long time, TimeUnit unit) {
      }

      @Override
      public void addCount(String name, int increment) {
      }
    };

    RetryLoop retryLoop = new RetryLoop(retry, new AtomicReference<>(tracerDriver));
    connect2(node, b, retryLoop);
  }

  private void connect2(Node node, Bootstrap bootstrap, RetryLoop retryLoop) {
    ChannelFutureListener listener = new ChannelFutureListener() {
      @Override
      public void operationComplete(ChannelFuture future) {
        if (!future.isSuccess()) {
          try {
            retryLoop.takeException((Exception) future.cause());
            log.error("==== Service connect failure (will retry) ", future.cause());
            connect2(node, bootstrap, retryLoop);
          } catch (Exception e) {
            log.error("==== Service connect failure ", future.cause());
            // Close the connection if the connection attempt has failed.
            node.setAvailable(false);
          }
        } else {
          log.info("Node connected: ");
        }
      }
    };

    ChannelFuture cf = bootstrap.connect(node.address()).addListener(listener);
  }
}
