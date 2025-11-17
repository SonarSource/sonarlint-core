/*
 * SonarLint Core - RPC Protocol
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
package org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.auth;


import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

public class HelpGenerateUserTokenParams {
  private final String serverUrl;

  private final Utm utm;

  @Deprecated
  public HelpGenerateUserTokenParams(String serverUrl) {
    this(serverUrl, null);
  }

  public HelpGenerateUserTokenParams(String serverUrl, @Nullable Utm utm) {
    this.serverUrl = serverUrl;
    this.utm = utm;
  }

  public String getServerUrl() {
    return serverUrl;
  }

  @CheckForNull
  public Utm getUtm() {
    return utm;
  }

  public static class Utm {
    private final String medium;
    private final String source;
    private final String content;
    private final String term;

    public Utm(String medium, String source, String content, String term) {
      this.medium = medium;
      this.source = source;
      this.content = content;
      this.term = term;
    }

    public String getMedium() {
      return medium;
    }

    public String getSource() {
      return source;
    }

    public String getContent() {
      return content;
    }

    public String getTerm() {
      return term;
    }
  }
}
