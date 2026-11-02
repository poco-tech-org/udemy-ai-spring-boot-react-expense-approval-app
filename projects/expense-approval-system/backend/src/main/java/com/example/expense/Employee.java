package com.example.expense;

import jakarta.persistence.*;

@Entity
@Table(
    name = "employees",
    uniqueConstraints = @UniqueConstraint(columnNames = {"issuer", "subject"}))
public class Employee {
  @Id public String id;

  @Column(nullable = false)
  public String issuer;

  @Column(nullable = false)
  public String subject;

  @Column(nullable = false)
  public String name;

  public String managerId;
  public boolean manager;
  public boolean accounting;
  public boolean active;

  protected Employee() {}
}
