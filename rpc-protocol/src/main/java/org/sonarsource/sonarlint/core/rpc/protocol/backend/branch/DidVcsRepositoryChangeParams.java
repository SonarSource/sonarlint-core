/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.branch;

public class DidVcsRepositoryChangeParams {
  private final String configurationScopeId;

<<<<<<<< HEAD:rpc-protocol/src/main/java/org/sonarsource/sonarlint/core/rpc/protocol/backend/branch/DidVcsRepositoryChangeParams.java
  public DidVcsRepositoryChangeParams(String configurationScopeId) {
    this.configurationScopeId = configurationScopeId;
  }
========
@JsonSegment("branch")
public interface SonarProjectBranchRpcService {
>>>>>>>> 3f040ee2a (Rework the use of completable futures):rpc-protocol/src/main/java/org/sonarsource/sonarlint/core/rpc/protocol/backend/branch/SonarProjectBranchRpcService.java

  public String getConfigurationScopeId() {
    return configurationScopeId;
  }
}
