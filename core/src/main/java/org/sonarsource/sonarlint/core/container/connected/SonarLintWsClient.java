/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2020 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.connected;

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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarqube.ws.Common.Paging;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.container.connected.exceptions.NotFoundException;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;
import org.sonarsource.sonarlint.core.util.ws.GetRequest;
import org.sonarsource.sonarlint.core.util.ws.HttpConnector;
import org.sonarsource.sonarlint.core.util.ws.PostRequest;
import org.sonarsource.sonarlint.core.util.ws.WsConnector;
import org.sonarsource.sonarlint.core.util.ws.WsResponse;

import static java.util.stream.Collectors.joining;

public class SonarLintWsClient {

  private static final Logger LOG = Loggers.get(SonarLintWsClient.class);

  public static final int PAGE_SIZE = 500;
  public static final int MAX_PAGES = 20;

  private final WsConnector client;
  private final String userAgent;
  private final String organizationKey;

  public SonarLintWsClient(ServerConfiguration serverConfig) {
    this.userAgent = serverConfig.getUserAgent();
    this.organizationKey = serverConfig.getOrganizationKey();
    client = buildClient(serverConfig);
  }

  private static WsConnector buildClient(ServerConfiguration serverConfig) {
    return HttpConnector.newBuilder().url(serverConfig.getUrl())
      .userAgent(serverConfig.getUserAgent())
      .credentials(serverConfig.getLogin(), serverConfig.getPassword())
      .proxy(serverConfig.getProxy())
      .proxyCredentials(serverConfig.getProxyLogin(), serverConfig.getProxyPassword())
      .readTimeoutMilliseconds(serverConfig.getReadTimeoutMs())
      .connectTimeoutMilliseconds(serverConfig.getConnectTimeoutMs())
      .setSSLSocketFactory(serverConfig.getSSLSocketFactory())
      .setTrustManager(serverConfig.getTrustManager())
      .build();
  }

  public WsResponse get(String path) {
    WsResponse response = rawGet(path);
    if (!response.isSuccessful()) {
      throw handleError(response);
    }
    return response;
  }

  public WsResponse post(String path) {
    WsResponse response = rawPost(path);
    if (!response.isSuccessful()) {
      throw handleError(response);
    }
    return response;
  }

  /**
   * Execute POST and don't check response
   */
  public WsResponse rawPost(String path) {
    long startTime = System2.INSTANCE.now();
    PostRequest request = new PostRequest(path);
    WsResponse response = client.call(request);
    long duration = System2.INSTANCE.now() - startTime;
    if (LOG.isDebugEnabled()) {
      LOG.debug("{} {} {} | response time={}ms", request.getMethod(), response.code(), response.requestUrl(), duration);
    }
    return response;
  }

  /**
   * Execute GET and don't check response
   */
  public WsResponse rawGet(String path) {
    long startTime = System2.INSTANCE.now();
    GetRequest request = new GetRequest(path);
    WsResponse response = client.call(request);
    long duration = System2.INSTANCE.now() - startTime;
    if (LOG.isDebugEnabled()) {
      LOG.debug("{} {} {} | response time={}ms", request.getMethod(), response.code(), response.requestUrl(), duration);
    }
    return response;
  }

  public static RuntimeException handleError(WsResponse toBeClosed) {
    try (WsResponse failedResponse = toBeClosed) {
      if (failedResponse.code() == HttpURLConnection.HTTP_UNAUTHORIZED) {
        return new IllegalStateException("Not authorized. Please check server credentials.");
      }
      if (failedResponse.code() == HttpURLConnection.HTTP_FORBIDDEN) {
        // Details are in response content
        return new IllegalStateException(tryParseAsJsonError(failedResponse.content()));
      }
      if (failedResponse.code() == HttpURLConnection.HTTP_NOT_FOUND) {
        return new NotFoundException(formatHttpFailedResponse(failedResponse, null));
      }

      String errorMsg = null;
      if (failedResponse.hasContent()) {
        errorMsg = tryParseAsJsonError(failedResponse.content());
      }

      return new IllegalStateException(formatHttpFailedResponse(failedResponse, errorMsg));
    }
  }

  private static String formatHttpFailedResponse(WsResponse failedResponse, @Nullable String errorMsg) {
    return "Error " + failedResponse.code() + " on " + failedResponse.requestUrl() + (errorMsg != null ? (": " + errorMsg) : "");
  }

  private static String tryParseAsJsonError(String responseContent) {
    try {
      JsonParser parser = new JsonParser();
      JsonObject obj = parser.parse(responseContent).getAsJsonObject();
      JsonArray errors = obj.getAsJsonArray("errors");
      List<String> errorMessages = new ArrayList<>();
      for (JsonElement e : errors) {
        errorMessages.add(e.getAsJsonObject().get("msg").getAsString());
      }
      return errorMessages.stream().collect(joining(", "));
    } catch (Exception e) {
      return null;
    }
  }

  public String getUserAgent() {
    return userAgent;
  }

  public Optional<String> getOrganizationKey() {
    return Optional.ofNullable(organizationKey);
  }

  // static to allow mocking SonarLintWsClient while still using this method
  /**
   * @param responseParser ProtoBuf parser
   * @param getPaging extract {@link Paging} from the protobuf message
   */
  public static <G, F> void getPaginated(SonarLintWsClient client, String baseUrl, CheckedFunction<InputStream, G> responseParser, Function<G, Paging> getPaging,
    Function<G, List<F>> itemExtractor, Consumer<F> itemConsumer, boolean limitToTwentyPages, ProgressWrapper progress) {
    AtomicInteger page = new AtomicInteger(0);
    AtomicBoolean stop = new AtomicBoolean(false);
    AtomicInteger loaded = new AtomicInteger(0);
    do {
      page.incrementAndGet();
      String url = baseUrl + (baseUrl.contains("?") ? "&" : "?") + "ps=" + PAGE_SIZE + "&p=" + page;
      SonarLintWsClient.consumeTimed(
        () -> client.get(url),
        response -> processPage(baseUrl, responseParser, getPaging, itemExtractor, itemConsumer, limitToTwentyPages, progress, page, stop, loaded, response),
        duration -> LOG.debug("Page downloaded in {}ms", duration));
    } while (!stop.get());
  }

  private static <F, G> void processPage(String baseUrl, CheckedFunction<InputStream, G> responseParser, Function<G, Paging> getPaging, Function<G, List<F>> itemExtractor,
    Consumer<F> itemConsumer, boolean limitToTwentyPages, ProgressWrapper progress, AtomicInteger page, AtomicBoolean stop, AtomicInteger loaded, WsResponse response)
    throws IOException {
    G protoBufResponse = responseParser.apply(response.contentStream());
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

  public static <G> G processTimed(Supplier<WsResponse> responseSupplier, IOFunction<WsResponse, G> responseProcessor, LongConsumer durationConsummer) {
    long startTime = System2.INSTANCE.now();
    G result;
    try (WsResponse response = responseSupplier.get()) {
      result = responseProcessor.apply(response);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    durationConsummer.accept(System2.INSTANCE.now() - startTime);
    return result;
  }

  public static void consumeTimed(Supplier<WsResponse> responseSupplier, IOConsummer<WsResponse> responseConsumer, LongConsumer durationConsummer) {
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
  public interface IOConsummer<T> {
    void accept(T t) throws IOException;
  }

}
