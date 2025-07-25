/*
 * SonarLint Core - Server API
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
package org.sonarsource.sonarlint.core.serverapi;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.Supplier;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.SonarLintCancelMonitor;
import org.sonarsource.sonarlint.core.http.HttpClient;
import org.sonarsource.sonarlint.core.http.HttpConnectionListener;
import org.sonarsource.sonarlint.core.serverapi.exception.ForbiddenException;
import org.sonarsource.sonarlint.core.serverapi.exception.NotFoundException;
import org.sonarsource.sonarlint.core.serverapi.exception.ServerErrorException;
import org.sonarsource.sonarlint.core.serverapi.exception.TooManyRequestsException;
import org.sonarsource.sonarlint.core.serverapi.exception.UnauthorizedException;

import static java.util.Objects.requireNonNull;

/**
 * Wrapper around HttpClient to avoid repetitive code, like support of pagination, and log timing of requests
 */
public class ServerApiHelper {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  public static final int PAGE_SIZE = 500;
  public static final int MAX_PAGES = 20;
  public static final int HTTP_TOO_MANY_REQUESTS = 429;

  private final HttpClient client;
  private final EndpointParams endpointParams;

  public ServerApiHelper(EndpointParams endpointParams, HttpClient client) {
    this.endpointParams = endpointParams;
    this.client = client;
  }

  public boolean isSonarCloud() {
    return endpointParams.isSonarCloud();
  }

  public HttpClient.Response getAnonymous(String path, SonarLintCancelMonitor cancelMonitor) {
    var response = rawGetAnonymous(path, cancelMonitor);
    if (!response.isSuccessful()) {
      throw handleError(response);
    }
    return response;
  }

  public HttpClient.Response get(String path, SonarLintCancelMonitor cancelMonitor) {
    var response = rawGet(path, cancelMonitor);
    if (!response.isSuccessful()) {
      throw handleError(response);
    }
    return response;
  }

  public HttpClient.Response apiGet(String path, SonarLintCancelMonitor cancelMonitor) {
    var response = rawGetUrl(buildApiEndpointUrl(path), cancelMonitor);
    if (!response.isSuccessful()) {
      throw handleError(response);
    }
    return response;
  }

  public HttpClient.Response post(String relativePath, String contentType, String body, SonarLintCancelMonitor cancelMonitor) {
    return postUrl(buildEndpointUrl(relativePath), contentType, body, cancelMonitor);
  }

  public HttpClient.Response apiPost(String relativePath, String contentType, String body, SonarLintCancelMonitor cancelMonitor) {
    return postUrl(buildApiEndpointUrl(relativePath), contentType, body, cancelMonitor);
  }

  private HttpClient.Response postUrl(String url, String contentType, String body, SonarLintCancelMonitor cancelMonitor) {
    var response = rawPost(url, contentType, body, cancelMonitor);
    if (!response.isSuccessful()) {
      throw handleError(response);
    }
    return response;
  }

  /**
   * Execute GET without credentials and don't check response
   */
  public HttpClient.Response rawGetAnonymous(String relativePath, SonarLintCancelMonitor cancelMonitor) {
    return rawGetUrlAnonymous(buildEndpointUrl(relativePath), cancelMonitor);
  }

  /**
   * Execute GET and don't check response
   */
  public HttpClient.Response rawGet(String relativePath, SonarLintCancelMonitor cancelMonitor) {
    return rawGetUrl(buildEndpointUrl(relativePath), cancelMonitor);
  }

  private HttpClient.Response rawGetUrl(String url, SonarLintCancelMonitor cancelMonitor) {
    var startTime = Instant.now();
    var httpFuture = client.getAsync(url);
    return processResponse("GET", cancelMonitor, httpFuture, startTime, url);
  }

  private HttpClient.Response rawGetUrlAnonymous(String url, SonarLintCancelMonitor cancelMonitor) {
    var startTime = Instant.now();
    var httpFuture = client.getAsyncAnonymous(url);
    return processResponse("GET", cancelMonitor, httpFuture, startTime, url);
  }

  public HttpClient.Response rawPost(String url, String contentType, String body, SonarLintCancelMonitor cancelMonitor) {
    var startTime = Instant.now();
    var httpFuture = client.postAsync(url, contentType, body);
    return processResponse("POST", cancelMonitor, httpFuture, startTime, url);
  }

  private static HttpClient.Response processResponse(String method, SonarLintCancelMonitor cancelMonitor, CompletableFuture<HttpClient.Response> httpFuture,
    Instant startTime, String url) {
    cancelMonitor.onCancel(() -> httpFuture.cancel(true));
    try {
      var response = httpFuture.join();
      logTime(method, startTime, url, response.code());
      return response;
    } catch (Exception e) {
      logFailure(method, startTime, url, e.getMessage());
      throw e;
    }
  }

  private static void logTime(String method, Instant startTime, String url, int responseCode) {
    var duration = Duration.between(startTime, Instant.now());
    LOG.debug("{} {} {} | response time={}ms", method, responseCode, url, duration.toMillis());
  }

  private static void logFailure(String method, Instant startTime, String url, String message) {
    var duration = Duration.between(startTime, Instant.now());
    LOG.debug("{} {} {} | failed after {}ms", method, url, message, duration.toMillis());
  }

  private String buildEndpointUrl(String relativePath) {
    return concat(endpointParams.getBaseUrl(), relativePath);
  }

  private String buildApiEndpointUrl(String relativePath) {
    return concat(requireNonNull(endpointParams.getApiBaseUrl()), relativePath);
  }

  public static String concat(String baseUrl, String relativePath) {
    return Strings.CS.appendIfMissing(baseUrl, "/") +
      (relativePath.startsWith("/") ? relativePath.substring(1) : relativePath);
  }

  public static RuntimeException handleError(HttpClient.Response toBeClosed) {
    try (var failedResponse = toBeClosed) {
      if (failedResponse.code() == HttpURLConnection.HTTP_UNAUTHORIZED) {
        return new UnauthorizedException("Not authorized. Please check server credentials.");
      }
      if (failedResponse.code() == HttpURLConnection.HTTP_FORBIDDEN) {
        // Details are in the response content
        var error = tryParseAsJsonError(failedResponse);
        if (error == null) {
          error = "Access denied";
        }
        return new ForbiddenException(error);
      }
      if (failedResponse.code() == HttpURLConnection.HTTP_NOT_FOUND) {
        return new NotFoundException(formatHttpFailedResponse(failedResponse, null));
      }
      if (failedResponse.code() >= HttpURLConnection.HTTP_INTERNAL_ERROR) {
        return new ServerErrorException(formatHttpFailedResponse(failedResponse, null));
      }
      if (failedResponse.code() == HTTP_TOO_MANY_REQUESTS) {
        return new TooManyRequestsException("Too many requests have been made.");
      }

      var errorMsg = tryParseAsJsonError(failedResponse);

      return new IllegalStateException(formatHttpFailedResponse(failedResponse, errorMsg));
    }
  }

  private static String formatHttpFailedResponse(HttpClient.Response failedResponse, @Nullable String errorMsg) {
    return "Error " + failedResponse.code() + " on " + failedResponse.url() + (errorMsg != null ? (": " + errorMsg) : "");
  }

  @CheckForNull
  private static String tryParseAsJsonError(HttpClient.Response response) {
    var content = response.bodyAsString();
    if (StringUtils.isBlank(content)) {
      return null;
    }
    var obj = JsonParser.parseString(content).getAsJsonObject();
    var errors = obj.getAsJsonArray("errors");
    if (errors == null) {
      return null;
    }
    List<String> errorMessages = new ArrayList<>();
    for (JsonElement e : errors) {
      errorMessages.add(e.getAsJsonObject().get("msg").getAsString());
    }
    return String.join(", ", errorMessages);
  }

  public Optional<String> getOrganizationKey() {
    return endpointParams.getOrganization();
  }

  public <G, F> void getPaginated(String relativeUrlWithoutPaginationParams, CheckedFunction<InputStream, G> responseParser, Function<G, Number> getPagingTotal,
    Function<G, List<F>> itemExtractor, Consumer<F> itemConsumer, boolean limitToTwentyPages, SonarLintCancelMonitor cancelChecker) {
    getPaginated(relativeUrlWithoutPaginationParams, responseParser, getPagingTotal, itemExtractor, itemConsumer, limitToTwentyPages, cancelChecker, "p", "ps");
  }

  public <G, F> void getPaginated(String relativeUrlWithoutPaginationParams, CheckedFunction<InputStream, G> responseParser, Function<G, Number> getPagingTotal,
    Function<G, List<F>> itemExtractor, Consumer<F> itemConsumer, boolean limitToTwentyPages, SonarLintCancelMonitor cancelChecker, String pageFieldName,
    String pageSizeFieldName) {
    var page = new AtomicInteger(0);
    var stop = new AtomicBoolean(false);
    var loaded = new AtomicInteger(0);
    do {
      page.incrementAndGet();
      String fullUrl = relativeUrlWithoutPaginationParams + (relativeUrlWithoutPaginationParams.contains("?") ? "&" : "?") +
        pageSizeFieldName + "=" + PAGE_SIZE + "&" + pageFieldName + "=" + page;
      ServerApiHelper.consumeTimed(
        () -> rawGet(fullUrl, cancelChecker),
        response -> processPage(relativeUrlWithoutPaginationParams, responseParser, getPagingTotal, itemExtractor, itemConsumer, limitToTwentyPages, page, stop, loaded,
          response),
        duration -> LOG.debug("Page downloaded in {}ms", duration));
    } while (!stop.get() && !cancelChecker.isCanceled());
  }

  private static <F, G> void processPage(String baseUrl, CheckedFunction<InputStream, G> responseParser, Function<G, Number> getPagingTotal, Function<G, List<F>> itemExtractor,
    Consumer<F> itemConsumer, boolean limitToTwentyPages, AtomicInteger page, AtomicBoolean stop, AtomicInteger loaded,
    HttpClient.Response response)
    throws IOException {
    if (!response.isSuccessful()) {
      throw handleError(response);
    }
    G protoBufResponse;
    try (var body = response.bodyAsStream()) {
      protoBufResponse = responseParser.apply(body);
    }

    var items = itemExtractor.apply(protoBufResponse);
    for (F item : items) {
      itemConsumer.accept(item);
      loaded.incrementAndGet();
    }
    var isEmpty = items.isEmpty();
    var pagingTotal = getPagingTotal.apply(protoBufResponse).longValue();
    // SONAR-9150 Some WS used to miss the paging information, so iterate until response is empty
    stop.set(isEmpty || (pagingTotal > 0 && (long) page.get() * PAGE_SIZE >= pagingTotal));
    if (!stop.get() && limitToTwentyPages && page.get() >= MAX_PAGES) {
      stop.set(true);
      LOG.debug("Limiting number of requested pages from '{}' to {}. Some of the data won't be fetched", baseUrl, MAX_PAGES);
    }
  }

  public HttpClient.AsyncRequest getEventStream(String path, HttpConnectionListener connectionListener, Consumer<String> messageConsumer) {
    return client.getEventStream(buildEndpointUrl(path),
      connectionListener,
      messageConsumer);
  }

  @FunctionalInterface
  public interface CheckedFunction<T, R> {
    R apply(T t) throws IOException;
  }

  public static <G> G processTimed(Supplier<HttpClient.Response> responseSupplier, IOFunction<HttpClient.Response, G> responseProcessor,
    LongConsumer durationConsumer) {
    var startTime = Instant.now();
    G result;
    try (var response = responseSupplier.get()) {
      result = responseProcessor.apply(response);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to parse WS response: " + e.getMessage(), e);
    }
    durationConsumer.accept(Duration.between(startTime, Instant.now()).toMillis());
    return result;
  }

  public static void consumeTimed(Supplier<HttpClient.Response> responseSupplier, IOConsumer<HttpClient.Response> responseConsumer,
    LongConsumer durationConsumer) {
    processTimed(responseSupplier, r -> {
      responseConsumer.accept(r);
      return null;
    }, durationConsumer);
  }

  @FunctionalInterface
  public interface IOFunction<T, R> {
    R apply(T t) throws IOException;
  }

  @FunctionalInterface
  public interface IOConsumer<T> {
    void accept(T t) throws IOException;
  }

}
