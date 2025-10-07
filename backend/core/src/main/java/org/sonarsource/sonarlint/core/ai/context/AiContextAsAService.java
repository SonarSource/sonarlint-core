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
package org.sonarsource.sonarlint.core.ai.context;

import com.google.gson.Gson;
import org.sonarsource.sonarlint.core.ai.context.api.IndexRequestBody;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.fs.FileSystemInitialized;
import org.sonarsource.sonarlint.core.fs.FileSystemUpdatedEvent;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.http.HttpClientProvider;
import org.springframework.context.event.EventListener;

public class AiContextAsAService {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private static final String CONTEXT_SERVER_URL = "http://localhost:8080";
  private static final String INDEX_API_PATH = "/index";
  private final HttpClientProvider httpClientProvider;

  public AiContextAsAService(HttpClientProvider httpClientProvider) {
    this.httpClientProvider = httpClientProvider;
  }

  @EventListener
  public void onFileSystemInitialized(FileSystemInitialized event) {
    var requestBody = new IndexRequestBody(
      event.files().stream()
        .map(f -> {
          var detectedLanguage = f.getDetectedLanguage();
          var language = detectedLanguage == null ? null : detectedLanguage.name();
          return new IndexRequestBody.File(f.getContent(), f.getIdeRelativePath().toString(), new IndexRequestBody.File.Metadata(language));
        }).toList());
    var body = new Gson().toJson(requestBody);
    httpClientProvider.getHttpClient()
      .postAsync(CONTEXT_SERVER_URL + INDEX_API_PATH, HttpClient.JSON_CONTENT_TYPE, body)
      .exceptionally(error -> {
        LOG.error("Error when sending the index request", error);
        return null;
      });
  }

  @EventListener
  public void onFileSystemUpdated(FileSystemUpdatedEvent event) {
    // TODO
  }
}
