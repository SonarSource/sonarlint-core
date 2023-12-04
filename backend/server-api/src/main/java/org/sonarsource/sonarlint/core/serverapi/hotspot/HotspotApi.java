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
package org.sonarsource.sonarlint.core.serverapi.hotspot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.HotspotReviewStatus;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.TextRangeWithHash;
import org.sonarsource.sonarlint.core.commons.Version;
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.commons.progress.ProgressMonitor;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.UrlUtils;
import org.sonarsource.sonarlint.core.serverapi.exception.UnexpectedBodyException;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Common;
import org.sonarsource.sonarlint.core.serverapi.proto.sonarqube.ws.Hotspots;
import org.sonarsource.sonarlint.core.serverapi.source.SourceApi;
import org.sonarsource.sonarlint.core.serverapi.util.ServerApiUtils;

import static org.sonarsource.sonarlint.core.http.HttpClient.FORM_URL_ENCODED_CONTENT_TYPE;
import static org.sonarsource.sonarlint.core.serverapi.UrlUtils.urlEncode;
import static org.sonarsource.sonarlint.core.serverapi.util.ProtobufUtil.readMessages;

public class HotspotApi {
  private static final SonarLintLogger LOG = SonarLintLogger.get();

  // the rule key is returned only on SQ, since 9.7
  // without that info, hotspot tracking is almost impossible
  public static final Version TRACKING_COMPATIBLE_MIN_SQ_VERSION = Version.create("9.7");
  public static final Version MIN_SQ_VERSION_SUPPORTING_PULL = Version.create("10.1");

  private static final String HOTSPOTS_SEARCH_API_URL = "/api/hotspots/search.protobuf";
  private static final String HOTSPOTS_SHOW_API_URL = "/api/hotspots/show.protobuf";
  private static final String HOTSPOTS_PULL_API_URL = "/api/hotspots/pull";
  private static final String PROJECT_KEY_QUERY_PARAM = "?projectKey=";

  private final ServerApiHelper helper;

  public HotspotApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public boolean permitsTracking(Supplier<Version> serverVersion) {
    return permitsTracking(helper.isSonarCloud(), serverVersion);
  }

  public static boolean permitsTracking(boolean isSonarCloud, Supplier<Version> serverVersion) {
    return isSonarCloud || serverVersion.get().compareToIgnoreQualifier(TRACKING_COMPATIBLE_MIN_SQ_VERSION) >= 0;
  }

  public CompletableFuture<Void> changeStatusAsync(String hotspotKey, HotspotReviewStatus status) {
    var isReviewed = status.isReviewed();
    var webApiStatus = isReviewed ? "REVIEWED" : "TO_REVIEW";
    var body = "hotspot=" + urlEncode(hotspotKey) + "&status=" + urlEncode(webApiStatus);
    if (isReviewed) {
      body += "&resolution=" + urlEncode(status.name());
    }
    return helper.postAsync("api/hotspots/change_status", FORM_URL_ENCODED_CONTENT_TYPE, body)
      .thenAccept(response -> {
        // no data, return void
      });
  }

  public Collection<ServerHotspot> getAll(String projectKey, String branchName, ProgressMonitor progress) {
    return searchHotspots(getSearchUrl(projectKey, null, branchName), progress);
  }

  public Collection<ServerHotspot> getFromFile(String projectKey, String filePath, String branchName) {
    return searchHotspots(getSearchUrl(projectKey, filePath, branchName), new ProgressMonitor(null));
  }

  public HotspotApi.HotspotsPullResult pullHotspots(String projectKey, String branchName, Set<Language> enabledLanguages, @Nullable Long changedSince) {
    return ServerApiHelper.processTimed(
      () -> helper.get(getPullHotspotsUrl(projectKey, branchName, enabledLanguages, changedSince)),
      response -> {
        var input = response.bodyAsStream();
        var timestamp = Hotspots.HotspotPullQueryTimestamp.parseDelimitedFrom(input);
        return new HotspotApi.HotspotsPullResult(timestamp, readMessages(input, Hotspots.HotspotLite.parser()));
      },
      duration -> LOG.debug("Pulled issues in {}ms", duration));
  }

  public static class HotspotsPullResult {
    private final Hotspots.HotspotPullQueryTimestamp timestamp;
    private final List<Hotspots.HotspotLite> hotspots;

    public HotspotsPullResult(Hotspots.HotspotPullQueryTimestamp timestamp, List<Hotspots.HotspotLite> hotspots) {
      this.timestamp = timestamp;
      this.hotspots = hotspots;
    }

    public Hotspots.HotspotPullQueryTimestamp getTimestamp() {
      return timestamp;
    }

    public List<Hotspots.HotspotLite> getHotspots() {
      return hotspots;
    }
  }

  private static String getPullHotspotsUrl(String projectKey, String branchName, Set<Language> enabledLanguages, @Nullable Long changedSince) {
    var enabledLanguageKeys = enabledLanguages.stream().map(Language::getLanguageKey).collect(Collectors.joining(","));
    var url = new StringBuilder()
      .append(HOTSPOTS_PULL_API_URL)
      .append(PROJECT_KEY_QUERY_PARAM)
      .append(UrlUtils.urlEncode(projectKey))
      .append("&branchName=")
      .append(UrlUtils.urlEncode(branchName));
    if (!enabledLanguageKeys.isEmpty()) {
      url.append("&languages=").append(enabledLanguageKeys);
    }
    if (changedSince != null) {
      url.append("&changedSince=").append(changedSince);
    }
    return url.toString();
  }

  public boolean supportHotspotsPull(Supplier<Version> serverVersion) {
    return supportHotspotsPull(helper.isSonarCloud(), serverVersion.get());
  }

  public static boolean supportHotspotsPull(boolean isSonarCloud, Version serverVersion) {
    return !isSonarCloud && serverVersion.compareToIgnoreQualifier(HotspotApi.MIN_SQ_VERSION_SUPPORTING_PULL) >= 0;
  }

  private Collection<ServerHotspot> searchHotspots(String searchUrl, ProgressMonitor progress) {
    Collection<ServerHotspot> hotspots = new ArrayList<>();
    Map<String, String> componentPathsByKey = new HashMap<>();
    helper.getPaginated(
      searchUrl,
      Hotspots.SearchWsResponse::parseFrom,
      r -> r.getPaging().getTotal(),
      r -> {
        componentPathsByKey.clear();
        componentPathsByKey.putAll(r.getComponentsList().stream().collect(Collectors.toMap(Hotspots.Component::getKey, Hotspots.Component::getPath)));
        return r.getHotspotsList();
      },
      hotspot -> {
        var filePath = componentPathsByKey.get(hotspot.getComponent());
        if (filePath != null) {
          hotspots.add(adapt(hotspot, filePath));
        } else {
          LOG.error("Error while fetching security hotspots, the component '" + hotspot.getComponent() + "' is missing");
        }
      },
      false,
      progress);
    return hotspots;
  }

  private static String getSearchUrl(String projectKey, @Nullable String filePath, String branchName) {
    return HOTSPOTS_SEARCH_API_URL
      + PROJECT_KEY_QUERY_PARAM + urlEncode(projectKey)
      + (filePath != null ? ("&files=" + urlEncode(filePath)) : "")
      + "&branch=" + urlEncode(branchName);
  }

  public CompletableFuture<ServerHotspotDetails> show(String hotspotKey) {
    return helper.getAsync(getShowUrl(hotspotKey)).thenApply(response -> {
      try (response; var is = response.bodyAsStream()) {
        return adapt(Hotspots.ShowWsResponse.parseFrom(is), null);
      } catch (IOException e) {
        throw new UnexpectedBodyException(e);
      }
    });
  }

  public Optional<ServerHotspotDetails> fetch(String hotspotKey) {
    Hotspots.ShowWsResponse response;
    try (var wsResponse = helper.get(getShowUrl(hotspotKey)); var is = wsResponse.bodyAsStream()) {
      response = Hotspots.ShowWsResponse.parseFrom(is);
    } catch (Exception e) {
      LOG.error("Error while fetching security hotspot", e);
      return Optional.empty();
    }
    var fileKey = response.getComponent().getKey();
    var source = new SourceApi(helper).getRawSourceCode(fileKey);
    String codeSnippet;
    if (source.isPresent()) {
      try {
        codeSnippet = ServerApiUtils.extractCodeSnippet(source.get(), response.getTextRange());
      } catch (Exception e) {
        LOG.debug("Unable to compute code snippet of '" + fileKey + "' for text range: " + response.getTextRange(), e);
        codeSnippet = null;
      }
    } else {
      codeSnippet = null;
    }
    return Optional.of(adapt(response, codeSnippet));
  }

  private static ServerHotspotDetails adapt(Hotspots.ShowWsResponse hotspot, @Nullable String codeSnippet) {
    return new ServerHotspotDetails(
      hotspot.getMessage(),
      hotspot.getComponent().getPath(),
      convertTextRange(hotspot.getTextRange()),
      hotspot.getAuthor(),
      ServerHotspotDetails.Status.valueOf(hotspot.getStatus()),
      hotspot.hasResolution() ? ServerHotspotDetails.Resolution.valueOf(hotspot.getResolution()) : null,
      adapt(hotspot.getRule()),
      codeSnippet, hotspot.getCanChangeStatus());
  }

  private static ServerHotspotDetails.Rule adapt(Hotspots.Rule rule) {
    return new ServerHotspotDetails.Rule(rule.getKey(), rule.getName(), rule.getSecurityCategory(),
      VulnerabilityProbability.valueOf(rule.getVulnerabilityProbability()),
      rule.getRiskDescription(), rule.getVulnerabilityDescription(), rule.getFixRecommendations());
  }

  private static ServerHotspot adapt(Hotspots.SearchWsResponse.Hotspot hotspot, String filePath) {
    return new ServerHotspot(
      hotspot.getKey(),
      hotspot.getRuleKey(),
      hotspot.getMessage(),
      filePath,
      convertTextRange(hotspot.getTextRange()),
      ServerApiUtils.parseOffsetDateTime(hotspot.getCreationDate()).toInstant(),
      getStatus(hotspot),
      VulnerabilityProbability.valueOf(hotspot.getVulnerabilityProbability()),
      hotspot.getAssignee());
  }

  private static HotspotReviewStatus getStatus(Hotspots.SearchWsResponse.Hotspot hotspot) {
    var status = hotspot.getStatus();
    var resolution = hotspot.hasResolution() ? hotspot.getResolution() : null;
    return HotspotReviewStatus.fromStatusAndResolution(status, resolution);
  }

  private static String getShowUrl(String hotspotKey) {
    return HOTSPOTS_SHOW_API_URL
      + "?hotspot=" + urlEncode(hotspotKey);
  }

  private static TextRangeWithHash convertTextRange(Common.TextRange textRange) {
    return new TextRangeWithHash(textRange.getStartLine(), textRange.getStartOffset(), textRange.getEndLine(), textRange.getEndOffset(), "");
  }
}
