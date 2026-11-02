package com.example.expense;

import static com.example.expense.ApiError.*;
import static com.example.expense.Expense.Status.*;

import jakarta.persistence.criteria.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ExpenseService {
  final ExpenseRepository expenses;
  final EmployeeRepository employees;
  final HistoryRepository histories;

  public ExpenseService(
      ExpenseRepository expenses, EmployeeRepository employees, HistoryRepository histories) {
    this.expenses = expenses;
    this.employees = employees;
    this.histories = histories;
  }

  public record LineInput(
      @NotNull LocalDate usedOn,
      @NotBlank @Size(max = 200) String merchant,
      @NotNull String category,
      @NotBlank @Size(max = 500) String description,
      @NotNull @Min(1) @Max(1000000000) Long amount) {}

  public record Edit(
      Long version,
      @NotNull @Size(max = 200) String purpose,
      @NotNull @Size(max = 100) List<@NotNull @Valid LineInput> lines) {}

  public record Command(Long version, String comment) {}

  public record Summary(
      UUID id,
      long version,
      String purpose,
      String ownerId,
      String ownerName,
      Expense.Status status,
      long total,
      Instant createdAt,
      Instant submittedAt) {}

  public record HistoryView(
      UUID id,
      String actorId,
      String actorName,
      String action,
      Instant occurredAt,
      String fromStatus,
      String toStatus,
      long total,
      String comment) {}

  public record Detail(
      Summary expense,
      List<LineInput> lines,
      List<HistoryView> history,
      boolean editable,
      boolean submittable,
      boolean approvable) {}

  Summary summary(Expense e) {
    return new Summary(
        e.id,
        e.version,
        e.purpose,
        e.ownerId,
        employees.findById(e.ownerId).orElseThrow().name,
        e.status,
        e.total,
        e.createdAt,
        e.submittedAt);
  }

  boolean related(Employee u, Expense e) {
    return u.manager
        && employees.findById(e.ownerId).map(o -> u.id.equals(o.managerId)).orElse(false);
  }

  boolean visible(Employee u, Expense e) {
    return e.ownerId.equals(u.id)
        || related(u, e)
        || (u.accounting && e.total > 50000 && e.submittedAt != null);
  }

  boolean approvable(Employee u, Expense e) {
    return !e.ownerId.equals(u.id)
        && ((e.status == SUBMITTED && related(u, e))
            || (e.status == ACCOUNTING_REVIEW && u.accounting));
  }

  Expense get(Employee u, UUID id) {
    Expense e = expenses.findById(id).orElseThrow(() -> new Failure(404, "申請が見つかりません。"));
    if (!visible(u, e)) throw new Failure(404, "申請が見つかりません。");
    return e;
  }

  void version(Expense e, Long version) {
    if (version == null || version != e.version) throw new Failure(409, "申請が更新されています。再読み込みしてください。");
  }

  void owner(Employee u, Expense e) {
    if (!e.ownerId.equals(u.id)) throw new Failure(403, "自分の申請のみ変更できます。");
  }

  boolean editable(Expense e) {
    return e.status == DRAFT || e.status == RETURNED;
  }

  public Detail detail(Employee u, UUID id) {
    Expense e = get(u, id);
    boolean own = e.ownerId.equals(u.id);
    var history = histories.findByExpenseIdOrderByOccurredAtAscIdAsc(id);
    Map<String, String> names = new HashMap<>();
    employees
        .findAllById(history.stream().map(h -> h.actorId).distinct().toList())
        .forEach(a -> names.put(a.id, a.name));
    return new Detail(
        summary(e),
        e.lines.stream()
            .map(l -> new LineInput(l.usedOn, l.merchant, l.category, l.description, l.amount))
            .toList(),
        history.stream()
            .map(
                h ->
                    new HistoryView(
                        h.id,
                        h.actorId,
                        names.get(h.actorId),
                        h.action,
                        h.occurredAt,
                        h.fromStatus,
                        h.toStatus,
                        h.total,
                        h.comment))
            .toList(),
        own && editable(e),
        own && editable(e),
        approvable(u, e));
  }

  public Page<Summary> list(
      Employee u,
      String scope,
      Expense.Status status,
      String applicant,
      LocalDate from,
      LocalDate to,
      int page,
      int size) {
    if (page < 0
        || size < 1
        || size > 100
        || !Set.of("mine", "approvals", "all").contains(scope)
        || (from != null && to != null && from.isAfter(to)))
      throw new Failure(400, "検索条件が正しくありません。");
    Specification<Expense> spec =
        (r, q, b) -> {
          Subquery<String> sub = q.subquery(String.class);
          Root<Employee> emp = sub.from(Employee.class);
          sub.select(emp.get("id")).where(b.equal(emp.get("managerId"), u.id));
          Predicate mine = b.equal(r.get("ownerId"), u.id);
          Predicate team = u.manager ? r.get("ownerId").in(sub) : b.disjunction();
          Predicate acct =
              u.accounting
                  ? b.and(b.greaterThan(r.get("total"), 50000L), b.isNotNull(r.get("submittedAt")))
                  : b.disjunction();
          Predicate access = b.or(mine, team, acct);
          if (scope.equals("mine")) access = mine;
          if (scope.equals("approvals"))
            access =
                b.and(
                    b.notEqual(r.get("ownerId"), u.id),
                    b.or(
                        b.and(team, b.equal(r.get("status"), SUBMITTED)),
                        u.accounting
                            ? b.equal(r.get("status"), ACCOUNTING_REVIEW)
                            : b.disjunction()));
          List<Predicate> filters = new ArrayList<>();
          filters.add(access);
          if (status != null) filters.add(b.equal(r.get("status"), status));
          if (applicant != null && !applicant.isBlank())
            filters.add(b.equal(r.get("ownerId"), applicant));
          if (from != null)
            filters.add(
                b.greaterThanOrEqualTo(
                    r.get("createdAt"), from.atStartOfDay(ZoneId.of("Asia/Tokyo")).toInstant()));
          if (to != null)
            filters.add(
                b.lessThan(
                    r.get("createdAt"),
                    to.plusDays(1).atStartOfDay(ZoneId.of("Asia/Tokyo")).toInstant()));
          return b.and(filters.toArray(Predicate[]::new));
        };
    // Resolve names once per distinct owner, never load expense lines in list responses.
    Page<Expense> result =
        expenses.findAll(
            spec,
            PageRequest.of(
                page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
    Map<String, String> names = new HashMap<>();
    employees
        .findAllById(result.stream().map(e -> e.ownerId).distinct().toList())
        .forEach(e -> names.put(e.id, e.name));
    return result.map(
        e ->
            new Summary(
                e.id,
                e.version,
                e.purpose,
                e.ownerId,
                names.get(e.ownerId),
                e.status,
                e.total,
                e.createdAt,
                e.submittedAt));
  }

  void apply(Expense e, Edit input) {
    if (input.purpose() == null
        || input.purpose().length() > 200
        || input.lines() == null
        || input.lines().size() > 100) throw new Failure(400, "目的は200文字以内、明細は100件以内で入力してください。");
    e.purpose = input.purpose().trim();
    e.lines.clear();
    e.total = 0;
    for (LineInput l : input.lines()) {
      if (l == null
          || l.usedOn() == null
          || l.merchant() == null
          || l.merchant().isBlank()
          || l.merchant().length() > 200
          || !Set.of("交通費", "宿泊費", "会議費", "消耗品費", "その他")
              .contains(l.category() == null ? "" : l.category())
          || l.description() == null
          || l.description().isBlank()
          || l.description().length() > 500
          || l.amount() == null
          || l.amount() < 1
          || l.amount() > 1000000000L)
        throw new Failure(400, "明細の利用日・利用先・区分・目的・正の整数金額を確認してください（1明細10億円まで）。");
      e.total = Math.addExact(e.total, l.amount());
      e.lines.add(
          new Expense.Line(
              l.usedOn(), l.merchant().trim(), l.category(), l.description().trim(), l.amount()));
    }
  }

  public Detail create(Employee u, Edit input) {
    Expense e = new Expense();
    e.ownerId = u.id;
    apply(e, input);
    expenses.saveAndFlush(e);
    histories.save(new History(e, u, "CREATE", DRAFT, null));
    return detail(u, e.id);
  }

  public Detail update(Employee u, UUID id, Edit input) {
    Expense e = get(u, id);
    owner(u, e);
    version(e, input.version());
    if (!editable(e)) throw new Failure(409, "提出済みの申請は編集できません。");
    apply(e, input); // Hibernate versions the parent when the owned collection changes
    expenses.flush();
    histories.save(new History(e, u, "EDIT", e.status, null));
    return detail(u, id);
  }

  public Detail act(Employee u, UUID id, String action, Command command) {
    Expense e = get(u, id);
    version(e, command.version());
    Expense.Status before = e.status;
    switch (action) {
      case "submit" -> {
        owner(u, e);
        if (!editable(e)) throw new Failure(409, "この状態では提出できません。");
        if (e.purpose.isBlank() || e.lines.isEmpty())
          throw new Failure(400, "提出には目的と1件以上の明細が必要です。");
        Employee m =
            employees
                .findById(u.managerId == null ? "" : u.managerId)
                .orElseThrow(() -> new Failure(400, "有効な上長が割り当てられていません。"));
        if (!m.active || !m.manager || m.id.equals(u.id))
          throw new Failure(400, "有効な上長が割り当てられていません。");
        e.status = SUBMITTED;
        e.submittedAt = Instant.now();
      }
      case "approve", "return" -> {
        if (!approvable(u, e))
          throw new Failure(
              e.status == APPROVED || e.status == RETURNED ? 409 : 403, "この申請を承認・差戻しできません。");
        if (action.equals("return")) {
          if (command.comment() == null
              || command.comment().isBlank()
              || command.comment().length() > 1000)
            throw new Failure(400, "差戻し理由を1〜1000文字で入力してください。");
          e.status = RETURNED;
        } else e.status = e.status == SUBMITTED && e.total > 50000 ? ACCOUNTING_REVIEW : APPROVED;
      }
      default -> throw new Failure(404, "操作が見つかりません。");
    }
    expenses.flush();
    histories.saveAndFlush(
        new History(
            e,
            u,
            action.toUpperCase(Locale.ROOT),
            before,
            action.equals("return") ? command.comment().trim() : null));
    return detail(u, id);
  }
}
