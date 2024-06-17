/*
 * SonarLint Core - RPC Implementation
 * Copyright (C) 2016-2024 SonarSource SA
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
import org.sonarsource.sonarlint.core.grip.GripService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.grip.GripRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.grip.ProvideFeedbackParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.grip.SuggestFixParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.grip.SuggestFixResponse;

class GripRpcServiceDelegate extends AbstractRpcServiceDelegate implements GripRpcService {

  public GripRpcServiceDelegate(SonarLintRpcServerImpl server) {
    super(server);
  }

  @Override
  public CompletableFuture<SuggestFixResponse> suggestFix(SuggestFixParams params) {
    return requestAsync(cancelChecker -> getBean(GripService.class).suggestFix(params));
  }

  @Override
  public CompletableFuture<Void> provideFeedback(ProvideFeedbackParams params) {
    return runAsync(cancelChecker -> getBean(GripService.class).provideFeedback(params), null);
  }
}
