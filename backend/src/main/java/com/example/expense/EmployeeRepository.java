package com.example.expense;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeRepository extends JpaRepository<Employee, String> {
  Optional<Employee> findByIssuerAndSubjectAndActiveTrue(String issuer, String subject);
}
