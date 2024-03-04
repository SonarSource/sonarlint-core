/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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
package org.sonarsource.sonarlint.core.client.api.connected;

import java.util.Optional;
import org.sonarsource.sonarlint.core.client.api.exceptions.SonarLintWrappedException;
import org.sonarsource.sonarlint.core.client.api.exceptions.UnsupportedServerException;
import org.sonarsource.sonarlint.core.container.connected.validate.AuthenticationChecker;
import org.sonarsource.sonarlint.core.container.connected.validate.DefaultValidationResult;
import org.sonarsource.sonarlint.core.container.connected.validate.ServerVersionAndStatusChecker;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.organization.ServerOrganization;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

public class ConnectionValidator {
  private final ServerApiHelper helper;

  public ConnectionValidator(ServerApiHelper helper) {
    this.helper = helper;
  }

  public ValidationResult validateConnection() {
    ServerVersionAndStatusChecker serverChecker = new ServerVersionAndStatusChecker(helper);
    AuthenticationChecker authChecker = new AuthenticationChecker(helper);
    try {
      serverChecker.checkVersionAndStatus();
      ValidationResult validateCredentials = authChecker.validateCredentials();
      Optional<String> organizationKey = helper.getOrganizationKey();
      if (validateCredentials.success() && organizationKey.isPresent()) {
        Optional<ServerOrganization> organization = new ServerApi(helper).organization().fetchOrganization(organizationKey.get(), new ProgressWrapper(null));
        if (!organization.isPresent()) {
          return new DefaultValidationResult(false, "No organizations found for key: " + organizationKey.get());
        }
      }
      return validateCredentials;
    } catch (UnsupportedServerException e) {
      return new DefaultValidationResult(false, e.getMessage());
    } catch (RuntimeException e) {
      throw SonarLintWrappedException.wrap(e);
    }
  }

}
