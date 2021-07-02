/*
 * SonarLint Core - ITs - Tests
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
package its;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.sonar.orchestrator.Orchestrator;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import org.junit.ClassRule;
import org.junit.rules.TemporaryFolder;
import org.sonarqube.ws.client.HttpConnector;
import org.sonarqube.ws.client.WsClient;
import org.sonarqube.ws.client.WsClientFactories;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedAnalysisConfiguration;
import org.sonarsource.sonarlint.core.serverapi.EndpointParams;
import org.sonarsource.sonarlint.core.serverapi.HttpClient;

public abstract class AbstractConnectedTest {
  protected static final String SONARLINT_USER = "sonarlint";
  protected static final String SONARLINT_PWD = "sonarlintpwd";

  protected static final OkHttpClient CLIENT_NO_AUTH = new OkHttpClient.Builder()
    .addNetworkInterceptor(new UserAgentInterceptor("SonarLint ITs"))
    .build();

  private static final OkHttpClient SQ_CLIENT = CLIENT_NO_AUTH.newBuilder()
    .addNetworkInterceptor(new PreemptiveAuthenticatorInterceptor(Credentials.basic(SONARLINT_USER, SONARLINT_PWD)))
    .build();

  @ClassRule
  public static TemporaryFolder t = new TemporaryFolder();

  protected static final class SonarLintHttpClientOkHttpImpl implements HttpClient {
    private final OkHttpClient okClient;

    public SonarLintHttpClientOkHttpImpl(OkHttpClient okClient) {
      this.okClient = okClient;
    }

    @Override
    public Response post(String url, String contentType, String bodyContent) {
      RequestBody body = RequestBody.create(MediaType.get(contentType), bodyContent);
      Request request = new Request.Builder()
        .url(url)
        .post(body)
        .build();
      return executeRequest(request);
    }

    @Override
    public Response get(String url) {
      Request request = new Request.Builder()
        .url(url)
        .build();
      return executeRequest(request);
    }

    @Override
    public CompletableFuture<Response> getAsync(String url) {
      Request request = new Request.Builder()
        .url(url)
        .build();
      return executeRequestAsync(request);
    }

    private CompletableFuture<Response> executeRequestAsync(Request request) {
      Call call = okClient.newCall(request);
      CompletableFuture<Response> futureResponse = new CompletableFuture<Response>()
        .whenComplete((response, error) -> {
          if (error instanceof CancellationException) {
            call.cancel();
          }
        });
      call.enqueue(new Callback() {
        @Override
        public void onFailure(Call call, IOException e) {
          futureResponse.completeExceptionally(e);
        }

        @Override
        public void onResponse(Call call, okhttp3.Response response) {
          futureResponse.complete(wrap(response));
        }
      });
      return futureResponse;
    }

    @Override
    public Response delete(String url, String contentType, String bodyContent) {
      RequestBody body = RequestBody.create(MediaType.get(contentType), bodyContent);
      Request request = new Request.Builder()
        .url(url)
        .delete(body)
        .build();
      return executeRequest(request);
    }

    private Response executeRequest(Request request) {
      try {
        return wrap(okClient.newCall(request).execute());
      } catch (IOException e) {
        throw new IllegalStateException("Unable to execute request: " + e.getMessage(), e);
      }
    }

    private Response wrap(okhttp3.Response wrapped) {
      return new Response() {

        @Override
        public String url() {
          return wrapped.request().url().toString();
        }

        @Override
        public int code() {
          return wrapped.code();
        }

        @Override
        public void close() {
          wrapped.close();
        }

        @Override
        public String bodyAsString() {
          try (ResponseBody body = wrapped.body()) {
            return body.string();
          } catch (IOException e) {
            throw new IllegalStateException("Unable to read response body: " + e.getMessage(), e);
          }
        }

        @Override
        public InputStream bodyAsStream() {
          return wrapped.body().byteStream();
        }

        @Override
        public String toString() {
          return wrapped.toString();
        }
      };
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
    com.sonar.orchestrator.container.Server server = orchestrator.getServer();
    return WsClientFactories.getDefault().newClient(HttpConnector.newBuilder()
      .url(server.getUrl())
      .credentials(com.sonar.orchestrator.container.Server.ADMIN_LOGIN, com.sonar.orchestrator.container.Server.ADMIN_PASSWORD)
      .build());
  }

  protected ConnectedAnalysisConfiguration createAnalysisConfiguration(String projectKey, String projectDir, String filePath, String... properties) throws IOException {
    final Path baseDir = Paths.get("projects/" + projectDir).toAbsolutePath();
    final Path path = baseDir.resolve(filePath);
    return ConnectedAnalysisConfiguration.builder()
      .setProjectKey(projectKey)
      .setBaseDir(new File("projects/" + projectDir).toPath().toAbsolutePath())
      .addInputFile(new TestClientInputFile(baseDir, path, false, StandardCharsets.UTF_8))
      .putAllExtraProperties(toMap(properties))
      .build();
  }

  protected ConnectedAnalysisConfiguration createAnalysisConfiguration(String projectKey, String absoluteFilePath) throws IOException {
    final Path path = Paths.get(absoluteFilePath).toAbsolutePath();
    return ConnectedAnalysisConfiguration.builder()
      .setProjectKey(projectKey)
      .setBaseDir(path.getParent())
      .addInputFile(new TestClientInputFile(path.getParent(), path, false, StandardCharsets.UTF_8))
      .build();
  }

  static Map<String, String> toMap(String[] keyValues) {
    Preconditions.checkArgument(keyValues.length % 2 == 0, "Must be an even number of key/values");
    Map<String, String> map = Maps.newHashMap();
    int index = 0;
    while (index < keyValues.length) {
      String key = keyValues[index++];
      String value = keyValues[index++];
      map.put(key, value);
    }
    return map;
  }

  public static HttpClient sqHttpClient() {
    return new SonarLintHttpClientOkHttpImpl(SQ_CLIENT);
  }

  public static HttpClient sqHttpClientNoAuth() {
    return new SonarLintHttpClientOkHttpImpl(CLIENT_NO_AUTH);
  }

  protected EndpointParams endpointParams(Orchestrator orchestrator) {
    return endpointParamsNoOrg(orchestrator.getServer().getUrl());
  }

  protected EndpointParams endpointParamsNoOrg(String url) {
    return endpointParams(url, false, null);
  }

  protected EndpointParams endpointParams(String url, boolean isSonarCloud, @Nullable String org) {
    return new EndpointParams(url, isSonarCloud, org);
  }
}
