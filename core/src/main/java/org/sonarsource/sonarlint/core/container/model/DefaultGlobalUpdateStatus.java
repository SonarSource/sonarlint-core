package org.sonarsource.sonarlint.core.container.model;

import java.util.Date;

import org.sonarsource.sonarlint.core.client.api.connected.GlobalUpdateStatus;

public class DefaultGlobalUpdateStatus implements GlobalUpdateStatus {
  private final String serverVersion;
  private final Date lastUpdate;
  private final boolean stale;

  public DefaultGlobalUpdateStatus(String serverVersion, Date lastUpdate, boolean stale) {
    this.serverVersion = serverVersion;
    this.lastUpdate = lastUpdate;
    this.stale = stale;

  }

  @Override
  public String getServerVersion() {
    return serverVersion;
  }

  @Override
  public Date getLastUpdateDate() {
    return lastUpdate;
  }

  @Override
  public boolean isStale() {
    return stale;
  }
}
