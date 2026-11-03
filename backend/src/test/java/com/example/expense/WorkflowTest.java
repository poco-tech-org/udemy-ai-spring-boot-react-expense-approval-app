package com.example.expense;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
    properties = {
      "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:workflow;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}",
      "spring.datasource.username=${TEST_DB_USER:sa}",
      "spring.datasource.password=${TEST_DB_PASSWORD:}"
    })
@AutoConfigureMockMvc
@Import(WorkflowTest.OidcTestConfiguration.class)
class WorkflowTest {
  @TestConfiguration(proxyBeanMethods = false)
  static class OidcTestConfiguration {
    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
      // Build metadata locally: issuer-uri auto-configuration performs discovery even
      // when endpoint URLs are specified. HTTP authentication is simulated with oidcLogin().
      return new InMemoryClientRegistrationRepository(
          ClientRegistration.withRegistrationId("keycloak")
              .clientId("expense-test")
              .clientSecret("test-only")
              .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
              .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
              .scope("openid", "profile")
              .issuerUri("http://localhost:8180/realms/expense-training")
              .authorizationUri("http://127.0.0.1:1/auth")
              .tokenUri("http://127.0.0.1:1/token")
              .jwkSetUri("http://127.0.0.1:1/certs")
              .userInfoUri("http://127.0.0.1:1/userinfo")
              .userNameAttributeName("sub")
              .build());
    }
  }

  @Autowired ExpenseService service;
  @Autowired EmployeeRepository employees;
  @Autowired ExpenseRepository expenses;
  @Autowired HistoryRepository histories;
  @Autowired MockMvc mvc;
  @Autowired PlatformTransactionManager transactionManager;
  Employee employee, manager, other, accounting;

  @BeforeEach
  void init() {
    histories.deleteAll();
    expenses.deleteAll();
    // Upsert deterministic users; no external identity provider is contacted.
    manager = user("manager", true, false, null);
    other = user("other", true, false, null);
    accounting = user("accounting", false, true, null);
    employee = user("employee", false, false, "manager");
  }

  Employee user(String id, boolean manager, boolean accounting, String managerId) {
    Employee e = new Employee();
    e.id = id;
    e.issuer = "http://localhost:8180/realms/expense-training";
    e.subject = id;
    e.name = id;
    e.manager = manager;
    e.accounting = accounting;
    e.active = true;
    e.managerId = managerId;
    return employees.saveAndFlush(e);
  }

  ExpenseService.Edit input(Long version, long... amounts) {
    return new ExpenseService.Edit(
        version,
        "業務出張",
        Arrays.stream(amounts)
            .mapToObj(a -> new ExpenseService.LineInput(LocalDate.now(), "鉄道会社", "交通費", "営業訪問", a))
            .toList());
  }

  ExpenseService.Detail draft(long... amounts) {
    return service.create(employee, input(null, amounts));
  }

  ExpenseService.Detail submit(long... amounts) {
    var d = draft(amounts);
    return service.act(
        employee,
        d.expense().id(),
        "submit",
        new ExpenseService.Command(d.expense().version(), null));
  }

  ExpenseService.Detail act(
      Employee actor, ExpenseService.Detail d, String action, String comment) {
    return service.act(
        actor,
        d.expense().id(),
        action,
        new ExpenseService.Command(d.expense().version(), comment));
  }

  void failure(int status, Runnable task) {
    assertEquals(status, assertThrows(ApiError.Failure.class, task::run).status);
  }

  @Test
  void boundaryAndServerTotals() {
    for (long amount : new long[] {49999, 50000, 50001}) {
      var d = submit(amount - 1, 1);
      assertEquals(amount, d.expense().total());
      var approved = act(manager, d, "approve", null);
      assertEquals(
          amount > 50000 ? Expense.Status.ACCOUNTING_REVIEW : Expense.Status.APPROVED,
          approved.expense().status());
      if (amount > 50000)
        assertEquals(
            Expense.Status.APPROVED, act(accounting, approved, "approve", null).expense().status());
    }
  }

  @Test
  void returnResubmitRestartsManagerApprovalAndKeepsHistory() {
    var d = act(manager, submit(50001), "approve", null);
    d = act(accounting, d, "return", "利用目的を修正してください");
    d = service.update(employee, d.expense().id(), input(d.expense().version(), 60000));
    d = act(employee, d, "submit", null);
    assertEquals(Expense.Status.SUBMITTED, d.expense().status());
    assertTrue(d.history().stream().anyMatch(h -> "RETURN".equals(h.action())));
    var latest = d;
    failure(403, () -> act(accounting, latest, "approve", null));
    assertEquals(
        Expense.Status.ACCOUNTING_REVIEW, act(manager, d, "approve", null).expense().status());
  }

  @Test
  void unauthorizedAccessAndSelfApproval() {
    var d = submit(100);
    failure(404, () -> service.detail(other, d.expense().id()));
    failure(404, () -> act(other, d, "approve", null));
    failure(403, () -> act(employee, d, "approve", null));
    failure(403, () -> service.update(manager, d.expense().id(), input(d.expense().version(), 1)));
    failure(409, () -> service.update(employee, d.expense().id(), input(d.expense().version(), 1)));
    employee.accounting = true;
    employees.saveAndFlush(employee);
    var high = act(manager, submit(50001), "approve", null);
    failure(403, () -> act(employee, high, "approve", null));
  }

  @Test
  void incompleteInputAndMissingManager() {
    failure(400, () -> draft(0));
    failure(400, () -> draft(-1));
    failure(400, () -> draft(1000000001));
    var d = draft();
    failure(400, () -> act(employee, d, "submit", null));
    employee.managerId = null;
    employees.saveAndFlush(employee);
    var valid = draft(10);
    failure(400, () -> act(employee, valid, "submit", null));
  }

  @Test
  void invalidTransitionsAndReasons() {
    var draft = draft(1);
    failure(403, () -> act(manager, draft, "approve", null));
    var d = submit(1);
    failure(400, () -> act(manager, d, "return", " "));
    var approved = act(manager, d, "approve", null);
    failure(409, () -> act(manager, approved, "approve", null));
    failure(409, () -> act(employee, approved, "submit", null));
  }

  @Test
  void paginationAndVisibility() {
    draft(1);
    draft(2);
    submit(50001);
    assertEquals(
        3, service.list(employee, "mine", null, null, null, null, 0, 2).getTotalElements());
    assertEquals(
        2, service.list(employee, "mine", null, null, null, null, 0, 2).getContent().size());
    assertEquals(0, service.list(other, "all", null, null, null, null, 0, 20).getTotalElements());
    assertEquals(
        1, service.list(manager, "approvals", null, null, null, null, 0, 20).getTotalElements());
  }

  @Test
  void staleVersionCannotCreateDuplicateHistory() {
    var d = submit(100);
    act(manager, d, "approve", null);
    int count = histories.findByExpenseIdOrderByOccurredAtAscIdAsc(d.expense().id()).size();
    failure(409, () -> act(manager, d, "approve", null));
    assertEquals(
        count, histories.findByExpenseIdOrderByOccurredAtAscIdAsc(d.expense().id()).size());
  }

  @Test
  void concurrentApprovalsHaveOneWinner() throws Exception {
    var d = submit(50001);
    var barrier = new CyclicBarrier(2);
    var pool = Executors.newFixedThreadPool(2);
    try {
      Callable<Boolean> task =
          () -> {
            try {
              new TransactionTemplate(transactionManager)
                  .execute(
                      s -> {
                        expenses.findById(d.expense().id()).orElseThrow();
                        try {
                          barrier.await(10, TimeUnit.SECONDS);
                        } catch (Exception e) {
                          throw new RuntimeException(e);
                        }
                        act(manager, d, "approve", null);
                        return null;
                      });
              return true;
            } catch (org.springframework.dao.OptimisticLockingFailureException
                | ApiError.Failure e) {
              return false;
            }
          };
      var a = pool.submit(task);
      var b = pool.submit(task);
      assertNotEquals(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
      assertEquals(
          1,
          histories.findByExpenseIdOrderByOccurredAtAscIdAsc(d.expense().id()).stream()
              .filter(h -> h.action.equals("APPROVE"))
              .count());
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void transactionFailureRollsBackStateAndHistory() {
    var d = submit(100);
    long count = histories.count();
    assertThrows(
        IllegalStateException.class,
        () ->
            new TransactionTemplate(transactionManager)
                .execute(
                    s -> {
                      act(manager, d, "approve", null);
                      throw new IllegalStateException("simulated failure");
                    }));
    assertEquals(
        Expense.Status.SUBMITTED, service.detail(employee, d.expense().id()).expense().status());
    assertEquals(count, histories.count());
  }

  OidcLoginRequestPostProcessor login(String subject) {
    return oidcLogin()
        .idToken(t -> t.subject(subject).issuer("http://localhost:8180/realms/expense-training"));
  }

  @Test
  void httpAuthenticationCsrfAndUnregisteredUsers() throws Exception {
    mvc.perform(get("/api/expenses")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/me").with(login("unknown"))).andExpect(status().isForbidden());
    mvc.perform(
            post("/api/expenses")
                .with(login("employee"))
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/expenses")
                .with(login("employee"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"purpose\":\"出張\",\"lines\":[]}"))
        .andExpect(status().isCreated());
  }

  @Test
  void directApiCannotReadOrModifyForeignApplication() throws Exception {
    var d = submit(100);
    String path = "/api/expenses/" + d.expense().id();
    mvc.perform(get(path).with(login("other"))).andExpect(status().isNotFound());
    mvc.perform(
            put(path)
                .with(login("other"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"version\":1,\"purpose\":\"改変\",\"lines\":[]}"))
        .andExpect(status().isNotFound());
    assertEquals(100, service.detail(employee, d.expense().id()).expense().total());
  }

  @Test
  void fractionalYenIsRejectedWithoutCreatingApplication() throws Exception {
    mvc.perform(
            post("/api/expenses")
                .with(login("employee"))
                .with(csrf())
                .contentType("application/json")
                .content(
                    """
                    {"purpose":"出張","lines":[{"usedOn":"2026-11-01","merchant":"鉄道","category":"交通費","description":"訪問","amount":1.5}]}
                    """))
        .andExpect(status().isBadRequest());
    assertEquals(0, expenses.count());
  }

  @Test
  void collectionOnlyEditsAdvanceVersion() {
    var d = draft(10, 20);
    var updated = service.update(employee, d.expense().id(), input(d.expense().version(), 20, 10));
    assertTrue(updated.expense().version() > d.expense().version());
    failure(
        409, () -> service.update(employee, d.expense().id(), input(d.expense().version(), 30)));
  }
}
