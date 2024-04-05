/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.client.api.connected;

import java.util.concurrent.CompletableFuture;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.authentication.AuthenticationChecker;
import org.sonarsource.sonarlint.core.serverapi.system.DefaultValidationResult;
import org.sonarsource.sonarlint.core.serverapi.system.ValidationResult;
import org.sonarsource.sonarlint.core.serverconnection.ServerVersionAndStatusChecker;

public class ConnectionValidator {
  private final ServerApiHelper helper;

  public ConnectionValidator(ServerApiHelper helper) {
    this.helper = helper;
  }

  public CompletableFuture<ValidationResult> validateConnection() {
    var serverChecker = new ServerVersionAndStatusChecker(new ServerApi(helper));
    var authChecker = new AuthenticationChecker(helper);
    return serverChecker.checkVersionAndStatusAsync()
      .thenApply(check -> {
        var validateCredentials = authChecker.validateCredentials();
        var organizationKey = helper.getOrganizationKey();
        if (validateCredentials.success() && organizationKey.isPresent()) {
          var organization = new ServerApi(helper).organization().getOrganization(organizationKey.get(),
            new ProgressMonitor(null));
          if (organization.isEmpty()) {
            return new DefaultValidationResult(false, "No organizations found for key: " + organizationKey.get());
          }
        }
        return validateCredentials;
      })
      .exceptionally(e -> new DefaultValidationResult(false, e.getCause().getMessage()));
  }

}
