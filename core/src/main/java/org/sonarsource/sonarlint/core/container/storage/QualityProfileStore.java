/*
 * SonarLint Core - Implementation
 * Copyright (C) 2016-2022 SonarSource SA
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
package org.sonarsource.sonarlint.core.container.storage;

import java.util.ArrayList;
import java.util.List;
import org.sonarsource.sonarlint.core.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverapi.qualityprofile.QualityProfile;

public class QualityProfileStore {
  public static final String QUALITY_PROFILES_PB = "quality_profiles.pb";

  private final StorageFolder storageFolder;
  private final RWLock rwLock = new RWLock();

  public QualityProfileStore(StorageFolder storageFolder) {
    this.storageFolder = storageFolder;
  }

  public void store(List<QualityProfile> qualityProfiles) {
    Sonarlint.QProfiles protoQualityProfiles = adapt(qualityProfiles);
    rwLock.write(() -> storageFolder.writeAction(dest -> ProtobufUtil.writeToFile(protoQualityProfiles, dest.resolve(QUALITY_PROFILES_PB))));
  }

  public List<QualityProfile> getAll() {
    var protoQualityProfiles = rwLock.read(() -> storageFolder.readAction(source -> ProtobufUtil.readFile(source.resolve(QUALITY_PROFILES_PB), Sonarlint.QProfiles.parser())));
    return adapt(protoQualityProfiles);
  }

  private static Sonarlint.QProfiles adapt(List<QualityProfile> qualityProfiles) {
    var qProfileBuilder = Sonarlint.QProfiles.newBuilder();
    for (var qp : qualityProfiles) {
      var qpBuilder = Sonarlint.QProfiles.QProfile.newBuilder();
      qpBuilder.setKey(qp.getKey());
      qpBuilder.setName(qp.getName());
      qpBuilder.setLanguage(qp.getLanguage());
      qpBuilder.setLanguageName(qp.getLanguageName());
      qpBuilder.setActiveRuleCount(qp.getActiveRuleCount());
      qpBuilder.setRulesUpdatedAt(qp.getRulesUpdatedAt());
      qpBuilder.setUserUpdatedAt(qp.getUserUpdatedAt());

      qProfileBuilder.putQprofilesByKey(qp.getKey(), qpBuilder.build());
      if (qp.isDefault()) {
        qProfileBuilder.putDefaultQProfilesByLanguage(qp.getLanguage(), qp.getKey());
      }
    }
    return qProfileBuilder.build();
  }

  private static List<QualityProfile> adapt(Sonarlint.QProfiles protoQualityProfiles) {
    List<QualityProfile> qualityProfiles = new ArrayList<>();
    for (var protoQualityProfile : protoQualityProfiles.getQprofilesByKeyMap().values()) {
      var isDefault = protoQualityProfile.getKey().equals(protoQualityProfiles.getDefaultQProfilesByLanguageMap().get(protoQualityProfile.getLanguage()));
      qualityProfiles.add(new QualityProfile(
        isDefault,
        protoQualityProfile.getKey(),
        protoQualityProfile.getName(),
        protoQualityProfile.getLanguage(),
        protoQualityProfile.getLanguageName(),
        protoQualityProfile.getActiveRuleCount(),
        protoQualityProfile.getRulesUpdatedAt(),
        protoQualityProfile.getUserUpdatedAt()
      ));
    }
    return qualityProfiles;
  }

}
