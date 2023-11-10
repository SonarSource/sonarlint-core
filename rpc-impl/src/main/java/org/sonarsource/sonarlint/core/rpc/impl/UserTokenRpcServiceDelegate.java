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
import org.sonarsource.sonarlint.core.rpc.protocol.backend.usertoken.RevokeTokenParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.usertoken.UserTokenRpcService;
import org.sonarsource.sonarlint.core.usertoken.UserTokenService;
import org.springframework.beans.factory.BeanFactory;

public class UserTokenRpcServiceDelegate extends AbstractRpcServiceDelegate implements UserTokenRpcService {

  public UserTokenRpcServiceDelegate(Supplier<BeanFactory> beanFactory, ExecutorService requestsExecutor, ExecutorService notificationsExecutor) {
    super(beanFactory, requestsExecutor, notificationsExecutor);
  }

  @Override
  public CompletableFuture<Void> revokeToken(RevokeTokenParams params) {
    return requestAsync(cancelChecker -> {
      getBean(UserTokenService.class).revokeToken(params);
      return null;
    });
  }

}
