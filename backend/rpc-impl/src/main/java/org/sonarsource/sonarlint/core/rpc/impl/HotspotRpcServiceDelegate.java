/*
 * SonarLint Core - RPC Implementation
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
package org.sonarsource.sonarlint.core.rpc.impl;

import java.util.concurrent.CompletableFuture;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.hotspot.HotspotService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.ChangeHotspotStatusParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.CheckLocalDetectionSupportedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.CheckLocalDetectionSupportedResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.CheckStatusChangePermittedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.CheckStatusChangePermittedResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.OpenHotspotInBrowserParams;

class HotspotRpcServiceDelegate extends AbstractRpcServiceDelegate implements HotspotRpcService {

  public HotspotRpcServiceDelegate(SonarLintRpcServerImpl server) {
    super(server);
  }

  @Override
  public void openHotspotInBrowser(OpenHotspotInBrowserParams params) {
    notify(() -> getBean(HotspotService.class).openHotspotInBrowser(params.getConfigScopeId(), params.getBranch(), params.getHotspotKey()), params.getConfigScopeId());
  }

  @Override
  public CompletableFuture<CheckLocalDetectionSupportedResponse> checkLocalDetectionSupported(CheckLocalDetectionSupportedParams params) {
    return requestAsync(cancelChecker -> getBean(HotspotService.class).checkLocalDetectionSupported(params.getConfigScopeId()), params.getConfigScopeId());
  }

  @Override
  public CompletableFuture<CheckStatusChangePermittedResponse> checkStatusChangePermitted(CheckStatusChangePermittedParams params) {
    return requestAsync(cancelChecker -> getBean(HotspotService.class).checkStatusChangePermitted(params.getConnectionId(), params.getHotspotKey(), cancelChecker));
  }

  @Override
  public CompletableFuture<Void> changeStatus(ChangeHotspotStatusParams params) {
    return runAsync(
      cancelChecker -> getBean(HotspotService.class).changeStatus(params.getConfigurationScopeId(), params.getHotspotKey(), adapt(params.getNewStatus()), cancelChecker),
      params.getConfigurationScopeId());
  }

  private static HotspotReviewStatus adapt(HotspotStatus newStatus) {
    return HotspotReviewStatus.valueOf(newStatus.name());
  }
}
