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
    var vortexAvailable = isVortexAvailable(tree.get("vortex"));
    var auth = tree.get("auth");
    var version = stringValue(tree, "version");
    if (auth == null || !auth.isObject()) {
      return new CliStatus(CliAuthenticationStatus.UNKNOWN, version, null, null, vortexAvailable);
    }
    if ("unauthenticated".equals(stringValue(auth, "status").orElse(null))) {
      return new CliStatus(CliAuthenticationStatus.UNAUTHENTICATED, version, null, null, vortexAvailable);
    }

    var authenticationStatus = switch (stringValue(auth, "token").orElse(null)) {
      case "active" -> CliAuthenticationStatus.AUTHENTICATED;
      case "invalid" -> CliAuthenticationStatus.INVALID;
      case "set_unverified" -> CliAuthenticationStatus.UNVERIFIED;
      case "not_set" -> CliAuthenticationStatus.UNAUTHENTICATED;
      case null, default -> CliAuthenticationStatus.UNKNOWN;
    };
    return new CliStatus(authenticationStatus, version,
      stringValue(auth, "server").orElse(null), stringValue(auth, "org").orElse(null), vortexAvailable);
  }

  private static boolean isVortexAvailable(@Nullable JsonNode vortex) {
    if (vortex == null || !vortex.isObject()) {
      return false;
    }
    var applicable = vortex.get("applicable");
    var status = vortex.get("status");
    return applicable != null && applicable.isBoolean() && applicable.booleanValue()
      && status != null && status.isTextual() && ("enabled".equals(status.textValue()) || "over_consumption".equals(status.textValue()));
  }

  private static Optional<String> stringValue(JsonNode object, String property) {
    var value = object.get(property);
    return value == null || value.isNull() || !value.isValueNode() ? Optional.empty() : Optional.of(value.asText());
  }

  record CliStatus(CliAuthenticationStatus authenticationStatus, Optional<String> version,
                   @Nullable String serverUrl, @Nullable String organization, boolean vortexAvailable) {
    CliStatus(CliAuthenticationStatus authenticationStatus, Optional<String> version, @Nullable String serverUrl, @Nullable String organization) {
      this(authenticationStatus, version, serverUrl, organization, false);
    }

    static CliStatus unknown() {
      return new CliStatus(CliAuthenticationStatus.UNKNOWN, Optional.empty(), null, null);
    }

    static CliStatus unavailable() {
      return new CliStatus(CliAuthenticationStatus.UNAVAILABLE, Optional.empty(), null, null);
    }
  }
}
