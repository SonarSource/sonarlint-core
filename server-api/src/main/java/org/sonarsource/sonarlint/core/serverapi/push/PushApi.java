/*
 * SonarLint Core - Server API
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
package org.sonarsource.sonarlint.core.serverapi.push;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.push.ServerEvent;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.UrlUtils;
import org.sonarsource.sonarlint.core.serverapi.push.parsing.EventParser;
import org.sonarsource.sonarlint.core.serverapi.push.parsing.IssueChangedEventParser;
import org.sonarsource.sonarlint.core.serverapi.push.parsing.RuleSetChangedEventParser;
import org.sonarsource.sonarlint.core.serverapi.push.parsing.SecurityHotspotChangedEventParser;
import org.sonarsource.sonarlint.core.serverapi.push.parsing.SecurityHotspotClosedEventParser;
import org.sonarsource.sonarlint.core.serverapi.push.parsing.SecurityHotspotRaisedEventParser;
import org.sonarsource.sonarlint.core.serverapi.push.parsing.TaintVulnerabilityClosedEventParser;
import org.sonarsource.sonarlint.core.serverapi.push.parsing.TaintVulnerabilityRaisedEventParser;
import org.sonarsource.sonarlint.core.serverapi.stream.Event;
import org.sonarsource.sonarlint.core.serverapi.stream.EventStream;

public class PushApi {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  private static final String API_PATH = "api/push/sonarlint_events";
  private static final Map<String, EventParser<?>> parsersByType = Map.of(
    "RuleSetChanged", new RuleSetChangedEventParser(),
    "IssueChanged", new IssueChangedEventParser(),
    "TaintVulnerabilityRaised", new TaintVulnerabilityRaisedEventParser(),
    "TaintVulnerabilityClosed", new TaintVulnerabilityClosedEventParser(),
    "SecurityHotspotRaised", new SecurityHotspotRaisedEventParser(),
    "SecurityHotspotChanged", new SecurityHotspotChangedEventParser(),
    "SecurityHotspotClosed", new SecurityHotspotClosedEventParser());

  private final ServerApiHelper helper;

  public PushApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public EventStream subscribe(Set<String> projectKeys, Set<Language> enabledLanguages, Consumer<ServerEvent> serverEventConsumer, ClientLogOutput clientLogOutput) {
    return new EventStream(helper)
      .onEvent(rawEvent -> handleRawEvent(rawEvent, serverEventConsumer, clientLogOutput))
      .connect(getWsPath(projectKeys, enabledLanguages), clientLogOutput);
  }

  private static String getWsPath(Set<String> projectKeys, Set<Language> enabledLanguages) {
    return API_PATH + "?projectKeys=" +
      projectKeys.stream().map(UrlUtils::urlEncode).collect(Collectors.joining(",")) +
      "&languages=" +
      enabledLanguages.stream().map(Language::getLanguageKey).map(UrlUtils::urlEncode).collect(Collectors.joining(","));
  }

  private static void handleRawEvent(Event rawEvent, Consumer<ServerEvent> serverEventConsumer, ClientLogOutput clientLogOutput) {
    clientLogOutput.log("Server event received: " + rawEvent, ClientLogOutput.Level.DEBUG);
    parse(rawEvent).ifPresent(serverEventConsumer);
  }

  private static Optional<? extends ServerEvent> parse(Event event) {
    var eventType = event.getType();
    if (!parsersByType.containsKey(eventType)) {
      LOG.error("Unknown '{}' event type ", eventType);
      return Optional.empty();
    }
    try {
      return parsersByType.get(eventType).parse(event.getData());
    } catch (Exception e) {
      LOG.error("Cannot parse '{}' received event", eventType, e);
    }
    return Optional.empty();
  }

}
