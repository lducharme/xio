package com.xjeffrose.xio.config;

import com.google.common.collect.ImmutableSet;
import com.xjeffrose.xio.marshall.Marshallable;
import com.xjeffrose.xio.marshall.Marshaller;
import com.xjeffrose.xio.marshall.Unmarshaller;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode
public final class IpAddressDeterministicRuleEngineConfig implements Marshallable {

  private final Set<InetAddress> blacklistIps = new HashSet<>();
  private final Set<InetAddress> whitelistIps = new HashSet<>();

  public IpAddressDeterministicRuleEngineConfig() {}

  public void blacklistIp(InetAddress address) {
    blacklistIps.add(address);
    if (whitelistIps.contains(address)) {
      whitelistIps.remove(address);
    }
  }

  public void whitelistIp(InetAddress address) {
    whitelistIps.add(address);
    if (blacklistIps.contains(address)) {
      blacklistIps.remove(address);
    }
  }

  public void remove(InetAddress address) {
    if (blacklistIps.contains(address)) {
      blacklistIps.remove(address);
    }
    if (whitelistIps.contains(address)) {
      whitelistIps.remove(address);
    }
  }

  public ImmutableSet<InetAddress> getBlacklistIps() {
    return ImmutableSet.copyOf(blacklistIps);
  }

  public ImmutableSet<InetAddress> getWhitelistIps() {
    return ImmutableSet.copyOf(whitelistIps);
  }

  public String keyName() {
    return "IpAddressDeterministicRuleEngineConfig";
  }

  public byte[] getBytes(Marshaller marshaller) {
    return marshaller.marshall(this);
  }

  public void putBytes(Unmarshaller unmarshaller, byte[] data) {
    unmarshaller.unmarshall(this, data);
  }

  public void clear() {
    blacklistIps.clear();
    whitelistIps.clear();
  }

  public long size() {
    return blacklistIps.size() + whitelistIps.size();
  }
}
