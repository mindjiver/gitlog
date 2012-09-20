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

public enum GitLogReturnCode {
  OK(0, "Success"),
  WRONG_PROJECT_NAME(1, "Can't find repository with given name."), 
  WRONG_RANGE(2, "Can't parse given range."),
  FIRST_REF_NOT_FOUND(3, "First commit from given range wasn't found in given repository."),
  SECOND_REF_NOT_FOUND(4, "Second commit from given range wasn't found in given repository."),
  AMBIGUOUS_COMMIT_REF(5, "Few commits correspond to provided refernce."),
  INTERNAL_ERROR(6, "Our bad, please file a issue at https://github.com/mindjiver/gitlog/issues");

  private final int code;
  private String description;

  private GitLogReturnCode(int code, String description) {
    this.code = code;
    this.description = description;
  }

  public String getDescription() {
    return description;
  }

  public int getCodeAsInt() {
    return code;
  }

  public String getCodeAsString() {
    return Integer.toString(code);
  }
}
