/*
 * SonarLint Core - Implementation
 * Copyright (C) SonarSource Sàrl
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

import org.sonarsource.sonarlint.core.embedded.server.handler.GeneratedUserTokenHandler;
import org.sonarsource.sonarlint.core.embedded.server.handler.ShowFixSuggestionRequestHandler;
import org.sonarsource.sonarlint.core.embedded.server.handler.ShowHotspotRequestHandler;
import org.sonarsource.sonarlint.core.embedded.server.handler.ShowIssueRequestHandler;
import org.sonarsource.sonarlint.core.embedded.server.handler.StatusRequestHandler;

/**
 * Groups all request handlers used by the embedded server.
 */
public class RequestHandlers {

  private final StatusRequestHandler statusRequestHandler;
  private final GeneratedUserTokenHandler generatedUserTokenHandler;
  private final ShowHotspotRequestHandler showHotspotRequestHandler;
  private final ShowIssueRequestHandler showIssueRequestHandler;
  private final ShowFixSuggestionRequestHandler showFixSuggestionRequestHandler;
  private final ToggleAutomaticAnalysisRequestHandler toggleAutomaticAnalysisRequestHandler;
  private final AnalyzeFileListRequestHandler analyzeFileListRequestHandler;

  public RequestHandlers(StatusRequestHandler statusRequestHandler, GeneratedUserTokenHandler generatedUserTokenHandler,
    ShowHotspotRequestHandler showHotspotRequestHandler, ShowIssueRequestHandler showIssueRequestHandler,
    ShowFixSuggestionRequestHandler showFixSuggestionRequestHandler,
    ToggleAutomaticAnalysisRequestHandler toggleAutomaticAnalysisRequestHandler,
    AnalyzeFileListRequestHandler analyzeFileListRequestHandler) {
    this.statusRequestHandler = statusRequestHandler;
    this.generatedUserTokenHandler = generatedUserTokenHandler;
    this.showHotspotRequestHandler = showHotspotRequestHandler;
    this.showIssueRequestHandler = showIssueRequestHandler;
    this.showFixSuggestionRequestHandler = showFixSuggestionRequestHandler;
    this.toggleAutomaticAnalysisRequestHandler = toggleAutomaticAnalysisRequestHandler;
    this.analyzeFileListRequestHandler = analyzeFileListRequestHandler;
  }

  public StatusRequestHandler getStatusRequestHandler() {
    return statusRequestHandler;
  }

  public GeneratedUserTokenHandler getGeneratedUserTokenHandler() {
    return generatedUserTokenHandler;
  }

  public ShowHotspotRequestHandler getShowHotspotRequestHandler() {
    return showHotspotRequestHandler;
  }

  public ShowIssueRequestHandler getShowIssueRequestHandler() {
    return showIssueRequestHandler;
  }

  public ShowFixSuggestionRequestHandler getShowFixSuggestionRequestHandler() {
    return showFixSuggestionRequestHandler;
  }

  public ToggleAutomaticAnalysisRequestHandler getToggleAutomaticAnalysisRequestHandler() {
    return toggleAutomaticAnalysisRequestHandler;
  }

  public AnalyzeFileListRequestHandler getAnalyzeFileListRequestHandler() {
    return analyzeFileListRequestHandler;
  }
}
