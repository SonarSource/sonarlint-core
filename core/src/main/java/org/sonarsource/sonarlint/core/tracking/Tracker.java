/*
 * SonarLint Core - Implementation
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonarsource.sonarlint.core.tracking;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * Match and track a collection of issues.
 *
 * @param <R> type of the "raw" trackables that are in the incoming collection
 * @param <B> type of the base trackables that are in the current collection
 */
public class Tracker<R extends Trackable, B extends Trackable> {

  public Tracking<R, B> track(TrackableSupplier<R> rawTrackableSupplier, TrackableSupplier<B> baseTrackableSupplier) {
    Tracking<R, B> tracking = new Tracking<>(rawTrackableSupplier, baseTrackableSupplier);

    // 1. match issues with same rule, same line and same text range hash, but not necessarily with same message
    match(tracking, LineAndTextRangeHashKeyFactory.INSTANCE);

    // 2. match issues with same rule, same message and same text range hash
    match(tracking, TextRangeHashAndMessageKeyFactory.INSTANCE);

    // 3. match issues with same rule, same line and same message
    match(tracking, LineAndMessageKeyFactory.INSTANCE);

    // 4. match issues with same rule and same text range hash but different line and different message.
    // See SONAR-2812
    match(tracking, TextRangeHashKeyFactory.INSTANCE);

    // 5. match issues with same rule, same line and same line hash
    match(tracking, LineAndLineHashKeyFactory.INSTANCE);

    // 6. match issues with same rule and same same line hash
    match(tracking, LineHashKeyFactory.INSTANCE);

    // 7. match issues with same server issue key
    match(tracking, ServerIssueSearchKeyFactory.INSTANCE);

    return tracking;
  }

  private void match(Tracking<R, B> tracking, SearchKeyFactory factory) {
    if (tracking.isComplete()) {
      return;
    }

    Map<SearchKey, List<B>> baseSearch = new HashMap<>();
    for (B base : tracking.getUnmatchedBases()) {
      SearchKey searchKey = factory.apply(base);
      if (!baseSearch.containsKey(searchKey)) {
        baseSearch.put(searchKey, new ArrayList<>());
      }
      baseSearch.get(searchKey).add(base);
    }

    for (R raw : tracking.getUnmatchedRaws()) {
      SearchKey rawKey = factory.apply(raw);
      Collection<B> bases = baseSearch.get(rawKey);
      if (bases != null && !bases.isEmpty()) {
        // TODO taking the first one. Could be improved if there are more than 2 issues on the same line.
        // Message could be checked to take the best one.
        B match = bases.iterator().next();
        tracking.match(raw, match);
        baseSearch.get(rawKey).remove(match);
      }
    }
  }

  private interface SearchKey {
  }

  @FunctionalInterface
  private interface SearchKeyFactory extends Function<Trackable, SearchKey> {
    @Override
    SearchKey apply(Trackable trackable);
  }

  private static class LineAndTextRangeHashKey implements SearchKey {
    private final String ruleKey;
    private final Integer textRangeHash;
    private final Integer line;

    LineAndTextRangeHashKey(Trackable trackable) {
      this.ruleKey = trackable.getRuleKey();
      this.line = trackable.getLine();
      this.textRangeHash = trackable.getTextRangeHash();
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (o == null) {
        return false;
      }
      if (this.getClass() != o.getClass()) {
        return false;
      }
      LineAndTextRangeHashKey that = (LineAndTextRangeHashKey) o;
      // start with most discriminant field
      return Objects.equals(line, that.line)
        && Objects.equals(textRangeHash, that.textRangeHash)
        && ruleKey.equals(that.ruleKey);
    }

    @Override
    public int hashCode() {
      int result = ruleKey.hashCode();
      result = 31 * result + (textRangeHash != null ? textRangeHash.hashCode() : 0);
      result = 31 * result + (line != null ? line.hashCode() : 0);
      return result;
    }
  }

  private enum LineAndTextRangeHashKeyFactory implements SearchKeyFactory {
    INSTANCE;

    @Override
    public SearchKey apply(Trackable t) {
      return new LineAndTextRangeHashKey(t);
    }
  }

  private static class LineAndLineHashKey implements SearchKey {
    private final String ruleKey;
    private final Integer line;
    private final Integer lineHash;

    LineAndLineHashKey(Trackable trackable) {
      this.ruleKey = trackable.getRuleKey();
      this.line = trackable.getLine();
      this.lineHash = trackable.getLineHash();
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (o == null) {
        return false;
      }
      if (this.getClass() != o.getClass()) {
        return false;
      }
      LineAndLineHashKey that = (LineAndLineHashKey) o;
      // start with most discriminant field
      return Objects.equals(line, that.line)
        && Objects.equals(lineHash, that.lineHash)
        && ruleKey.equals(that.ruleKey);
    }

    @Override
    public int hashCode() {
      int result = ruleKey.hashCode();
      result = 31 * result + (lineHash != null ? lineHash.hashCode() : 0);
      result = 31 * result + (line != null ? line.hashCode() : 0);
      return result;
    }
  }

  private enum LineAndLineHashKeyFactory implements SearchKeyFactory {
    INSTANCE;

    @Override
    public SearchKey apply(Trackable t) {
      return new LineAndLineHashKey(t);
    }
  }

  private static class LineHashKey implements SearchKey {
    private final String ruleKey;
    private final Integer lineHash;

    LineHashKey(Trackable trackable) {
      this.ruleKey = trackable.getRuleKey();
      this.lineHash = trackable.getLineHash();
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (o == null) {
        return false;
      }
      if (this.getClass() != o.getClass()) {
        return false;
      }
      LineHashKey that = (LineHashKey) o;
      // start with most discriminant field
      return Objects.equals(lineHash, that.lineHash)
        && ruleKey.equals(that.ruleKey);
    }

    @Override
    public int hashCode() {
      int result = ruleKey.hashCode();
      result = 31 * result + (lineHash != null ? lineHash.hashCode() : 0);
      return result;
    }
  }

  private enum LineHashKeyFactory implements SearchKeyFactory {
    INSTANCE;

    @Override
    public SearchKey apply(Trackable t) {
      return new LineHashKey(t);
    }
  }

  private static class TextRangeHashAndMessageKey implements SearchKey {
    private final String ruleKey;
    private final String message;
    private final Integer textRangeHash;

    TextRangeHashAndMessageKey(Trackable trackable) {
      this.ruleKey = trackable.getRuleKey();
      this.message = trackable.getMessage();
      this.textRangeHash = trackable.getTextRangeHash();
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (o == null) {
        return false;
      }
      if (this.getClass() != o.getClass()) {
        return false;
      }
      TextRangeHashAndMessageKey that = (TextRangeHashAndMessageKey) o;
      // start with most discriminant field
      return Objects.equals(textRangeHash, that.textRangeHash)
        && message.equals(that.message)
        && ruleKey.equals(that.ruleKey);
    }

    @Override
    public int hashCode() {
      int result = ruleKey.hashCode();
      result = 31 * result + message.hashCode();
      result = 31 * result + (textRangeHash != null ? textRangeHash.hashCode() : 0);
      return result;
    }
  }

  private enum TextRangeHashAndMessageKeyFactory implements SearchKeyFactory {
    INSTANCE;

    @Override
    public SearchKey apply(Trackable t) {
      return new TextRangeHashAndMessageKey(t);
    }
  }

  private static class LineAndMessageKey implements SearchKey {
    private final String ruleKey;
    private final String message;
    private final Integer line;

    LineAndMessageKey(Trackable trackable) {
      this.ruleKey = trackable.getRuleKey();
      this.message = trackable.getMessage();
      this.line = trackable.getLine();
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (o == null) {
        return false;
      }
      if (this.getClass() != o.getClass()) {
        return false;
      }
      LineAndMessageKey that = (LineAndMessageKey) o;
      // start with most discriminant field
      return Objects.equals(line, that.line)
        && message.equals(that.message)
        && ruleKey.equals(that.ruleKey);
    }

    @Override
    public int hashCode() {
      int result = ruleKey.hashCode();
      result = 31 * result + message.hashCode();
      result = 31 * result + (line != null ? line.hashCode() : 0);
      return result;
    }
  }

  private enum LineAndMessageKeyFactory implements SearchKeyFactory {
    INSTANCE;

    @Override
    public SearchKey apply(Trackable t) {
      return new LineAndMessageKey(t);
    }
  }

  private static class TextRangeHashKey implements SearchKey {
    private final String ruleKey;
    private final Integer textRangeHash;

    TextRangeHashKey(Trackable trackable) {
      this.ruleKey = trackable.getRuleKey();
      this.textRangeHash = trackable.getTextRangeHash();
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (o == null) {
        return false;
      }
      if (this.getClass() != o.getClass()) {
        return false;
      }
      TextRangeHashKey that = (TextRangeHashKey) o;
      // start with most discriminant field
      return Objects.equals(textRangeHash, that.textRangeHash)
        && ruleKey.equals(that.ruleKey);
    }

    @Override
    public int hashCode() {
      int result = ruleKey.hashCode();
      result = 31 * result + (textRangeHash != null ? textRangeHash.hashCode() : 0);
      return result;
    }
  }

  private enum TextRangeHashKeyFactory implements SearchKeyFactory {
    INSTANCE;

    @Override
    public SearchKey apply(Trackable t) {
      return new TextRangeHashKey(t);
    }
  }

  private static class ServerIssueSearchKey implements SearchKey {
    private final String serverIssueKey;

    ServerIssueSearchKey(Trackable trackable) {
      serverIssueKey = trackable.getServerIssueKey();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      ServerIssueSearchKey that = (ServerIssueSearchKey) o;

      return !isBlank(serverIssueKey) && !isBlank(that.serverIssueKey) && serverIssueKey.equals(that.serverIssueKey);
    }

    private static boolean isBlank(String s) {
      return s == null || s.isEmpty();
    }

    @Override
    public int hashCode() {
      return serverIssueKey != null ? serverIssueKey.hashCode() : 0;
    }
  }

  private enum ServerIssueSearchKeyFactory implements SearchKeyFactory {
    INSTANCE;

    @Override
    public SearchKey apply(Trackable trackable) {
      return new ServerIssueSearchKey(trackable);
    }
  }
}
