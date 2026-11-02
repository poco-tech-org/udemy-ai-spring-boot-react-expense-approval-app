package com.example.expense;

import java.util.UUID;
import org.springframework.data.jpa.repository.*;

public interface ExpenseRepository
    extends JpaRepository<Expense, UUID>, JpaSpecificationExecutor<Expense> {}
