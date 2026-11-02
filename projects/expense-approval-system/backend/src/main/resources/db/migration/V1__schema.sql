CREATE TABLE employees (
 id varchar(255) PRIMARY KEY, issuer varchar(255) NOT NULL, subject varchar(255) NOT NULL,
 name varchar(255) NOT NULL, manager_id varchar(255) REFERENCES employees(id),
 manager boolean NOT NULL, accounting boolean NOT NULL, active boolean NOT NULL,
 UNIQUE(issuer,subject)
);
CREATE TABLE expenses (
 id uuid PRIMARY KEY, version bigint NOT NULL, owner_id varchar(255) NOT NULL REFERENCES employees(id),
 purpose varchar(200) NOT NULL, status varchar(40) NOT NULL CHECK(status IN ('DRAFT','SUBMITTED','ACCOUNTING_REVIEW','RETURNED','APPROVED')),
 total bigint NOT NULL CHECK(total>=0), created_at timestamp with time zone NOT NULL, submitted_at timestamp with time zone
);
CREATE TABLE expense_lines (
 expense_id uuid NOT NULL REFERENCES expenses(id), position integer NOT NULL,
 used_on date NOT NULL, merchant varchar(200) NOT NULL, category varchar(40) NOT NULL,
 description varchar(500) NOT NULL, amount bigint NOT NULL CHECK(amount>0 AND amount<=1000000000),
 PRIMARY KEY(expense_id,position)
);
CREATE TABLE histories (
 id uuid PRIMARY KEY, expense_id uuid NOT NULL REFERENCES expenses(id), actor_id varchar(255) NOT NULL REFERENCES employees(id),
 action varchar(255) NOT NULL, occurred_at timestamp with time zone NOT NULL,
 from_status varchar(255) NOT NULL, to_status varchar(255) NOT NULL, total bigint NOT NULL, comment varchar(1000)
);
CREATE INDEX expenses_owner_created ON expenses(owner_id,created_at DESC);
CREATE INDEX expenses_status_created ON expenses(status,created_at DESC);
CREATE INDEX histories_expense ON histories(expense_id,occurred_at);
CREATE INDEX employees_manager ON employees(manager_id);
