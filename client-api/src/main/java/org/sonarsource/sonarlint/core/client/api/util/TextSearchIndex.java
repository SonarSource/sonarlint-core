/*
 * SonarLint Core - Client API
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
package org.sonarsource.sonarlint.core.client.api.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Indexes text associated to objects, and performs full text search to find matching objects.
 * It is a positional index, so it supports queries consisted of multiple terms, in which case it will find partial term matches in sequence (distance = 1).
 * The result is sorted by score. The score of each term matches is the ratio of the term matches (1 for exact match), 
 * and the global score is the sum of the term's scores in the object divided by the total term frequency in the object.
 * 
 * The generic type should properly implement equals and hashCode.
 * <b>An object cannot be indexed twice</b>. 
 * 
 * Performance of indexing: O(N)
 * Performance of search: O(log N) on the number of indexed terms + O(N) on the number of results
 */
public class TextSearchIndex<T> {
  private static final String SPLIT_PATTERN = "\\W";
  private TreeMap<String, List<DictEntry>> termToObj;
  private Set<T> indexedObjs;
  private Map<T, Integer> objToWordFrequency;

  public TextSearchIndex() {
    clear();
  }

  public int size() {
    return indexedObjs.size();
  }

  public boolean isEmpty() {
    return indexedObjs.isEmpty();
  }

  public void index(T obj, String text) {
    if (indexedObjs.contains(obj)) {
      throw new IllegalArgumentException("Already indexed");
    }
    indexedObjs.add(obj);
    List<String> terms = tokenize(text);

    int i = 0;
    for (String s : terms) {
      addToDictionary(s, i, obj);
      i++;
    }
  }

  /**
   * @return Can be empty, but never null
   */
  public Map<T, Double> search(String query) {
    List<String> terms = tokenize(query);

    if (terms.isEmpty()) {
      return Collections.emptyMap();
    }

    List<SearchResult> matched;

    // positional search
    if (terms.size() > 1) {
      Iterator<String> it = terms.iterator();
      matched = searchTerm(it.next());

      while (it.hasNext()) {
        List<SearchResult> termMatches = searchTerm(it.next());
        matched = matchPositional(matched, termMatches, 1);

        if (matched.isEmpty()) {
          break;
        }
      }

      // simple term search with partial match
    } else {
      matched = searchTerm(terms.get(0));
    }

    // convert results and calc score
    return prepareResult(matched);
  }

  private Map<T, Double> prepareResult(List<SearchResult> entries) {
    Map<T, Double> objToScore = new HashMap<>();

    for (SearchResult e : entries) {
      double score = e.score / objToWordFrequency.get(e.obj);
      Double previousScore = objToScore.get(e.obj);

      if (previousScore == null || previousScore < score) {
        objToScore.put(e.obj, score);
      }
    }

    return objToScore.entrySet().stream()
      .sorted(Map.Entry.<T, Double>comparingByValue().reversed())
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
  }

  /**
   * Returns any term prefixed by the given text
   */
  private List<SearchResult> searchTerm(String termPrefix) {
    List<SearchResult> entries = new LinkedList<>();

    SortedMap<String, List<DictEntry>> tailMap = termToObj.tailMap(termPrefix);
    for (Entry<String, List<DictEntry>> e : tailMap.entrySet()) {
      if (!e.getKey().startsWith(termPrefix)) {
        break;
      }
      double score = ((double) termPrefix.length()) / e.getKey().length();
      e.getValue().stream()
        .map(v -> new SearchResult(score, v.obj, v.tokenIndex))
        .forEach(entries::add);
    }

    return entries;
  }

  public void clear() {
    indexedObjs = new HashSet<>();
    termToObj = new TreeMap<>();
    objToWordFrequency = new HashMap<>();
  }

  /**
   * @return Can be empty, but never null
   */
  public Set<String> getTokens() {
    return Collections.unmodifiableSet(termToObj.keySet());
  }

  private void addToDictionary(String token, int tokenIndex, T obj) {
    List<DictEntry> objects = termToObj.get(token);
    Integer count = objToWordFrequency.get(obj);

    if (objects == null) {
      objects = new LinkedList<>();
      termToObj.put(token, objects);
    }

    if (count == null) {
      count = 0;
    }

    count++;
    objects.add(new DictEntry(obj, tokenIndex));
    objToWordFrequency.put(obj, count);
  }

  private static List<String> tokenize(String text) {
    String[] split = text.split(SPLIT_PATTERN);
    List<String> terms = new ArrayList<>(split.length);

    for (String s : split) {
      if (!s.isEmpty()) {
        terms.add(s.toLowerCase(Locale.ENGLISH));
      }
    }

    return terms;
  }

  private List<SearchResult> matchPositional(List<SearchResult> previousMatches, List<SearchResult> termMatches, int maxDistance) {
    List<SearchResult> matches = new LinkedList<>();

    for (SearchResult e1 : previousMatches) {
      for (SearchResult e2 : termMatches) {
        if (!e1.obj.equals(e2.obj)) {
          continue;
        }

        int dist = e2.lastIdx - e1.lastIdx;
        if (dist > 0 && dist <= maxDistance) {
          e2.score += e1.score;
          matches.add(e2);
        }
      }
    }
    return matches;
  }

  private class SearchResult {
    private double score;
    private T obj;
    private int lastIdx;

    public SearchResult(double score, T obj, int lastIdx) {
      this.score = score;
      this.obj = obj;
      this.lastIdx = lastIdx;
    }
  }

  private class DictEntry {
    T obj;
    int tokenIndex;

    public DictEntry(T obj, int tokenIndex) {
      this.obj = obj;
      this.tokenIndex = tokenIndex;
    }
  }
}
