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
package org.sonarsource.sonarlint.core.ai.ide;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.util.Optional;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.CliAuthenticationStatus;

final class SonarQubeCliStatusDecoder {

  private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

  private SonarQubeCliStatusDecoder() {
  }

  static CliStatus decode(String json) throws IOException {
    var tree = JSON_MAPPER.readTree(json);
    if (tree == null || !tree.isObject()) {
      return CliStatus.unknown();
    }
    var auth = tree.get("auth");
    var version = stringValue(tree, "version");
    if (auth == null || !auth.isObject()) {
      return new CliStatus(CliAuthenticationStatus.UNKNOWN, version, null, null);
    }
    if ("unauthenticated".equals(stringValue(auth, "status").orElse(null))) {
      return new CliStatus(CliAuthenticationStatus.UNAUTHENTICATED, version, null, null);
    }

    var tokenStatus = stringValue(auth, "token").orElse(null);
    var authenticationStatus = switch (tokenStatus == null ? "" : tokenStatus) {
      case "active" -> CliAuthenticationStatus.AUTHENTICATED;
      case "invalid" -> CliAuthenticationStatus.INVALID;
      case "set_unverified" -> CliAuthenticationStatus.UNVERIFIED;
      case "not_set" -> CliAuthenticationStatus.UNAUTHENTICATED;
      default -> CliAuthenticationStatus.UNKNOWN;
    };
    return new CliStatus(authenticationStatus, version,
      stringValue(auth, "server").orElse(null), stringValue(auth, "org").orElse(null));
  }

  private static Optional<String> stringValue(JsonNode object, String property) {
    var value = object.get(property);
    return value == null || value.isNull() || !value.isValueNode() ? Optional.empty() : Optional.of(value.asText());
  }

  record CliStatus(CliAuthenticationStatus authenticationStatus, Optional<String> version,
                   @Nullable String serverUrl, @Nullable String organization) {
    static CliStatus unknown() {
      return new CliStatus(CliAuthenticationStatus.UNKNOWN, Optional.empty(), null, null);
    }

    static CliStatus unavailable() {
      return new CliStatus(CliAuthenticationStatus.UNAVAILABLE, Optional.empty(), null, null);
    }
  }
}
