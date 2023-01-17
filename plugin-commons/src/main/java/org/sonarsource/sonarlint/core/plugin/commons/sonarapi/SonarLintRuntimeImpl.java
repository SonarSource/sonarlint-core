/*
 * SonarLint Core - Plugin Commons
 * Copyright (C) 2016-2023 SonarSource SA
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
package org.sonarsource.sonarlint.core.plugin.commons.sonarapi;

import org.sonar.api.SonarEdition;
import org.sonar.api.SonarProduct;
import org.sonar.api.SonarQubeSide;
import org.sonar.api.utils.Version;
import org.sonarsource.sonarlint.plugin.api.SonarLintRuntime;

import static java.util.Objects.requireNonNull;

public class SonarLintRuntimeImpl implements SonarLintRuntime {

  private final Version sonarPluginApiVersion;
  private final Version sonarLintPluginApiVersion;
  private final long clientPid;

  public SonarLintRuntimeImpl(Version sonarPluginApiVersion, Version sonarLintPluginApiVersion, long clientPid) {
    this.clientPid = clientPid;
    this.sonarPluginApiVersion = requireNonNull(sonarPluginApiVersion);
    this.sonarLintPluginApiVersion = sonarLintPluginApiVersion;
  }

  @Override
  public Version getApiVersion() {
    return sonarPluginApiVersion;
  }

  @Override
  public Version getSonarLintPluginApiVersion() {
    return sonarLintPluginApiVersion;
  }

  @Override
  public SonarProduct getProduct() {
    return SonarProduct.SONARLINT;
  }

  @Override
  public SonarQubeSide getSonarQubeSide() {
    throw new UnsupportedOperationException("Can only be called in SonarQube");
  }

  @Override
  public SonarEdition getEdition() {
    throw new UnsupportedOperationException("Can only be called in SonarQube");
  }

  @Override
  public long getClientPid() {
    return clientPid;
  }

}
