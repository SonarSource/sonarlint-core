/*
 * SonarLint Core - Server API
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
package org.sonarsource.sonarlint.core.serverapi.usertokens;

import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;

import static org.sonarsource.sonarlint.core.http.HttpClient.FORM_URL_ENCODED_CONTENT_TYPE;
import static org.sonarsource.sonarlint.core.serverapi.UrlUtils.urlEncode;

/**
 * For the /api/user_tokens endpoint of SonarQube / SonarCloud. When adding more methods, please ensure the protobuf
 * files for "ws-user_tokens.proto" are present and configured correctly regarding the "java_package" preference!
 */
public class UserTokensApi {
  private final ServerApiHelper helper;

  public UserTokensApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public void revoke(String tokenName, SonarLintCancelMonitor cancelMonitor) {
    var body = "name=" + urlEncode(tokenName);
    helper.post("/api/user_tokens/revoke", FORM_URL_ENCODED_CONTENT_TYPE, body, cancelMonitor);
  }
}
