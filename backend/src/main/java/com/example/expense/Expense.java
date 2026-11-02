package com.example.expense;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "expenses")
public class Expense {
  public enum Status {
    DRAFT,
    SUBMITTED,
    ACCOUNTING_REVIEW,
    RETURNED,
    APPROVED
  }

  @Id public UUID id = UUID.randomUUID();
  @Version public long version;

  @Column(nullable = false)
  public String ownerId;

  @Column(nullable = false, length = 200)
  public String purpose = "";

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  public Status status = Status.DRAFT;

  public long total;
  public Instant createdAt = Instant.now();
  public Instant submittedAt;

  @ElementCollection
  @CollectionTable(name = "expense_lines", joinColumns = @JoinColumn(name = "expense_id"))
  @OrderColumn(name = "position")
  public List<Line> lines = new ArrayList<>();

  @Embeddable
  public static class Line {
    public LocalDate usedOn;

    @Column(length = 200)
    public String merchant;

    @Column(length = 40)
    public String category;

    @Column(length = 500)
    public String description;

    public long amount;

    public Line() {}

    public Line(LocalDate day, String merchant, String category, String description, long amount) {
      this.usedOn = day;
      this.merchant = merchant;
      this.category = category;
      this.description = description;
      this.amount = amount;
    }
  }
}
