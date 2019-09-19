/*
 * SonarLint Language Server
 * Copyright (C) 2009-2019 SonarSource SA
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
package org.sonarlint.languageserver;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.services.LanguageServer;

public interface SonarLintExtendedLanguageServer extends LanguageServer {

  @JsonRequest("sonarlint/listAllRules")
  CompletableFuture<Map<String, List<RuleDescription>>> listAllRules();

  /**
   * Undocumented VSCode message
   * https://github.com/Microsoft/vscode-languageserver-node/issues/170
   * https://github.com/eclipse/lsp4j/issues/22
   * https://github.com/microsoft/vscode-languageserver-node/blob/5c446d0620fc5fa6c57b1addcdfaff89a47624ae/jsonrpc/src/main.ts#L204
   */
  @JsonNotification("$/setTraceNotification")
  void setTraceNotification(SetTraceNotificationParams params);

  public static class SetTraceNotificationParams {
    private String value;

    public String getValue() {
      return value;
    }

    public void setValue(String value) {
      this.value = value;
    }
  }

  public enum TraceValues {
    @SerializedName("off")
    OFF,
    @SerializedName("messages")
    MESSAGES,
    @SerializedName("verbose")
    VERBOSE
  }

}
