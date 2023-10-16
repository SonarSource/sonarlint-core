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
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import org.sonarsource.sonarlint.core.hotspot.HotspotService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.ChangeHotspotStatusParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.CheckLocalDetectionSupportedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.CheckLocalDetectionSupportedResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.CheckStatusChangePermittedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.CheckStatusChangePermittedResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.OpenHotspotInBrowserParams;
import org.springframework.beans.factory.BeanFactory;

class HotspotRpcServiceDelegate extends AbstractRpcServiceDelegate implements HotspotRpcService {

  public HotspotRpcServiceDelegate(Supplier<BeanFactory> beanFactory, ExecutorService requestsExecutor, ExecutorService notificationsExecutor) {
    super(beanFactory, requestsExecutor, notificationsExecutor);
  }


  @Override
  public void openHotspotInBrowser(OpenHotspotInBrowserParams params) {
    notify(() -> getBean(HotspotService.class).openHotspotInBrowser(params));
  }

  @Override
  public CompletableFuture<CheckLocalDetectionSupportedResponse> checkLocalDetectionSupported(CheckLocalDetectionSupportedParams params) {
    return requestAsync(cancelChecker -> getBean(HotspotService.class).checkLocalDetectionSupported(params, cancelChecker));
  }

  @Override
  public CompletableFuture<CheckStatusChangePermittedResponse> checkStatusChangePermitted(CheckStatusChangePermittedParams params) {
    return requestAsync(cancelChecker -> getBean(HotspotService.class).checkStatusChangePermitted(params, cancelChecker));
  }

  @Override
  public CompletableFuture<Void> changeStatus(ChangeHotspotStatusParams params) {
    return runAsync(cancelChecker -> getBean(HotspotService.class).changeStatus(params, cancelChecker));
  }
}
