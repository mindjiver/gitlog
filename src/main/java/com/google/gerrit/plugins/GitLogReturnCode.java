package com.google.gerrit.plugins;

public enum GitLogReturnCode {
  OK(0, "Sucsses"), 
  WRONG_PROJECT_NAME(1, "Can't find repository with given name."), 
  WRONG_RANGE(2, "Can't parse given range."),
  FIRST_REF_NOT_FOUND(3, "First commit from given range wasn't found in given repository."),
  SECOND_REF_NOT_FOUND(4, "Second commit from given range wasn't found in given repository."),
  AMBIGUOUS_COMMIT_REF(5, "Few commits correspond to provided refernce."),
  INTERNAL_ERROR(6, "Our bad.");

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
