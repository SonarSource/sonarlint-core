/*
 * SonarLint Core - RPC Protocol
 * Copyright (C) 2016-2024 SonarSource SA
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
package org.sonarsource.sonarlint.core.rpc.protocol;

import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;

/*
 * A class to use in place of {@link org.eclipse.lsp4j.jsonrpc.messages.ResponseError} to stop depending on lsp4j types in API and services.
 * See SLCORE-663 for details.
 */
public class SonarLintRpcResponseError {

  private int code;

  public int getCode() {
    return this.code;
  }

  public void setCode(int code) {
    this.code = code;
  }

  /**
   * A string providing a short description of the error.
   */

  private String message;


  public String getMessage() {
    return this.message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public SonarLintRpcResponseError(int code, String message) {
    this.code = code;
    this.message = message;
  }

  @Override
  public String toString() {
    return MessageJsonHandler.toString(this);
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    SonarLintRpcResponseError other = (SonarLintRpcResponseError) obj;
    if (other.code != this.code) {
      return false;
    }
    if (this.message == null) {
      return other.message == null;
    } else {
      return this.message.equals(other.message);
    }
  }

  @Override
  public int hashCode() {
    final var prime = 31;
    var result = 1;
    result = prime * result + this.code;
    result = prime * result + ((this.message == null) ? 0 : this.message.hashCode());
    return result;
  }
}
