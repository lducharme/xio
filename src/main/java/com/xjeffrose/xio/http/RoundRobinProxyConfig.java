package com.xjeffrose.xio.http;

import com.google.common.collect.ImmutableList;
import com.typesafe.config.Config;
import com.xjeffrose.xio.client.ClientConfig;
import com.xjeffrose.xio.client.XioClientBootstrap;
import com.xjeffrose.xio.server.Route;
import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.http.HttpRequest;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RoundRobinProxyConfig {

  public static class Host {

    public final InetSocketAddress address;
    public final String hostHeader;
    public final boolean needSSL;

    public Host(InetSocketAddress address, String hostHeader, boolean needSSL) {
      this.address = address;
      this.hostHeader = hostHeader;
      this.needSSL = needSSL;
    }
  }

  private final AtomicInteger next = new AtomicInteger();
  private final ImmutableList<Host> hosts;
  private final ClientConfig clientConfig;
  private final Function<Boolean, ChannelHandler> tracingHandler;

  public RoundRobinProxyConfig(
      ImmutableList<Host> hosts,
      ClientConfig clientConfig,
      Function<Boolean, ChannelHandler> tracingHandler) {
    this.hosts = hosts;
    this.clientConfig = clientConfig;
    this.tracingHandler = tracingHandler;
  }

  private static ImmutableList<Host> parse(Config config) {
    List<Host> hosts =
        config
            .root()
            .entrySet()
            .stream()
            .map(
                (item) -> {
                  Config entry = config.getConfig(item.getKey());
                  InetSocketAddress address =
                      new InetSocketAddress(entry.getString("host"), entry.getInt("port"));
                  String hostHeader = entry.getString("hostHeader");
                  boolean needSSL = entry.getBoolean("needSSL");

                  return new Host(address, hostHeader, needSSL);
                })
            .collect(Collectors.toList());

    return ImmutableList.copyOf(hosts);
  }

  public RoundRobinProxyConfig(
      Config config, ClientConfig clientConfig, Function<Boolean, ChannelHandler> tracingHandler) {
    this(parse(config.getConfig("proxy.hosts")), clientConfig, tracingHandler);
  }

  public static RoundRobinProxyConfig fromConfig(
      String key,
      Config config,
      ClientConfig clientConfig,
      Function<Boolean, ChannelHandler> tracingHandler) {
    return new RoundRobinProxyConfig(config.getConfig(key), clientConfig, tracingHandler);
  }

  XioClientBootstrap newClient(boolean tls) {
    return new XioClientBootstrap(clientConfig).tracingHandler(() -> tracingHandler.apply(tls));
  }

  public RequestHandler getRouteProvider(HttpRequest request) {
    int idx = next.getAndIncrement();
    Host host = hosts.get(idx % hosts.size());

    ProxyConfig proxyConfig =
        new ProxyConfig(host.address, host.hostHeader, "", "/", host.needSSL, false);
    return new SimpleProxyHandler(Route.build("/:*path"), proxyConfig, newClient(host.needSSL));
  }
}
