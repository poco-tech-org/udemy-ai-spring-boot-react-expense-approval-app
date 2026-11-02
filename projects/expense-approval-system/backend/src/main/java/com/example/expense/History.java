package com.example.expense;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "histories")
public class History {
  @Id public UUID id = UUID.randomUUID();
  public UUID expenseId;
  public String actorId;
  public String action;
  public Instant occurredAt = Instant.now();
  public String fromStatus;
  public String toStatus;
  public long total;

  @Column(length = 1000)
  public String comment;

  protected History() {}

  public History(Expense e, Employee actor, String action, Expense.Status from, String comment) {
    expenseId = e.id;
    actorId = actor.id;
    this.action = action;
    fromStatus = from.name();
    toStatus = e.status.name();
    total = e.total;
    this.comment = comment;
  }
}
