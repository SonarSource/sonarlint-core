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
package org.sonarsource.sonarlint.core.ai.ide;

import java.nio.file.Path;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiAssistedIde;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.GetRuleFileContentResponse;

public class AiAssistedIdeService {
  public GetRuleFileContentResponse getRuleFileContent(AiAssistedIde ide) {
    if (ide == AiAssistedIde.CURSOR) {
      return new GetRuleFileContentResponse(Path.of(".cursor/rules/sonarqube_ide.md"), """
        """);
    }
    return new GetRuleFileContentResponse();
  }
}
