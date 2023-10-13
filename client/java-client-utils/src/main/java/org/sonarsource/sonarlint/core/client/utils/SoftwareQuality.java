/*
<<<<<<<< HEAD:client/java-client-utils/src/main/java/org/sonarsource/sonarlint/core/client/utils/SoftwareQuality.java
 * SonarLint Core - Java Client Utils
========
 * SonarLint Core - RPC Protocol
>>>>>>>> 42594bc97 (SLCORE-571 Make the client-api JSON-RPC friendly):rpc-protocol/src/main/java/org/sonarsource/sonarlint/core/rpc/protocol/backend/issue/ResolutionStatus.java
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
<<<<<<<< HEAD:client/java-client-utils/src/main/java/org/sonarsource/sonarlint/core/client/utils/SoftwareQuality.java
package org.sonarsource.sonarlint.core.client.utils;
========
package org.sonarsource.sonarlint.core.rpc.protocol.backend.issue;
>>>>>>>> 42594bc97 (SLCORE-571 Make the client-api JSON-RPC friendly):rpc-protocol/src/main/java/org/sonarsource/sonarlint/core/rpc/protocol/backend/issue/ResolutionStatus.java

public enum SoftwareQuality {
  MAINTAINABILITY("Maintainability"),
  RELIABILITY("Reliability"),
  SECURITY("Security");

  private final String label;

  SoftwareQuality(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }

  public static SoftwareQuality fromDto(org.sonarsource.sonarlint.core.rpc.protocol.common.SoftwareQuality rpcEnum) {
    switch (rpcEnum) {
      case MAINTAINABILITY:
        return MAINTAINABILITY;
      case RELIABILITY:
        return RELIABILITY;
      case SECURITY:
        return SECURITY;
      default:
        throw new IllegalArgumentException("Unknown quality: " + rpcEnum);
    }
  }
}
