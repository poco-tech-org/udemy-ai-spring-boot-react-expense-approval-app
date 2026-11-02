package com.example.expense;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HistoryRepository extends JpaRepository<History, UUID> {
  List<History> findByExpenseIdOrderByOccurredAtAscIdAsc(UUID expenseId);
}
