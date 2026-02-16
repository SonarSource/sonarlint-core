/*
 * SonarLint Core - Server API
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
package org.sonarsource.sonarlint.core.serverapi;

import java.net.HttpURLConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogTester;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.serverapi.exception.ForbiddenException;
import org.sonarsource.sonarlint.core.serverapi.exception.NotFoundException;
import org.sonarsource.sonarlint.core.serverapi.exception.ServerErrorException;
import org.sonarsource.sonarlint.core.serverapi.exception.TooManyRequestsException;
import org.sonarsource.sonarlint.core.serverapi.exception.UnauthorizedException;
import org.sonarsource.sonarlint.core.serverapi.exception.UnexpectedServerResponseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServerApiHelperTests {

  @RegisterExtension
  private static final SonarLintLogTester logTester = new SonarLintLogTester();

  @Test
  void concat_should_handle_base_url_with_trailing_slash() {
    var result = ServerApiHelper.concat("http://localhost:9000/", "/api/test");
    
    assertThat(result).isEqualTo("http://localhost:9000/api/test");
  }

  @Test
  void concat_should_handle_base_url_without_trailing_slash() {
    var result = ServerApiHelper.concat("http://localhost:9000", "/api/test");
    
    assertThat(result).isEqualTo("http://localhost:9000/api/test");
  }

  @Test
  void concat_should_handle_relative_path_without_leading_slash() {
    var result = ServerApiHelper.concat("http://localhost:9000", "api/test");
    
    assertThat(result).isEqualTo("http://localhost:9000/api/test");
  }

  @Test
  void concat_should_handle_empty_relative_path() {
    var result = ServerApiHelper.concat("http://localhost:9000", "");
    
    assertThat(result).isEqualTo("http://localhost:9000/");
  }

  @Test
  void handleError_should_throw_unauthorized_exception() {
    var response = mock(HttpClient.Response.class);
    when(response.code()).thenReturn(HttpURLConnection.HTTP_UNAUTHORIZED);

    var error = ServerApiHelper.handleError(response);
    
    assertThat(error)
      .isInstanceOf(UnauthorizedException.class)
      .hasMessage("Not authorized. Please check server credentials.");
  }

  @Test
  void handleError_should_throw_forbidden_exception() {
    var response = mock(HttpClient.Response.class);
    when(response.code()).thenReturn(HttpURLConnection.HTTP_FORBIDDEN);
    when(response.bodyAsString()).thenReturn("{\"errors\":[{\"msg\":\"Access denied\"}]}");

    var error = ServerApiHelper.handleError(response);

    assertThat(error)
      .isInstanceOf(ForbiddenException.class)
      .hasMessage("Access denied");
  }

  @Test
  void handleError_should_throw_forbidden_exception_with_default_message() {
    var response = mock(HttpClient.Response.class);
    when(response.code()).thenReturn(HttpURLConnection.HTTP_FORBIDDEN);
    when(response.bodyAsString()).thenReturn("{}");

    var error = ServerApiHelper.handleError(response);
    
    assertThat(error)
      .isInstanceOf(ForbiddenException.class)
      .hasMessage("Access denied");
  }

  @Test
  void handleError_should_throw_not_found_exception() {
    var response = mock(HttpClient.Response.class);
    when(response.code()).thenReturn(HttpURLConnection.HTTP_NOT_FOUND);
    when(response.url()).thenReturn("http://localhost:9000/api/test");

    var error = ServerApiHelper.handleError(response);
    
    assertThat(error).isInstanceOf(NotFoundException.class);
  }

  @Test
  void handleError_should_throw_server_error_exception() {
    var response = mock(HttpClient.Response.class);
    when(response.code()).thenReturn(HttpURLConnection.HTTP_INTERNAL_ERROR);
    when(response.url()).thenReturn("http://localhost:9000/api/test");

    var error = ServerApiHelper.handleError(response);
    
    assertThat(error).isInstanceOf(ServerErrorException.class);
  }

  @Test
  void handleError_should_throw_too_many_requests_exception() {
    var response = mock(HttpClient.Response.class);
    when(response.code()).thenReturn(ServerApiHelper.HTTP_TOO_MANY_REQUESTS);

    var error = ServerApiHelper.handleError(response);
    
    assertThat(error)
      .isInstanceOf(TooManyRequestsException.class)
      .hasMessage("Too many requests have been made.");
  }

  @Test
  void handleError_should_throw_illegal_state_exception_for_other_codes() {
    var response = mock(HttpClient.Response.class);
    when(response.code()).thenReturn(HttpURLConnection.HTTP_BAD_REQUEST);
    when(response.url()).thenReturn("http://localhost:9000/api/test");
    when(response.bodyAsString()).thenReturn("{\"errors\":[{\"msg\":\"Bad request\"}]}");

    var error = ServerApiHelper.handleError(response);
    
    assertThat(error)
      .isInstanceOf(UnexpectedServerResponseException.class)
      .hasMessageContaining("Error 400 on http://localhost:9000/api/test: Bad request");
  }

  @Test
  void handleError_should_throw_unexpected_response_body_exception_when_error_body_unexpected() {
    var response = mock(HttpClient.Response.class);
    when(response.code()).thenReturn(HttpURLConnection.HTTP_BAD_REQUEST);
    when(response.url()).thenReturn("http://localhost:9000/api/test");
    when(response.bodyAsString()).thenReturn("not json");

    var error = ServerApiHelper.handleError(response);

    assertThat(error)
      .isInstanceOf(UnexpectedServerResponseException.class)
      .hasMessageContaining("Error 400 on http://localhost:9000/api/test");
  }

} 
