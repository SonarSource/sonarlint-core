/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2017 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.model;

import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.client.api.connected.RemoteModule;
import org.sonarsource.sonarlint.core.proto.Sonarlint;

public class DefaultRemoteModule implements RemoteModule {
  private final String key;
  private final String name;
  private final boolean root;
  private final String organizationKey;
  private final String organizationName;

  public DefaultRemoteModule(Sonarlint.ModuleList.Module module, @Nullable String organizationName) {
    this.organizationKey = module.getOrgaKey();
    this.organizationName = organizationName;
    this.key = module.getKey();
    this.name = module.getName();
    this.root = "TRK".equals(module.getQu());
  }

  @Override
  public String getKey() {
    return key;
  }

  @Override
  public String getOrganizationKey() {
    return organizationKey;
  }

  @Override
  public String getOrganizationName() {
    return organizationName;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public boolean isRoot() {
    return root;
  }
}
