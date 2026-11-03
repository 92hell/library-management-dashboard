-- Initial schema. Matches the tables the first version of the app created through Hibernate,
-- so an existing database can be adopted with spring.flyway.baseline-on-migrate.

CREATE TABLE authors (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,
    date_of_birth DATE,
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP    NULL
);

CREATE TABLE books (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title          VARCHAR(255) NOT NULL,
    published_date DATE         NOT NULL,
    publisher_name VARCHAR(255),
    author_id      BIGINT       NOT NULL,
    categories     VARCHAR(1000),
    is_deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP    NULL,
    CONSTRAINT fk_books_author FOREIGN KEY (author_id) REFERENCES authors (id)
);

CREATE TABLE members (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,
    date_of_birth DATE         NOT NULL,
    email         VARCHAR(255) NOT NULL,
    phone         VARCHAR(15)  NOT NULL,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP    NULL
);

CREATE TABLE borrowed_books (
    id          BIGINT    NOT NULL AUTO_INCREMENT PRIMARY KEY,
    book_id     BIGINT    NOT NULL,
    member_id   BIGINT    NOT NULL,
    borrow_date DATE      NOT NULL,
    return_date DATE,
    is_deleted  BOOLEAN   NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP NULL,
    CONSTRAINT fk_borrowed_books_book FOREIGN KEY (book_id) REFERENCES books (id),
    CONSTRAINT fk_borrowed_books_member FOREIGN KEY (member_id) REFERENCES members (id)
);
