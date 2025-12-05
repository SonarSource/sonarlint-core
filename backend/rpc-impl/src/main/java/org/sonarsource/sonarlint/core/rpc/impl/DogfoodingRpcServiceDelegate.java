/*
 * SonarLint Core - RPC Implementation
 * Copyright (C) 2016-2025 SonarSource Sàrl
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
import org.sonarsource.sonarlint.core.commons.dogfood.DogfoodEnvironmentDetectionService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.dogfooding.DogfoodingRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.dogfooding.IsDogfoodingEnvironmentResponse;

public class DogfoodingRpcServiceDelegate extends AbstractRpcServiceDelegate implements DogfoodingRpcService {
  private final DogfoodEnvironmentDetectionService dogfoodEnvironmentDetectionService;
  public DogfoodingRpcServiceDelegate(SonarLintRpcServerImpl sonarLintRpcServer) {
    super(sonarLintRpcServer);
    this.dogfoodEnvironmentDetectionService = new DogfoodEnvironmentDetectionService();
  }

  @Override
  public CompletableFuture<IsDogfoodingEnvironmentResponse> isDogfoodingEnvironment() {
    return requestAsync(cancelMonitor -> new IsDogfoodingEnvironmentResponse(dogfoodEnvironmentDetectionService.isDogfoodEnvironment()));
  }
}
