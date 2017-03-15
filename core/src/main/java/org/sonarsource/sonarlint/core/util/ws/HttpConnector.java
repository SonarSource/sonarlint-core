/*
 * SonarLint Core - Implementation
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
package org.sonarsource.sonarlint.core.util.ws;

import java.io.IOException;
import java.net.Proxy;
import java.util.Map;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.Call;
import okhttp3.Credentials;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Strings.nullToEmpty;
import static java.lang.String.format;

/**
 * Connect to any SonarQube server available through HTTP or HTTPS.
 * <p>TLS 1.0, 1.1 and 1.2 are supported on both Java 7 and 8. SSLv3 is not supported.</p>
 * <p>The JVM system proxies are used.</p>
 */
public class HttpConnector implements WsConnector {

  public static final int DEFAULT_CONNECT_TIMEOUT_MILLISECONDS = 30_000;
  public static final int DEFAULT_READ_TIMEOUT_MILLISECONDS = 60_000;

  /**
   * Base URL with trailing slash, for instance "https://localhost/sonarqube/".
   * It is required for further usage of {@link HttpUrl#resolve(String)}.
   */
  private final HttpUrl baseUrl;
  private final String credentials;
  private final OkHttpClient okHttpClient;

  private HttpConnector(Builder builder) {
    this.baseUrl = HttpUrl.parse(builder.url.endsWith("/") ? builder.url : format("%s/", builder.url));
    checkArgument(this.baseUrl != null, "Malformed URL: '%s'", builder.url);

    OkHttpClientBuilder okHttpClientBuilder = new OkHttpClientBuilder();
    okHttpClientBuilder.setUserAgent(builder.userAgent);

    if (isNullOrEmpty(builder.login)) {
      // no login nor access token
      this.credentials = null;
    } else {
      // password is null when login represents an access token. In this case
      // the Basic credentials consider an empty password.
      this.credentials = Credentials.basic(builder.login, nullToEmpty(builder.password));
    }
    okHttpClientBuilder.setProxy(builder.proxy);
    okHttpClientBuilder.setProxyLogin(builder.proxyLogin);
    okHttpClientBuilder.setProxyPassword(builder.proxyPassword);
    okHttpClientBuilder.setConnectTimeoutMs(builder.connectTimeoutMs);
    okHttpClientBuilder.setReadTimeoutMs(builder.readTimeoutMs);
    okHttpClientBuilder.setSSLSocketFactory(builder.sslSocketFactory);
    okHttpClientBuilder.setTrustManager(builder.sslTrustManager);
    this.okHttpClient = okHttpClientBuilder.build();
  }

  @Override
  public String baseUrl() {
    return baseUrl.url().toExternalForm();
  }

  public OkHttpClient okHttpClient() {
    return okHttpClient;
  }

  @Override
  public WsResponse call(WsRequest httpRequest) {
    if (httpRequest instanceof GetRequest) {
      return get((GetRequest) httpRequest);
    }
    if (httpRequest instanceof PostRequest) {
      return post((PostRequest) httpRequest);
    }
    throw new IllegalArgumentException(format("Unsupported implementation: %s", httpRequest.getClass()));
  }

  private WsResponse get(GetRequest getRequest) {
    HttpUrl.Builder urlBuilder = prepareUrlBuilder(getRequest);
    Request.Builder okRequestBuilder = prepareOkRequestBuilder(getRequest, urlBuilder).get();
    return doCall(okRequestBuilder.build());
  }

  private WsResponse post(PostRequest postRequest) {
    HttpUrl.Builder urlBuilder = prepareUrlBuilder(postRequest);
    Request.Builder okRequestBuilder = prepareOkRequestBuilder(postRequest, urlBuilder);

    Map<String, PostRequest.Part> parts = postRequest.getParts();
    if (parts.isEmpty()) {
      okRequestBuilder.post(RequestBody.create(null, ""));
    } else {
      MultipartBody.Builder bodyBuilder = new MultipartBody.Builder();
      bodyBuilder.setType(MultipartBody.FORM);
      for (Map.Entry<String, PostRequest.Part> param : parts.entrySet()) {
        PostRequest.Part part = param.getValue();
        bodyBuilder.addPart(
          Headers.of("Content-Disposition", format("form-data; name=\"%s\"", param.getKey())),
          RequestBody.create(MediaType.parse(part.getMediaType()), part.getFile()));
      }
      okRequestBuilder.post(bodyBuilder.build());
    }

    return doCall(okRequestBuilder.build());
  }

  public WsResponse post(PostRequest postRequest, String body) {
    HttpUrl.Builder urlBuilder = prepareUrlBuilder(postRequest);
    Request.Builder okRequestBuilder = prepareOkRequestBuilder(postRequest, urlBuilder);
    okRequestBuilder.post(RequestBody.create(MediaType.parse(postRequest.getMediaType()), body));
    return doCall(okRequestBuilder.build());
  }
  
  public WsResponse delete(DeleteRequest deleteRequest, String body) {
    HttpUrl.Builder urlBuilder = prepareUrlBuilder(deleteRequest);
    Request.Builder okRequestBuilder = prepareOkRequestBuilder(deleteRequest, urlBuilder);
    okRequestBuilder.delete(RequestBody.create(MediaType.parse(deleteRequest.getMediaType()), body));
    return doCall(okRequestBuilder.build());
  }

  private HttpUrl.Builder prepareUrlBuilder(WsRequest wsRequest) {
    String path = wsRequest.getPath();
    HttpUrl.Builder urlBuilder = baseUrl
      .resolve(path.startsWith("/") ? path.replaceAll("^(/)+", "") : path)
      .newBuilder();
    wsRequest.getParameters().getKeys()
      .forEach(key -> wsRequest.getParameters().getValues(key)
        .forEach(value -> urlBuilder.addQueryParameter(key, value)));

    return urlBuilder;
  }

  private Request.Builder prepareOkRequestBuilder(WsRequest getRequest, HttpUrl.Builder urlBuilder) {
    Request.Builder okHttpRequestBuilder = new Request.Builder()
      .url(urlBuilder.build())
      .addHeader("Accept", getRequest.getMediaType())
      .addHeader("Accept-Charset", "UTF-8");
    if (credentials != null) {
      okHttpRequestBuilder.header("Authorization", credentials);
    }
    return okHttpRequestBuilder;
  }

  private OkHttpResponse doCall(Request okRequest) {
    Call call = okHttpClient.newCall(okRequest);
    try {
      Response okResponse = call.execute();
      return new OkHttpResponse(okResponse);
    } catch (IOException e) {
      throw new IllegalStateException("Fail to request " + okRequest.url(), e);
    }
  }

  /**
   * @since 5.5
   */
  public static Builder newBuilder() {
    return new Builder();
  }

  public static class Builder {
    private String url;
    private String userAgent;
    private String login;
    private String password;
    private Proxy proxy;
    private String proxyLogin;
    private String proxyPassword;
    private int connectTimeoutMs = DEFAULT_CONNECT_TIMEOUT_MILLISECONDS;
    private int readTimeoutMs = DEFAULT_READ_TIMEOUT_MILLISECONDS;
    private SSLSocketFactory sslSocketFactory = null;
    private X509TrustManager sslTrustManager = null;

    /**
     * Private since 5.5.
     * @see HttpConnector#newBuilder()
     */
    private Builder() {
    }

    /**
     * Optional User  Agent
     */
    public Builder userAgent(@Nullable String userAgent) {
      this.userAgent = userAgent;
      return this;
    }

    /**
     * Mandatory HTTP server URL, eg "http://localhost:9000"
     */
    public Builder url(String url) {
      this.url = url;
      return this;
    }

    /**
     * Optional login/password, for example "admin"
     */
    public Builder credentials(@Nullable String login, @Nullable String password) {
      this.login = login;
      this.password = password;
      return this;
    }

    /**
     * Optional access token, for example {@code "ABCDE"}. Alternative to {@link #credentials(String, String)}
     */
    public Builder token(@Nullable String token) {
      this.login = token;
      this.password = null;
      return this;
    }

    /**
     * Sets a specified timeout value, in milliseconds, to be used when opening HTTP connection.
     * A timeout of zero is interpreted as an infinite timeout. Default value is {@link #DEFAULT_CONNECT_TIMEOUT_MILLISECONDS}
     */
    public Builder connectTimeoutMilliseconds(int i) {
      this.connectTimeoutMs = i;
      return this;
    }

    /**
     * Optional SSL socket factory with which SSL sockets will be created to establish SSL connections.
     * If not set, a default SSL socket factory will be used, base d on the JVM's default key store.
     */
    public Builder setSSLSocketFactory(@Nullable SSLSocketFactory sslSocketFactory) {
      this.sslSocketFactory = sslSocketFactory;
      return this;
    }

    /**
     * Optional SSL trust manager used to validate certificates.
     * If not set, a default system trust manager will be used, based on the JVM's default truststore.
     */
    public Builder setTrustManager(@Nullable X509TrustManager sslTrustManager) {
      this.sslTrustManager = sslTrustManager;
      return this;
    }

    /**
     * Sets the read timeout to a specified timeout, in milliseconds.
     * A timeout of zero is interpreted as an infinite timeout. Default value is {@link #DEFAULT_READ_TIMEOUT_MILLISECONDS}
     */
    public Builder readTimeoutMilliseconds(int i) {
      this.readTimeoutMs = i;
      return this;
    }

    public Builder proxy(@Nullable Proxy proxy) {
      this.proxy = proxy;
      return this;
    }

    public Builder proxyCredentials(@Nullable String proxyLogin, @Nullable String proxyPassword) {
      this.proxyLogin = proxyLogin;
      this.proxyPassword = proxyPassword;
      return this;
    }

    public HttpConnector build() {
      checkArgument(!isNullOrEmpty(url), "Server URL is not defined");
      return new HttpConnector(this);
    }
  }

}
