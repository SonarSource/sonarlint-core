package org.sonarsource.sonarlint.core.embedded.server;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.io.HttpFilterChain;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.mockito.Mockito.*;

class RateLimitFilterTests {

  private final ClassicHttpRequest request = mock(ClassicHttpRequest.class);
  private final HttpFilterChain.ResponseTrigger responseTrigger = mock(HttpFilterChain.ResponseTrigger.class);
  private final HttpContext context = mock(HttpContext.class);
  private final HttpFilterChain chain = mock(HttpFilterChain.class);
  private RateLimitFilter filter;

  @BeforeEach
  void init() {
    filter = new RateLimitFilter();
  }

  @Test
  void should_not_proceed_with_request_if_origin_is_null() throws HttpException, IOException {
    when(request.getHeader("Origin")).thenReturn(null);

    filter.handle(request, responseTrigger, context, chain);

    verify(responseTrigger).submitResponse(any());
    verify(chain, never()).proceed(any(), any(), any());
  }

  @Test
  void should_proceed_when_request_is_valid() throws HttpException, IOException {
    when(request.getHeader("Origin")).thenReturn(new BasicHeader("Origin", "https://example.com"));

    filter.handle(request, responseTrigger, context, chain);

    verify(responseTrigger, never()).submitResponse(any());
    verify(chain).proceed(any(), any(), any());
  }

}
