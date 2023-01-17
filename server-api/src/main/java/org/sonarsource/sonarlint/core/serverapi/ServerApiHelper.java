/*
 * SonarLint Core - Server API
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
import org.sonarsource.sonarlint.core.commons.http.HttpClient;
import org.sonarsource.sonarlint.core.commons.http.HttpConnectionListener;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.serverapi.exception.NotFoundException;
import org.sonarsource.sonarlint.core.serverapi.exception.ServerErrorException;

/**
 * Wrapper around HttpClient to avoid repetitive code, like support of pagination, and log timing of requests
 */
public class ServerApiHelper {

  private static final SonarLintLogger LOG = SonarLintLogger.get();

  public static final int PAGE_SIZE = 500;
  public static final int MAX_PAGES = 20;

  private final HttpClient client;
  private final EndpointParams endpointParams;

  public ServerApiHelper(EndpointParams endpointParams, HttpClient client) {
    this.endpointParams = endpointParams;
    this.client = client;
  }

  public boolean isSonarCloud() {
    return endpointParams.isSonarCloud();
  }

  public HttpClient.Response get(String path) {
    var response = rawGet(path);
    if (!response.isSuccessful()) {
      throw handleError(response);
    }
    return response;
  }

  public CompletableFuture<HttpClient.Response> getAsync(String path) {
    return rawGetAsync(path)
      .thenApply(response -> {
        if (!response.isSuccessful()) {
          throw handleError(response);
        }
        return response;
      });
  }

  /**
   * Execute GET and don't check response
   */
  public HttpClient.Response rawGet(String relativePath) {
    var startTime = Instant.now();
    var url = buildEndpointUrl(relativePath);

    var response = client.get(url);
    var duration = Duration.between(startTime, Instant.now());
    LOG.debug("{} {} {} | response time={}ms", "GET", response.code(), url, duration.toMillis());
    return response;
  }

  public CompletableFuture<HttpClient.Response> rawGetAsync(String relativePath) {
    var startTime = Instant.now();
    var url = buildEndpointUrl(relativePath);

    return client.getAsync(url)
      .whenComplete((response, error) -> {
        var duration = Duration.between(startTime, Instant.now());
        LOG.debug("{} {} {} | response time={}ms", "GET", response.code(), url, duration.toMillis());
      });
  }

  private String buildEndpointUrl(String relativePath) {
    return concat(endpointParams.getBaseUrl(), relativePath);
  }

  public static String concat(String baseUrl, String relativePath) {
    return StringUtils.appendIfMissing(baseUrl, "/") +
      (relativePath.startsWith("/") ? relativePath.substring(1) : relativePath);
  }

  public static RuntimeException handleError(HttpClient.Response toBeClosed) {
    try (var failedResponse = toBeClosed) {
      if (failedResponse.code() == HttpURLConnection.HTTP_UNAUTHORIZED) {
        return new IllegalStateException("Not authorized. Please check server credentials.");
      }
      if (failedResponse.code() == HttpURLConnection.HTTP_FORBIDDEN) {
        // Details are in response content
        return new IllegalStateException(tryParseAsJsonError(failedResponse));
      }
      if (failedResponse.code() == HttpURLConnection.HTTP_NOT_FOUND) {
        return new NotFoundException(formatHttpFailedResponse(failedResponse, null));
      }
      if (failedResponse.code() >= HttpURLConnection.HTTP_INTERNAL_ERROR) {
        return new ServerErrorException(formatHttpFailedResponse(failedResponse, null));
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
    Function<G, List<F>> itemExtractor, Consumer<F> itemConsumer, boolean limitToTwentyPages, ProgressMonitor progress) {
    var page = new AtomicInteger(0);
    var stop = new AtomicBoolean(false);
    var loaded = new AtomicInteger(0);
    do {
      page.incrementAndGet();
      var fullUrl = new StringBuilder(relativeUrlWithoutPaginationParams);
      fullUrl.append(relativeUrlWithoutPaginationParams.contains("?") ? "&" : "?");
      fullUrl.append("ps=" + PAGE_SIZE + "&p=").append(page);
      ServerApiHelper.consumeTimed(
        () -> rawGet(fullUrl.toString()),
        response -> processPage(relativeUrlWithoutPaginationParams, responseParser, getPagingTotal, itemExtractor, itemConsumer, limitToTwentyPages, progress, page, stop, loaded,
          response),
        duration -> LOG.debug("Page downloaded in {}ms", duration));
    } while (!stop.get());
  }

  private static <F, G> void processPage(String baseUrl, CheckedFunction<InputStream, G> responseParser, Function<G, Number> getPagingTotal, Function<G, List<F>> itemExtractor,
    Consumer<F> itemConsumer, boolean limitToTwentyPages, ProgressMonitor progress, AtomicInteger page, AtomicBoolean stop, AtomicInteger loaded,
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
    stop.set(isEmpty || (pagingTotal > 0 && page.get() * PAGE_SIZE >= pagingTotal));
    if (!stop.get() && limitToTwentyPages && page.get() >= MAX_PAGES) {
      stop.set(true);
      LOG.debug("Limiting number of requested pages from '{}' to {}. Some of the data won't be fetched", baseUrl, MAX_PAGES);
    }

    progress.setProgressAndCheckCancel("Page " + page, loaded.get() / (float) pagingTotal);
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

  public static <G> CompletableFuture<G> processTimed(CompletableFuture<HttpClient.Response> futureResponse, IOFunction<HttpClient.Response, G> responseProcessor,
    LongConsumer durationConsumer) {
    var startTime = Instant.now();
    return futureResponse.thenApply(response -> {
      try (response) {
        var processed = responseProcessor.apply(response);
        durationConsumer.accept(Duration.between(startTime, Instant.now()).toMillis());
        return processed;
      } catch (IOException e) {
        throw new IllegalStateException("Unable to parse WS response: " + e.getMessage(), e);
      }
    });
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
