/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonarsource.sonarlint.core;

import com.google.gson.Gson;
import java.util.Map;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ValidationResult;
import org.sonarsource.sonarlint.core.client.api.connected.WsHelper;
import org.sonarsource.sonarlint.core.client.api.exceptions.SonarLintWrappedException;
import org.sonarsource.sonarlint.core.client.api.exceptions.UnsupportedServerException;
import org.sonarsource.sonarlint.core.container.connected.CloseableWsResponse;
import org.sonarsource.sonarlint.core.container.connected.SonarLintWsClient;
import org.sonarsource.sonarlint.core.container.connected.validate.AuthenticationChecker;
import org.sonarsource.sonarlint.core.container.connected.validate.DefaultValidationResult;
import org.sonarsource.sonarlint.core.container.connected.validate.PluginVersionChecker;
import org.sonarsource.sonarlint.core.container.connected.validate.ServerVersionAndStatusChecker;

import static com.google.common.base.Preconditions.checkNotNull;

public class WsHelperImpl implements WsHelper {

  @Override
  public ValidationResult validateConnection(ServerConfiguration serverConfig) {
    SonarLintWsClient client = createClient(serverConfig);
    return validateConnection(new ServerVersionAndStatusChecker(client), new PluginVersionChecker(client), new AuthenticationChecker(client));
  }

  static ValidationResult validateConnection(ServerVersionAndStatusChecker serverChecker, PluginVersionChecker pluginsChecker, AuthenticationChecker authChecker) {
    try {
      serverChecker.checkVersionAndStatus();
      pluginsChecker.checkPlugins();
      return authChecker.validateCredentials();
    } catch (UnsupportedServerException e) {
      return new DefaultValidationResult(false, e.getMessage());
    } catch (RuntimeException e) {
      throw SonarLintWrappedException.wrap(e);
    }
  }

  private static SonarLintWsClient createClient(ServerConfiguration serverConfig) {
    checkNotNull(serverConfig);
    return new SonarLintWsClient(serverConfig);
  }

  static String generateAuthenticationToken(ServerVersionAndStatusChecker serverChecker, SonarLintWsClient client, String name, boolean force) {
    try {
      // in 5.3 login is mandatory and user needs admin privileges
      serverChecker.checkVersionAndStatus("5.4");

      if (force) {
        // revoke
        client.post("api/user_tokens/revoke?name=" + name);
      }

      // create
      CloseableWsResponse response = client.post("api/user_tokens/generate?name=" + name);
      Map<?, ?> javaRootMapObject = new Gson().fromJson(response.content(), Map.class);
      return (String) javaRootMapObject.get("token");
    } catch (RuntimeException e) {
      throw SonarLintWrappedException.wrap(e);
    }
  }

  @Override
  public String generateAuthenticationToken(ServerConfiguration serverConfig, String name, boolean force) {
    SonarLintWsClient client = createClient(serverConfig);
    return generateAuthenticationToken(new ServerVersionAndStatusChecker(client), client, name, force);
  }
}
