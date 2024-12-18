/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.embedded.server;

import java.io.IOException;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.io.HttpFilterChain;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;

class CspFilterTest {

  private CspFilter cspFilter;
  private HttpFilterChain.ResponseTrigger mockResponseTrigger;
  private HttpFilterChain mockFilterChain;
  private HttpContext mockContext;
  private ClassicHttpRequest mockRequest;

  @BeforeEach
  void setUp() {
    cspFilter = new CspFilter();
    mockResponseTrigger = Mockito.mock(HttpFilterChain.ResponseTrigger.class);
    mockFilterChain = Mockito.mock(HttpFilterChain.class);
    mockContext = Mockito.mock(HttpContext.class);
    mockRequest = new BasicClassicHttpRequest("GET", "http://localhost:64120/sonarlint/api/endpoint");
  }

  @Test
  void it_should_add_csp_header_to_the_response_when_response_is_successful() throws HttpException, IOException {
    doAnswer(invocation -> {
      HttpFilterChain.ResponseTrigger trigger = invocation.getArgument(1);
      var mockResponse = new BasicClassicHttpResponse(200);
      trigger.submitResponse(mockResponse);
      trigger.sendInformation(mockResponse);
      return null;
    }).when(mockFilterChain).proceed(eq(mockRequest), any(), eq(mockContext));

    cspFilter.handle(mockRequest, mockResponseTrigger, mockContext, mockFilterChain);

    var captor = ArgumentCaptor.forClass(ClassicHttpResponse.class);
    verify(mockResponseTrigger).submitResponse(captor.capture());
    var response = captor.getValue();
    var cspHeader = response.getHeader("Content-Security-Policy-Report-Only").getValue();

    assertThat(cspHeader).isEqualTo("connect-src 'self' http://localhost:64120;");
    verify(mockResponseTrigger).sendInformation(any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"400", "401", "403", "404", "500"})
  void it_should_not_add_csp_header_to_the_response_when_response_is_unsuccessful(String responseCode) throws HttpException, IOException {
    doAnswer(invocation -> {
      HttpFilterChain.ResponseTrigger trigger = invocation.getArgument(1);
      var mockResponse = new BasicClassicHttpResponse(Integer.parseInt(responseCode));
      trigger.submitResponse(mockResponse);
      return null;
    }).when(mockFilterChain).proceed(eq(mockRequest), any(), eq(mockContext));

    cspFilter.handle(mockRequest, mockResponseTrigger, mockContext, mockFilterChain);

    var captor = ArgumentCaptor.forClass(ClassicHttpResponse.class);
    verify(mockResponseTrigger).submitResponse(captor.capture());
    var response = captor.getValue();
    assertThat(response.getHeader("Content-Security-Policy-Report-Only")).isNull();
  }
}

