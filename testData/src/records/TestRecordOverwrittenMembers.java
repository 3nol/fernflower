package records;

public record TestRecordOverwrittenMembers(int x, int y) {
  public TestRecordOverwrittenMembers(int x) {
    this(x, 42);
  }

  public int x() {
    return 100;
  }
}
