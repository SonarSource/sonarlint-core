/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2018 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarsource.sonarlint.core.container.connected;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.sonar.api.CoreProperties;
import org.sonar.api.SonarRuntime;
import org.sonar.api.config.Settings;
import org.sonar.api.platform.Server;
import org.sonar.api.utils.log.Loggers;

public class DefaultServer extends Server {

  private Settings settings;
  private SonarRuntime runtime;

  public DefaultServer(Settings settings, SonarRuntime runtime) {
    this.settings = settings;
    this.runtime = runtime;
  }

  @Override
  public String getId() {
    return settings.getString(CoreProperties.SERVER_ID);
  }

  @Override
  public String getVersion() {
    return runtime.getApiVersion().toString();
  }

  @Override
  public Date getStartedAt() {
    String dateString = settings.getString(CoreProperties.SERVER_STARTTIME);
    if (dateString != null) {
      try {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").parse(dateString);

      } catch (ParseException e) {
        Loggers.get(getClass()).error("The property " + CoreProperties.SERVER_STARTTIME + " is badly formatted.", e);
      }
    }
    return null;
  }

  @Override
  public File getRootDir() {
    return null;
  }

  @Override
  public String getContextPath() {
    return null;
  }

  @Override
  public String getURL() {
    return null;
  }

  @Override
  public boolean isSecured() {
    return false;
  }

  @Override
  public boolean isDev() {
    return false;
  }

  @Override
  public String getPublicRootUrl() {
    return null;
  }

  @Override
  public String getPermanentServerId() {
    return settings.getString(CoreProperties.PERMANENT_SERVER_ID);
  }
}
