// Copyright (C) 2012 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.plugins;

import java.util.regex.Pattern;

import org.javatuples.Pair;

public final class GitLogInputParser {
  public static Pair<String, String> parse(String argv) {
    // Regex to match correct range, i.e. from..to
    String isCorrectRange = "^(.+?)\\.{2}(.+?)$";
    // Regex to match incorrect range with separator in
    // the beginning of the string, i.e ..fromto
    String isSeparatorFirst = "^\\.{2}(.+?)$";
    // Regex to match incorrect range with separator in
    // the end of the string, i.e fromto..
    String isSeparatorLast = "^(.+?)\\.{2}$";
    // Regex to match spaces in the string
    String isEmptyCharacters = "(.*)(\\p{Space})(.*)";
    // Regex to match all control symbols except ~ and ^
    String isControlCharacters = "(.*)[(\\p{Cntrl})&&[^\\~\\^]](.*)";

    // Return empty tuple if input contains spaces or control symbols
    if (Pattern.matches(isEmptyCharacters, argv)
        || Pattern.matches(isControlCharacters, argv)) {
      return Pair.with(null, null);
    }

    // Return empty tuple if first or last symbols of the
    // input string is separator
    if (Pattern.matches(isSeparatorFirst, argv)
        || Pattern.matches(isSeparatorLast, argv)) {
      return Pair.with(null, null);
    }

    // If input line is correctly formed range of revisions
    if (Pattern.matches(isCorrectRange, argv)) {
      // then split line into to revisions using separator
      String[] values = argv.split("\\.{2}");
      /* In case of one separator we have to get to strings as we expect
       * but in case of three or more separators we will have larger array
       * So we need to check resulting array length to sort out incorrect
       * input strings.
       */
      if (values.length == 2) {
        return Pair.with(values[0], values[1]);
      } else {
        return Pair.with(null, null);
      }
    }

    /* if we end up herewe assume that input line is only one
     * revision and return only "to" values, "from" value is empty in this case
     */
    return Pair.with(argv, null);
  }
}
