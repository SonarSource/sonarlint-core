/*
 * SonarLint Core - Server Connection
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
package org.sonarsource.sonarlint.core.serverconnection.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.jooq.JSON;
import org.sonarsource.sonarlint.core.commons.ImpactSeverity;
import org.sonarsource.sonarlint.core.commons.SoftwareQuality;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerDependencyRisk;
import org.sonarsource.sonarlint.core.serverconnection.issues.ServerTaintIssue;

public class JsonMapper {
  private static final SonarLintLogger LOG = SonarLintLogger.get();
  private final ObjectMapper objectMapper = new ObjectMapper();

  public String serializeImpacts(Map<SoftwareQuality, ImpactSeverity> impacts) {
    try {
      return objectMapper.writeValueAsString(impacts);
    } catch (Exception e) {
      return "{}";
    }
  }

  public String serializeFlows(ServerTaintIssue issue) {
    try {
      return objectMapper.writeValueAsString(issue.getFlows());
    } catch (Exception e) {
      return "[]";
    }
  }


  public JSON serializeTransitions(@Nullable List<ServerDependencyRisk.Transition> transitions) {
    if (transitions == null) {
      return null;
    }
    try {
      var stringList = transitions.stream().map(Enum::name).toList();
      return JSON.valueOf(objectMapper.writeValueAsString(stringList));
    } catch (Exception e) {
      LOG.error("Failed to serialize transitions {}", transitions, e);
      return JSON.valueOf("{}");
    }
  }

  public Map<SoftwareQuality, ImpactSeverity> deserializeImpacts(@Nullable JSON impactsJson) {
    if (impactsJson == null) {
      return Map.of();
    }
    try {
      var map = objectMapper.readValue(impactsJson.data(), new TypeReference<Map<String, String>>() {
      });
      return map.entrySet().stream()
        .collect(Collectors.toMap(entry -> SoftwareQuality.valueOf(entry.getKey()), entry -> ImpactSeverity.valueOf(entry.getValue())));
    } catch (Exception e) {
      LOG.error("Failed to deserialize impacts {}", impactsJson.data(), e);
      return Map.of();
    }
  }

  public List<ServerDependencyRisk.Transition> deserializeTransitions(@Nullable JSON json) {
    if (json == null) {
      return List.of();
    }
    try {
      var transitions = objectMapper.readValue(json.data(), new TypeReference<List<String>>() {
      });
      return transitions.stream()
        .map(transition -> {
          try {
            return ServerDependencyRisk.Transition.valueOf(transition);
          } catch (Exception e) {
            return null;
          }
        })
        .filter(Objects::nonNull)
        .toList();
    } catch (Exception e) {
      LOG.error("Failed to deserialize transitions {}", json.data(), e);
      return List.of();
    }
  }

  public Set<SonarLanguage> deserializeLanguages(@Nullable JSON json) {
    if (json == null) {
      return Set.of();
    }
    try {
      var languages = objectMapper.readValue(json.data(), new TypeReference<List<String>>() {
      });
      return languages.stream()
        .map(language -> {
          try {
            return SonarLanguage.valueOf(language);
          } catch (Exception e) {
            return null;
          }
        })
        .filter(Objects::nonNull)
        .collect(Collectors.toUnmodifiableSet());
    } catch (Exception e) {
      LOG.error("Failed to deserialize enabled languages {}", json.data(), e);
      return Set.of();
    }
  }

  @Nullable
  public JSON serializeLanguages(Set<SonarLanguage> enabledLanguages) {
    try {
      var languageNames = enabledLanguages.stream().map(Enum::name).toList();
      return JSON.valueOf(objectMapper.writeValueAsString(languageNames));
    } catch (Exception e) {
      LOG.error("Failed to serialize enabled languages {}", enabledLanguages, e);
    }
    return null;
  }
}
