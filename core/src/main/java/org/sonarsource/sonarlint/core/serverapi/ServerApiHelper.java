/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2021 SonarSource SA
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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
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
import org.sonar.api.utils.System2;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarqube.ws.Common.Paging;
import org.sonarsource.sonarlint.core.container.connected.exceptions.NotFoundException;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;
import org.sonarsource.sonarlint.core.util.StringUtils;

import static java.util.stream.Collectors.joining;

/**
 * Wrapper around HttpClient to avoid repetitive code, like support of pagination, and log timing of requests
 */
public class ServerApiHelper {

  private static final Logger LOG = Loggers.get(ServerApiHelper.class);

  public static final int PAGE_SIZE = 500;
  public static final int MAX_PAGES = 20;

  private final HttpClient client;
  private final EndpointParams endpointParams;

  public ServerApiHelper(EndpointParams endpointParams, HttpClient client) {
    this.endpointParams = endpointParams;
    this.client = client;
  }

  public HttpClient.Response get(String path) {
    HttpClient.Response response = rawGet(path);
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
    long startTime = System2.INSTANCE.now();
    String url = buildEndpointUrl(relativePath);

    HttpClient.Response response = client.get(url);
    long duration = System2.INSTANCE.now() - startTime;
    if (LOG.isDebugEnabled()) {
      LOG.debug("{} {} {} | response time={}ms", "GET", response.code(), url, duration);
    }
    return response;
  }

  public CompletableFuture<HttpClient.Response> rawGetAsync(String relativePath) {
    long startTime = System2.INSTANCE.now();
    String url = buildEndpointUrl(relativePath);

    return client.getAsync(url)
      .whenComplete((response, error) -> {
        long duration = System2.INSTANCE.now() - startTime;
        LOG.debug("{} {} {} | response time={}ms", "GET", response.code(), url, duration);
      });
  }

  private String buildEndpointUrl(String relativePath) {
    StringBuilder fullUrl = new StringBuilder();
    String endpointUrl = endpointParams.getBaseUrl();
    fullUrl.append(endpointUrl.endsWith("/") ? endpointUrl.substring(0, endpointUrl.length() - 1) : endpointUrl);
    fullUrl.append("/");
    fullUrl.append(relativePath.startsWith("/") ? relativePath.substring(1, relativePath.length()) : relativePath);
    return fullUrl.toString();
  }

  public static RuntimeException handleError(HttpClient.Response toBeClosed) {
    try (HttpClient.Response failedResponse = toBeClosed) {
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

      String errorMsg = tryParseAsJsonError(failedResponse);

      return new IllegalStateException(formatHttpFailedResponse(failedResponse, errorMsg));
    }
  }

  private static String formatHttpFailedResponse(HttpClient.Response failedResponse, @Nullable String errorMsg) {
    return "Error " + failedResponse.code() + " on " + failedResponse.url() + (errorMsg != null ? (": " + errorMsg) : "");
  }

  @CheckForNull
  private static String tryParseAsJsonError(HttpClient.Response response) {
    String content = response.bodyAsString();
    if (StringUtils.isBlank(content)) {
      return null;
    }
    JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
    JsonArray errors = obj.getAsJsonArray("errors");
    List<String> errorMessages = new ArrayList<>();
    for (JsonElement e : errors) {
      errorMessages.add(e.getAsJsonObject().get("msg").getAsString());
    }
    return errorMessages.stream().collect(joining(", "));
  }

  public Optional<String> getOrganizationKey() {
    return endpointParams.getOrganization();
  }

  public <G, F> void getPaginated(String relativeUrlWithoutPaginationParams, CheckedFunction<InputStream, G> responseParser, Function<G, Paging> getPaging,
    Function<G, List<F>> itemExtractor, Consumer<F> itemConsumer, boolean limitToTwentyPages, ProgressWrapper progress) {
    AtomicInteger page = new AtomicInteger(0);
    AtomicBoolean stop = new AtomicBoolean(false);
    AtomicInteger loaded = new AtomicInteger(0);
    do {
      page.incrementAndGet();
      StringBuilder fullUrl = new StringBuilder(buildEndpointUrl(relativeUrlWithoutPaginationParams));
      fullUrl.append(relativeUrlWithoutPaginationParams.contains("?") ? "&" : "?");
      fullUrl.append("ps=" + PAGE_SIZE + "&p=" + page);
      ServerApiHelper.consumeTimed(
        () -> client.get(fullUrl.toString()),
        response -> processPage(relativeUrlWithoutPaginationParams, responseParser, getPaging, itemExtractor, itemConsumer, limitToTwentyPages, progress, page, stop, loaded,
          response),
        duration -> LOG.debug("Page downloaded in {}ms", duration));
    } while (!stop.get());
  }

  private static <F, G> void processPage(String baseUrl, CheckedFunction<InputStream, G> responseParser, Function<G, Paging> getPaging, Function<G, List<F>> itemExtractor,
    Consumer<F> itemConsumer, boolean limitToTwentyPages, ProgressWrapper progress, AtomicInteger page, AtomicBoolean stop, AtomicInteger loaded,
    HttpClient.Response response)
    throws IOException {
    if (!response.isSuccessful()) {
      throw handleError(response);
    }
    G protoBufResponse;
    try (InputStream body = response.bodyAsStream()) {
      protoBufResponse = responseParser.apply(body);
    }

    List<F> items = itemExtractor.apply(protoBufResponse);
    for (F item : items) {
      itemConsumer.accept(item);
      loaded.incrementAndGet();
    }
    boolean isEmpty = items.isEmpty();
    Paging paging = getPaging.apply(protoBufResponse);
    // SONAR-9150 Some WS used to miss the paging information, so iterate until response is empty
    stop.set(isEmpty || (paging.getTotal() > 0 && page.get() * PAGE_SIZE >= paging.getTotal()));
    if (!stop.get() && limitToTwentyPages && page.get() >= MAX_PAGES) {
      stop.set(true);
      LOG.debug("Limiting number of requested pages from '{}' to {}. Some of the data won't be fetched", baseUrl, MAX_PAGES);
    }

    progress.setProgressAndCheckCancel("Page " + page, loaded.get() / (float) paging.getTotal());
  }

  @FunctionalInterface
  public interface CheckedFunction<T, R> {
    R apply(T t) throws IOException;
  }

  public static <G> G processTimed(Supplier<HttpClient.Response> responseSupplier, IOFunction<HttpClient.Response, G> responseProcessor,
    LongConsumer durationConsummer) {
    long startTime = System2.INSTANCE.now();
    G result;
    try (HttpClient.Response response = responseSupplier.get()) {
      result = responseProcessor.apply(response);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to parse WS response: " + e.getMessage(), e);
    }
    durationConsummer.accept(System2.INSTANCE.now() - startTime);
    return result;
  }

  public static <G> CompletableFuture<G> processTimed(CompletableFuture<HttpClient.Response> futureResponse, IOFunction<HttpClient.Response, G> responseProcessor,
    LongConsumer durationConsumer) {
    long startTime = System2.INSTANCE.now();
    return futureResponse.thenApply(response -> {
      try {
        G processed = responseProcessor.apply(response);
        durationConsumer.accept(System2.INSTANCE.now() - startTime);
        return processed;
      } catch (IOException e) {
        throw new IllegalStateException("Unable to parse WS response: " + e.getMessage(), e);
      } finally {
        response.close();
      }
    });
  }

  public static void consumeTimed(Supplier<HttpClient.Response> responseSupplier, IOConsumer<HttpClient.Response> responseConsumer,
    LongConsumer durationConsummer) {
    processTimed(responseSupplier, r -> {
      responseConsumer.accept(r);
      return null;
    }, durationConsummer);
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
