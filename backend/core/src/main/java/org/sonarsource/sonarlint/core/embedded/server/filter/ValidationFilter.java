/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2025 SonarSource SA
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
package org.sonarsource.sonarlint.core.embedded.server.filter;

import java.io.IOException;
import org.apache.commons.lang3.Strings;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpFilterChain;
import org.apache.hc.core5.http.io.HttpFilterHandler;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.sonarsource.sonarlint.core.SonarCloudActiveEnvironment;
import org.sonarsource.sonarlint.core.SonarCloudRegion;
import org.sonarsource.sonarlint.core.embedded.server.AttributeUtils;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcClient;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.MessageType;
import org.sonarsource.sonarlint.core.rpc.protocol.client.message.ShowMessageParams;

import static org.apache.hc.core5.http.io.HttpFilterChain.ResponseTrigger;

public class ValidationFilter implements HttpFilterHandler {

  private final SonarLintRpcClient client;
  private final SonarCloudActiveEnvironment sonarCloudActiveEnvironment;

  public ValidationFilter(SonarLintRpcClient client, SonarCloudActiveEnvironment sonarCloudActiveEnvironment) {
    this.client = client;
    this.sonarCloudActiveEnvironment = sonarCloudActiveEnvironment;
  }

  @Override
  public void handle(ClassicHttpRequest request, ResponseTrigger responseTrigger, HttpContext context, HttpFilterChain chain) throws HttpException, IOException {
    var origin = AttributeUtils.getOrigin(context);
    boolean isSonarCloud = sonarCloudActiveEnvironment.isSonarQubeCloud(origin);
    var params = AttributeUtils.getParams(context);
    if (!isSonarCloud && params.containsKey("server")) {
      var serverUrl = params.get("server");
      if (Strings.CI.startsWithAny(serverUrl, SonarCloudRegion.CLOUD_URLS)) {
        var response = new BasicClassicHttpResponse(HttpStatus.SC_BAD_REQUEST);
        client.showMessage(new ShowMessageParams(MessageType.ERROR,
          "Invalid request to SonarQube backend. " +
            "The 'server' parameter should not be SonarQube Cloud URL, use it only to specify URL of a SonarQube Server."));
        responseTrigger.submitResponse(response);
      }
    }
    chain.proceed(request, responseTrigger, context);
  }

}
