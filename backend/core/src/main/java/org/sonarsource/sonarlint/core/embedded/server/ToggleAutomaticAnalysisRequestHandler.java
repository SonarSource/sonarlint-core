/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.embedded.server;

import com.google.gson.Gson;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.net.URIBuilder;
import org.sonarsource.sonarlint.core.analysis.AnalysisService;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;

public class ToggleAutomaticAnalysisRequestHandler implements HttpRequestHandler {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private final AnalysisService analysisService;
  private final Gson gson = new Gson();

  public ToggleAutomaticAnalysisRequestHandler(AnalysisService analysisService) {
    this.analysisService = analysisService;
  }

  @Override
  public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context) throws HttpException, IOException {
    LOG.debug("Received request for toggling automatic analysis");

    if (!Method.POST.isSame(request.getMethod())) {
      response.setCode(HttpStatus.SC_BAD_REQUEST);
      return;
    }

    var params = new HashMap<String, String>();
    try {
      new URIBuilder(request.getUri(), StandardCharsets.UTF_8)
        .getQueryParams()
        .forEach(p -> params.put(p.getName(), p.getValue()));
    } catch (URISyntaxException e) {
      handleError(response,  "Invalid URI");
      return;
    }

    var enabledParam = params.get("enabled");
    if (enabledParam == null) {
      handleError(response, "Missing 'enabled' query parameter");
      return;
    }

    boolean enabled;
    try {
      enabled = Boolean.parseBoolean(enabledParam);
    } catch (Exception e) {
      handleError(response, "Invalid 'enabled' parameter value");
      return;
    }

    try {
      analysisService.didChangeAutomaticAnalysisSetting(enabled);
      response.setCode(HttpStatus.SC_OK);
    } catch (Exception e) {
      LOG.error("Failed to toggle automatic analysis", e);
      response.setCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
      var errorResponse = new ErrorMessage("Failed to toggle automatic analysis: " + e.getMessage());
      response.setEntity(new StringEntity(gson.toJson(errorResponse), ContentType.APPLICATION_JSON));
    }
  }

  private void handleError(ClassicHttpResponse response, String clientMessage) {
    response.setCode(HttpStatus.SC_BAD_REQUEST);
    var errorResponse = new ErrorMessage(clientMessage);
    response.setEntity(new StringEntity(gson.toJson(errorResponse), ContentType.APPLICATION_JSON));
  }

  public record ErrorMessage(String message) {
  }

}
