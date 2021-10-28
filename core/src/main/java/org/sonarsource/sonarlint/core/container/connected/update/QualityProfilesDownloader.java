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
package org.sonarsource.sonarlint.core.container.connected.update;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.sonarsource.sonarlint.core.container.storage.QualityProfileStore;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverapi.ServerApi;
import org.sonarsource.sonarlint.core.serverapi.ServerApiHelper;
import org.sonarsource.sonarlint.core.serverapi.qualityprofile.QualityProfileApi;

public class QualityProfilesDownloader {
  private final QualityProfileApi qualityProfileApi;
  private final QualityProfileStore qualityProfileStore;

  public QualityProfilesDownloader(ServerApiHelper serverApiHelper, QualityProfileStore qualityProfileStore) {
    this.qualityProfileApi = new ServerApi(serverApiHelper).qualityProfile();
    this.qualityProfileStore = qualityProfileStore;
  }

  public List<UpdateEvent> fetchQualityProfiles(QualityProfileStore currentQualityProfileStore) {
    var previousQProfiles = currentQualityProfileStore.getAllOrEmpty();
    var newQualityProfiles = qualityProfileApi.getQualityProfiles();
    List<UpdateEvent> events = new ArrayList<>(diffProfiles(previousQProfiles.getQprofilesByKeyMap(), newQualityProfiles.getQprofilesByKeyMap()));
    events.addAll(diffDefaultProfiles(previousQProfiles.getDefaultQProfilesByLanguageMap(), newQualityProfiles.getDefaultQProfilesByLanguageMap()));
    qualityProfileStore.store(newQualityProfiles);
    return events;
  }

  private static List<UpdateEvent> diffProfiles(Map<String, Sonarlint.QProfiles.QProfile> previousQualityProfilesByKey,
    Map<String, Sonarlint.QProfiles.QProfile> qProfilesByKeyMap) {
    List<UpdateEvent> events = new ArrayList<>();
    for (Map.Entry<String, Sonarlint.QProfiles.QProfile> newQualityProfile : qProfilesByKeyMap.entrySet()) {
      String newQualityProfileKey = newQualityProfile.getKey();
      Sonarlint.QProfiles.QProfile newProfile = newQualityProfile.getValue();
      if (previousQualityProfilesByKey.containsKey(newQualityProfileKey)) {
        Sonarlint.QProfiles.QProfile previousProfile = previousQualityProfilesByKey.get(newQualityProfileKey);
        if (!previousProfile.equals(newProfile)) {
          events.add(new QualityProfileChanged(newQualityProfileKey, previousProfile, newProfile));
        }
      } else {
        events.add(new QualityProfileAdded(newQualityProfileKey, newProfile));
      }
    }
    for (Map.Entry<String, Sonarlint.QProfiles.QProfile> previousQualityProfile : previousQualityProfilesByKey.entrySet()) {
      String previousKey = previousQualityProfile.getKey();
      if (!qProfilesByKeyMap.containsKey(previousKey)) {
        events.add(new QualityProfileRemoved(previousKey, previousQualityProfile.getValue()));
      }
    }
    return events;
  }

  private static List<UpdateEvent> diffDefaultProfiles(Map<String, String> previousDefaultQProfilesByLanguage, Map<String, String> defaultQProfilesByLanguageMap) {
    List<UpdateEvent> events = new ArrayList<>();
    for (Map.Entry<String, String> newDefaultProfileKeyByLanguage : defaultQProfilesByLanguageMap.entrySet()) {
      String languageKey = newDefaultProfileKeyByLanguage.getKey();
      String profileKey = newDefaultProfileKeyByLanguage.getValue();
      if (previousDefaultQProfilesByLanguage.containsKey(languageKey)) {
        if (!previousDefaultQProfilesByLanguage.get(languageKey).equals(profileKey)) {
          events.add(new DefaultQualityProfileChanged(languageKey, profileKey));
        }
      } else {
        events.add(new DefaultQualityProfileChanged(languageKey, profileKey));
      }
    }
    return events;
  }

  public static class QualityProfileAdded implements UpdateEvent {

    private final String key;
    private final Sonarlint.QProfiles.QProfile profile;

    public QualityProfileAdded(String key, Sonarlint.QProfiles.QProfile profile) {
      this.key = key;
      this.profile = profile;
    }

    public String getKey() {
      return key;
    }

    public Sonarlint.QProfiles.QProfile getProfile() {
      return profile;
    }
  }

  public static class QualityProfileRemoved implements UpdateEvent {

    private final String key;
    private final Sonarlint.QProfiles.QProfile profile;

    public QualityProfileRemoved(String key, Sonarlint.QProfiles.QProfile profile) {
      this.key = key;
      this.profile = profile;
    }

    public String getKey() {
      return key;
    }

    public Sonarlint.QProfiles.QProfile getProfile() {
      return profile;
    }
  }

  public static class QualityProfileChanged implements UpdateEvent {

    private final String key;
    private final Sonarlint.QProfiles.QProfile oldProfile;
    private final Sonarlint.QProfiles.QProfile newProfile;

    public QualityProfileChanged(String key, Sonarlint.QProfiles.QProfile oldProfile, Sonarlint.QProfiles.QProfile newProfile) {
      this.key = key;
      this.oldProfile = oldProfile;
      this.newProfile = newProfile;
    }

    public String getKey() {
      return key;
    }

    public Sonarlint.QProfiles.QProfile getOldProfile() {
      return oldProfile;
    }

    public Sonarlint.QProfiles.QProfile getNewProfile() {
      return newProfile;
    }
  }

  public static class DefaultQualityProfileChanged implements UpdateEvent {

    private final String languageKey;
    private final String profileKey;

    public DefaultQualityProfileChanged(String languageKey, String profileKey) {
      this.languageKey = languageKey;
      this.profileKey = profileKey;
    }

    public String getLanguageKey() {
      return languageKey;
    }

    public String getProfileKey() {
      return profileKey;
    }
  }
}
