/*
 * SonarLint Core - Client API
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
package org.sonarsource.sonarlint.core.client.api.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Map.Entry;

/**
 * Indexes text associated to objects, and performs full text search to find matching objects.
 * It is a positional index, so it supports queries consisted of multiple terms, in which case it will find the exact terms in sequence (distance = 1).
 * When the query is a single term, it will match any term that begins by the query. 
 * The result is sorted by score. The score is the number of matches in the object divided by the total term frequency in the object.
 * 
 * The generic type should properly implement equals and hashCode.
 * <b>An object cannot be indexed twice</b>. 
 * 
 * Performance of indexing: O(N)
 * Performance of search: O(log N) on the number of indexed terms + O(N) on the number of results
 */
public class TextSearchIndex<T> {
  private static final String SPLIT_PATTERN = "\\W";
  private TreeMap<String, List<DictEntry>> wordToObj;
  private Set<T> indexedObjs;
  private Map<T, Integer> objToWordFrequency;

  public TextSearchIndex() {
    clear();
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
  public List<T> search(String query) {
    List<String> terms = tokenize(query);

    if (terms.isEmpty()) {
      return Collections.emptyList();
    }

    Iterator<String> it = terms.iterator();
    String first = it.next();

    List<DictEntry> matched;

    // positional search
    if (terms.size() > 1) {
      matched = searchTerm(first, true);

      while (it.hasNext()) {
        List<DictEntry> entries = searchTerm(it.next(), true);
        matched = matchPositional(matched, entries, 1);

        if (matched.isEmpty()) {
          break;
        }
      }

      // simple term search with partial match
    } else {
      matched = searchTerm(first, false);
    }

    // convert results and calc score
    return prepareResult(matched);
  }

  private List<T> prepareResult(List<DictEntry> entries) {
    Map<T, Double> projectToScore = new HashMap<>();

    for (DictEntry e : entries) {
      Double score = projectToScore.get(e.obj);
      if (score == null) {
        score = 0.0;
      }

      score += 1.0 / objToWordFrequency.get(e.obj);
      projectToScore.put(e.obj, score);
    }

    List<ScorableObject> scoredProjects = new LinkedList<>();
    for (Entry<T, Double> e : projectToScore.entrySet()) {
      scoredProjects.add(new ScorableObject(e.getKey(), e.getValue()));
    }

    Collections.sort(scoredProjects, new Comparator<ScorableObject>() {
      @Override
      public int compare(ScorableObject o1, ScorableObject o2) {
        double score = o2.score - o1.score;
        if (score > 0) {
          return 1;
        } else if (score < 0) {
          return -1;
        }
        return 0;
      }
    });

    List<T> projects = new LinkedList<>();
    for (ScorableObject p : scoredProjects) {
      projects.add(p.obj);
    }

    return projects;
  }

  private List<DictEntry> searchTerm(String term, boolean fullMatchOnly) {
    List<DictEntry> entries = new LinkedList<>();

    if (fullMatchOnly) {
      entries = wordToObj.get(term);
      return entries != null ? entries : Collections.<DictEntry>emptyList();
    }

    SortedMap<String, List<DictEntry>> tailMap = wordToObj.tailMap(term);
    for (Entry<String, List<DictEntry>> e : tailMap.entrySet()) {
      if (!e.getKey().startsWith(term)) {
        break;
      }
      entries.addAll(e.getValue());
    }

    return entries;
  }

  public void clear() {
    indexedObjs = new HashSet<>();
    wordToObj = new TreeMap<>();
    objToWordFrequency = new HashMap<>();
  }

  /**
   * @return Can be empty, but never null
   */
  public Set<String> getTokens() {
    return wordToObj.keySet();
  }

  private void addToDictionary(String token, int tokenIndex, T obj) {
    List<DictEntry> projects = wordToObj.get(token);
    Integer count = objToWordFrequency.get(obj);

    if (projects == null) {
      projects = new LinkedList<>();
      wordToObj.put(token, projects);
    }

    if (count == null) {
      count = 0;
    }

    count++;
    projects.add(new DictEntry(obj, tokenIndex));
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

  private List<DictEntry> matchPositional(List<DictEntry> results1, List<DictEntry> results2, int maxDistance) {
    List<DictEntry> matches = new LinkedList<>();

    // TODO: optimize this n2 ugliness
    for (DictEntry e1 : results1) {
      for (DictEntry e2 : results2) {
        if (!e1.obj.equals(e2.obj)) {
          continue;
        }

        int dist = e2.tokenIndex - e1.tokenIndex;
        if (dist > 0 && dist <= maxDistance) {
          matches.add(e2);
        }
      }
    }
    return matches;
  }

  private class ScorableObject {
    double score;
    T obj;

    public ScorableObject(T obj, double score) {
      this.score = score;
      this.obj = obj;
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
