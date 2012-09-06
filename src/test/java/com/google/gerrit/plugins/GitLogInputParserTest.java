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

import org.junit.*;
import org.javatuples.Pair;

public class GitLogInputParserTest {

  @Test
  public void RangeOfValuesTest() {
    Assert.assertEquals(Pair.with("from", "to"),
        GitLogInputParser.parse("from..to"));
  }

  @Test
  public void SingleValueTest() {
    Assert.assertEquals(Pair.with(null,"to"),
        GitLogInputParser.parse("to"));
  }

  @Test
  public void SpacesTest() {
    Assert.assertEquals(Pair.with(null, null),
        GitLogInputParser.parse("from to"));
  }

  @Test
  public void NotAllowedControlSymbolsTest() {
    Assert.assertEquals(Pair.with(null, null),
        GitLogInputParser.parse("from..to\n"));
  }

  @Test
  public void TildeControlSymbolsTest1() {
    Assert.assertEquals(Pair.with("from", "to~6"),
        GitLogInputParser.parse("from..to~6"));
  }

  @Test
  public void TildeControlSymbolsTest2() {
    Assert.assertEquals(Pair.with(null, "to~6"),
        GitLogInputParser.parse("to~6"));
  }

  @Test
  public void HatControlSymbolsTest1() {
    Assert.assertEquals(Pair.with("from", "to^"),
        GitLogInputParser.parse("from..to^"));
  }

  @Test
  public void HatControlSymbolsTest2() {
    Assert.assertEquals(Pair.with(null, "to^"),
        GitLogInputParser.parse("to^"));
  }

  @Test
  public void MultipleSeparatorTest() {
    Assert.assertEquals(Pair.with(null, null),
        GitLogInputParser.parse("from..to..hell"));
  }

  @Test
  public void SeparatorAtTheBeginningTest() {
    Assert.assertEquals(Pair.with(null, null),
        GitLogInputParser.parse("..from..to"));
  }

  @Test
  public void SeparatorAtTheBeginning2Test() {
    Assert.assertEquals(Pair.with(null, null),
        GitLogInputParser.parse("..fromto"));
  }

  @Test
  public void SeparatorAtTheEndTest() {
    Assert.assertEquals(Pair.with(null, null),
        GitLogInputParser.parse("from..to.."));
  }

  @Test
  public void SeparatorAtTheEnd2Test() {
    Assert.assertEquals(Pair.with(null, null),
        GitLogInputParser.parse("fromto.."));
  }
}
