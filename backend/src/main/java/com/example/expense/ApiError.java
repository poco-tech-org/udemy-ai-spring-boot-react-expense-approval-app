package com.example.expense;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiError {
  public static class Failure extends RuntimeException {
    final int status;

    public Failure(int status, String message) {
      super(message);
      this.status = status;
    }
  }

  @ExceptionHandler(Failure.class)
  ResponseEntity<ProblemDetail> business(Failure e) {
    return problem(e.status, e.getMessage());
  }

  @ExceptionHandler({
    OptimisticLockingFailureException.class,
    jakarta.persistence.OptimisticLockException.class
  })
  ResponseEntity<ProblemDetail> conflict(Exception e) {
    return problem(409, "他の操作で更新されました。再読み込みしてください。");
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HttpMessageNotReadableException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
    IllegalArgumentException.class
  })
  ResponseEntity<ProblemDetail> invalid(Exception e) {
    return problem(400, "入力内容が正しくありません。必須項目・金額・日付を確認してください。");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected(Exception e) {
    org.slf4j.LoggerFactory.getLogger(ApiError.class).error("Request failed", e);
    return problem(500, "処理に失敗しました。時間をおいて再度お試しください。");
  }

  static ResponseEntity<ProblemDetail> problem(int status, String message) {
    return ResponseEntity.status(status)
        .body(ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), message));
  }
}
