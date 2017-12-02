/*
 * SonarLint Language Server
 * Copyright (C) 2009-2017 SonarSource SA
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

import java.util.Map;

public class SonarQubeServer {
  private String id;
  private String url;
  private String token;
  private String login;
  private String password;

  public SonarQubeServer(Map<String, String> map) {
	this.id = map.get("id");
	this.url = map.get("url");
	this.token = map.get("token");
	this.login = map.get("login");
	this.password = map.get("password");
  }

  public String id() {
    return id;
  }

  public String url() {
    return url;
  }

  public String token() {
    return token;
  }

  public String login() {
    return login;
  }

  public String password() {
    return password;
  }
}
