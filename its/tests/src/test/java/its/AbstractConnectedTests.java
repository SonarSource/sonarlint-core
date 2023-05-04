/*
 * SonarLint Core - ITs - Tests
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
package its;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.build.MavenBuild;
import com.sonar.orchestrator.http.HttpMethod;
import its.utils.TestClientInputFile;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow.Subscription;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.assertj.core.internal.Failures;
import org.sonarqube.ws.Issues;
import org.sonarqube.ws.Qualityprofiles.SearchWsResponse.QualityProfile;
import org.sonarqube.ws.client.HttpConnector;
import org.sonarqube.ws.client.PostRequest;
import org.sonarqube.ws.client.WsClient;
import org.sonarqube.ws.client.WsClientFactories;
import org.sonarqube.ws.client.qualityprofiles.SearchRequest;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.commons.http.HttpClient;
import org.sonarsource.sonarlint.core.commons.http.HttpConnectionListener;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;

import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class AbstractConnectedTests {
  protected static final String SONARLINT_USER = "sonarlint";
  protected static final String SONARLINT_PWD = "sonarlintpwd";
  protected static final String MAIN_BRANCH_NAME = "master";

  protected static final java.net.http.HttpClient SHARED_CLIENT = java.net.http.HttpClient.newBuilder().build();

  protected static final class SonarLintHttpClientJavaNetImpl implements HttpClient {

    private final java.net.http.HttpClient javaNetClient;
    private final String username;
    private final String password;

    SonarLintHttpClientJavaNetImpl(java.net.http.HttpClient jdkClient) {
      this(jdkClient, null, null);
    }

    SonarLintHttpClientJavaNetImpl(java.net.http.HttpClient javaNetClient, String username, String password) {
      this.javaNetClient = javaNetClient;
      this.username = username;
      this.password = password;
    }

    @Override
    public Response post(String url, String contentType, String bodyContent) {
      var request = buildRequest(url)
        .headers("Content-Type", contentType)
        .POST(HttpRequest.BodyPublishers.ofString(bodyContent)).build();
      return executeRequest(request);
    }

    private HttpRequest.Builder buildRequest(String url) {
      var request = HttpRequest.newBuilder().uri(URI.create(url)).setHeader("User-Agent", "SonarLint ITs");
      if (username != null) {
        var plainCreds = username + ":" + password;
        var base64Bytes = Base64.encodeBase64(plainCreds.getBytes());
        request.header("Authorization", "Basic " + new String(base64Bytes, StandardCharsets.UTF_8));
      }
      return request;
    }

    @Override
    public Response get(String url) {
      var request = buildRequest(url).GET().build();
      return executeRequest(request);
    }

    @Override
    public CompletableFuture<Response> getAsync(String url) {
      var request = buildRequest(url).build();
      return executeRequestAsync(request);
    }

    @Override
    public CompletableFuture<Response> postAsync(String url, String contentType, String body) {
      var request = buildRequest(url)
        .headers("Content-Type", contentType)
        .POST(HttpRequest.BodyPublishers.ofString(body)).build();
      return executeRequestAsync(request);
    }

    @Override
    public AsyncRequest getEventStream(String url, HttpConnectionListener connectionListener, Consumer<String> messageConsumer) {
      var request = buildRequest(url)
        .header("Accept", "text/event-stream")
        .build();
      var responseFuture = javaNetClient.sendAsync(request, responseInfo -> {
        if (responseInfo.statusCode() == HttpURLConnection.HTTP_OK) {
          connectionListener.onConnected();
          return new SseSubscriber(messageConsumer::accept);
        } else {
          connectionListener.onError(responseInfo.statusCode());
          throw new RuntimeException("Request failed");
        }
      });
      return new HttpAsyncRequest(responseFuture);
    }

    @Override
    public Response delete(String url, String contentType, String bodyContent) {
      var request = buildRequest(url)
        .headers("Content-Type", contentType)
        .method("DELETE", HttpRequest.BodyPublishers.ofString(bodyContent)).build();
      return executeRequest(request);
    }

    private Response executeRequest(HttpRequest request) {
      try {
        return wrap(javaNetClient.send(request, HttpResponse.BodyHandlers.ofInputStream()));
      } catch (Exception e) {
        throw new IllegalStateException("Unable to execute request: " + e.getMessage(), e);
      }
    }

    private CompletableFuture<Response> executeRequestAsync(HttpRequest request) {
      var call = javaNetClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
      return call.thenApply(r -> wrap(r));
    }

    private Response wrap(HttpResponse<InputStream> wrapped) {
      return new Response() {

        @Override
        public String url() {
          return wrapped.request().uri().toString();
        }

        @Override
        public int code() {
          return wrapped.statusCode();
        }

        @Override
        public void close() {
          // Nothing
        }

        @Override
        public String bodyAsString() {
          try (var body = wrapped.body()) {
            return new String(body.readAllBytes(), StandardCharsets.UTF_8);
          } catch (IOException e) {
            throw new IllegalStateException("Unable to read response body: " + e.getMessage(), e);
          }
        }

        @Override
        public InputStream bodyAsStream() {
          return wrapped.body();
        }

        @Override
        public String toString() {
          return wrapped.toString();
        }
      };
    }

    public static class SseSubscriber implements HttpResponse.BodySubscriber<Void> {
      protected final Consumer<? super String> messageConsumer;
      protected final CompletableFuture<Void> future;
      protected volatile Subscription subscription;
      protected volatile String deferredText;

      public SseSubscriber(Consumer<? super String> messageConsumer) {
        this.messageConsumer = messageConsumer;
        this.future = new CompletableFuture<>();
        this.subscription = null;
        this.deferredText = null;
      }

      @Override
      public void onSubscribe(Subscription subscription) {
        this.subscription = subscription;
        try {
          this.deferredText = "";
          this.subscription.request(1);
        } catch (Exception e) {
          this.future.completeExceptionally(e);
          this.subscription.cancel();
        }
      }

      @Override
      public void onNext(List<ByteBuffer> buffers) {
        try {
          for (var buffer : buffers) {
            // TODO: Safe to assume multi-byte chars don't get split across buffers?
            this.messageConsumer.accept(StandardCharsets.UTF_8.decode(buffer).toString());
          }
          this.subscription.request(1);
        } catch (Exception e) {
          this.future.completeExceptionally(e);
          this.subscription.cancel();
        }
      }

      @Override
      public void onError(Throwable e) {
        this.future.completeExceptionally(e);
      }

      @Override
      public void onComplete() {
        try {
          this.future.complete(null);
        } catch (Exception e) {
          this.future.completeExceptionally(e);
        }
      }

      @Override
      public CompletionStage<Void> getBody() {
        return this.future;
      }
    }

  }

  private static class HttpAsyncRequest implements HttpClient.AsyncRequest {
    private final CompletableFuture<?> response;

    private HttpAsyncRequest(CompletableFuture<?> response) {
      this.response = response;
    }

    @Override
    public void cancel() {
      response.cancel(true);
    }

  }

  protected static class SaveIssueListener implements IssueListener {
    List<Issue> issues = new LinkedList<>();

    @Override
    public void handle(Issue issue) {
      issues.add(issue);
    }

    public List<Issue> getIssues() {
      return issues;
    }

    public void clear() {
      issues.clear();
    }

  }

  protected static WsClient newAdminWsClient(Orchestrator orchestrator) {
    var server = orchestrator.getServer();
    return WsClientFactories.getDefault().newClient(HttpConnector.newBuilder()
      .url(server.getUrl())
      .credentials(com.sonar.orchestrator.container.Server.ADMIN_LOGIN, com.sonar.orchestrator.container.Server.ADMIN_PASSWORD)
      .build());
  }

  protected static ConnectedAnalysisConfiguration createAnalysisConfiguration(String projectKey, String projectDir, String filePath, String... properties) throws IOException {
    final var baseDir = Paths.get("projects/" + projectDir).toAbsolutePath();
    final var path = baseDir.resolve(filePath);
    return ConnectedAnalysisConfiguration.builder()
      .setProjectKey(projectKey)
      .setBaseDir(new File("projects/" + projectDir).toPath().toAbsolutePath())
      .addInputFile(new TestClientInputFile(baseDir, path, false, StandardCharsets.UTF_8))
      .putAllExtraProperties(toMap(properties))
      .build();
  }

  protected static ConnectedAnalysisConfiguration createAnalysisConfiguration(String projectKey, String absoluteFilePath) throws IOException {
    final var path = Paths.get(absoluteFilePath).toAbsolutePath();
    return ConnectedAnalysisConfiguration.builder()
      .setProjectKey(projectKey)
      .setBaseDir(path.getParent())
      .addInputFile(new TestClientInputFile(path.getParent(), path, false, StandardCharsets.UTF_8))
      .build();
  }

  static Map<String, String> toMap(String[] keyValues) {
    Preconditions.checkArgument(keyValues.length % 2 == 0, "Must be an even number of key/values");
    Map<String, String> map = Maps.newHashMap();
    var index = 0;
    while (index < keyValues.length) {
      var key = keyValues[index++];
      var value = keyValues[index++];
      map.put(key, value);
    }
    return map;
  }

  public static HttpClient sqHttpClient() {
    return new SonarLintHttpClientJavaNetImpl(SHARED_CLIENT, SONARLINT_USER, SONARLINT_PWD);
  }

  public static HttpClient sqHttpClientNoAuth() {
    return new SonarLintHttpClientJavaNetImpl(SHARED_CLIENT);
  }

  protected static EndpointParams endpointParams(Orchestrator orchestrator) {
    return endpointParamsNoOrg(orchestrator.getServer().getUrl());
  }

  protected static EndpointParams endpointParamsNoOrg(String url) {
    return endpointParams(url, false, null);
  }

  protected static EndpointParams endpointParams(String url, boolean isSonarCloud, @Nullable String org) {
    return new EndpointParams(url, isSonarCloud, org);
  }

  private static final Pattern MATCH_ALL_WHITESPACES = Pattern.compile("\\s");

  protected static String hash(String codeSnippet) {
    String codeSnippetWithoutWhitespaces = MATCH_ALL_WHITESPACES.matcher(codeSnippet).replaceAll("");
    return DigestUtils.md5Hex(codeSnippetWithoutWhitespaces);
  }

  protected static void analyzeMavenProject(Orchestrator orchestrator, String projectDirName) {
    analyzeMavenProject(orchestrator, projectDirName, Map.of());
  }

  protected static void analyzeMavenProject(Orchestrator orchestrator, String projectDirName, Map<String, String> extraProperties) {
    var projectDir = Paths.get("projects/" + projectDirName).toAbsolutePath();
    var pom = projectDir.resolve("pom.xml");
    orchestrator.executeBuild(MavenBuild.create(pom.toFile())
      .setCleanPackageSonarGoals()
      .setProperty("sonar.login", com.sonar.orchestrator.container.Server.ADMIN_LOGIN)
      .setProperty("sonar.password", com.sonar.orchestrator.container.Server.ADMIN_PASSWORD)
      .setProperties(extraProperties));
  }

  protected QualityProfile getQualityProfile(WsClient adminWsClient, String qualityProfileName) {
    var searchReq = new SearchRequest();
    searchReq.setQualityProfile(qualityProfileName);
    searchReq.setDefaults("false");
    var search = adminWsClient.qualityprofiles().search(searchReq);
    for (QualityProfile profile : search.getProfilesList()) {
      if (profile.getName().equals(qualityProfileName)) {
        return profile;
      }
    }
    throw Failures.instance().failure("Unable to get quality profile " + qualityProfileName);
  }

  protected void deactivateRule(WsClient adminWsClient, QualityProfile qualityProfile, String ruleKey) {
    var request = new PostRequest("/api/qualityprofiles/deactivate_rule")
      .setParam("key", qualityProfile.getKey())
      .setParam("rule", ruleKey);
    try (var response = adminWsClient.wsConnector().call(request)) {
      assertTrue(response.isSuccessful(), "Unable to deactivate rule");
    }
  }

  protected static List<String> getIssueKeys(WsClient adminWsClient, String ruleKey) {
    var searchReq = new org.sonarqube.ws.client.issues.SearchRequest();
    searchReq.setRules(List.of(ruleKey));
    var response = adminWsClient.issues().search(searchReq);
    return response.getIssuesList().stream().map(Issues.Issue::getKey).collect(Collectors.toList());
  }

  protected static void resolveIssueAsWontFix(WsClient adminWsClient, String issueKey) {
    changeIssueStatus(adminWsClient, issueKey, "wontfix");
  }

  protected static void reopenIssue(WsClient adminWsClient, String issueKey) {
    changeIssueStatus(adminWsClient, issueKey, "reopen");
  }

  protected static void changeIssueStatus(WsClient adminWsClient, String issueKey, String status) {
    var request = new PostRequest("/api/issues/do_transition")
      .setParam("issue", issueKey)
      .setParam("transition", status);
    try (var response = adminWsClient.wsConnector().call(request)) {
      assertTrue(response.isSuccessful(), "Unable to resolve issue");
    }
  }

  protected static void provisionProject(Orchestrator orchestrator, String projectKey, String projectName) {
    orchestrator.getServer()
      .newHttpCall("/api/projects/create")
      .setMethod(HttpMethod.POST)
      .setAdminCredentials()
      .setParam("project", projectKey)
      .setParam("name", projectName)
      .setParam("mainBranch", MAIN_BRANCH_NAME)
      .execute();
  }

  protected static String javaRuleKey(Orchestrator orchestrator, String key) {
    // Starting from SonarJava 6.0 (embedded in SQ 8.2), rule repository has been changed
    return orchestrator.getServer().version().isGreaterThanOrEquals(8, 2) ? ("java:" + key) : ("squid:" + key);
  }

}
