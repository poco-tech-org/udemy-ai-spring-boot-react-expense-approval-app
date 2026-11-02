package com.example.expense;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ExpenseController {
  final ExpenseService service;
  final EmployeeRepository employees;

  public ExpenseController(ExpenseService service, EmployeeRepository employees) {
    this.service = service;
    this.employees = employees;
  }

  Employee user(OidcUser principal) {
    if (principal == null) throw new ApiError.Failure(401, "ログインが必要です。");
    return employees
        .findByIssuerAndSubjectAndActiveTrue(
            principal.getIssuer().toString(), principal.getSubject())
        .orElseThrow(() -> new ApiError.Failure(403, "利用者が登録されていません。管理者にお問い合わせください。"));
  }

  @GetMapping("/csrf")
  Map<String, String> csrf(CsrfToken token) {
    return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
  }

  public record UserView(
      String id, String name, String managerId, boolean manager, boolean accounting) {}

  @GetMapping("/me")
  UserView me(@AuthenticationPrincipal OidcUser p) {
    var u = user(p);
    return new UserView(u.id, u.name, u.managerId, u.manager, u.accounting);
  }

  @GetMapping("/expenses")
  Page<ExpenseService.Summary> list(
      @AuthenticationPrincipal OidcUser p,
      @RequestParam(defaultValue = "mine") String scope,
      @RequestParam(required = false) Expense.Status status,
      @RequestParam(required = false) String applicant,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.list(user(p), scope, status, applicant, from, to, page, size);
  }

  @GetMapping("/expenses/{id}")
  ExpenseService.Detail detail(@AuthenticationPrincipal OidcUser p, @PathVariable UUID id) {
    return service.detail(user(p), id);
  }

  @PostMapping("/expenses")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  ExpenseService.Detail create(
      @AuthenticationPrincipal OidcUser p, @Valid @RequestBody ExpenseService.Edit edit) {
    return service.create(user(p), edit);
  }

  @PutMapping("/expenses/{id}")
  ExpenseService.Detail update(
      @AuthenticationPrincipal OidcUser p,
      @PathVariable UUID id,
      @Valid @RequestBody ExpenseService.Edit edit) {
    return service.update(user(p), id, edit);
  }

  @PostMapping("/expenses/{id}/{action}")
  ExpenseService.Detail act(
      @AuthenticationPrincipal OidcUser p,
      @PathVariable UUID id,
      @PathVariable String action,
      @RequestBody ExpenseService.Command command) {
    return service.act(user(p), id, action, command);
  }
}
