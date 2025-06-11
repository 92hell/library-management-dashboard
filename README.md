# Library Management System

A full-stack web application for managing authors, books, members, and book borrowing records within a library system. This project demonstrates CRUD (Create, Read, Update, Delete) operations, search functionalities, and relational data handling using Spring Boot for the backend and React for the frontend.

## 🚀 Features

* **Author Management:**
    * Add, view, edit, and soft-delete authors.
    * Search authors by name.
* **Member Management:**
    * Add, view, edit, and soft-delete members.
    * Search members by name, email, or phone.
* **Book Management:**
    * Add, view, edit, and soft-delete books.
    * Associate books with authors.
    * Manage book categories.
    * Search books by title, publisher name, categories, or author name.
* **Borrowed Book Management:**
    * Record book borrowing events (borrow date, return date, associated book and member).
    * View all borrowed book records.
    * Search borrowed books by member name, book title, author name, or specific borrow date.
    * Edit and soft-delete borrowed book records.

## 💻 Technologies Used

### Backend (Spring Boot)

* **Spring Boot:** Framework for building robust, stand-alone, production-grade Spring applications.
* **Spring Data JPA:** Simplifies data access and persistence with Hibernate.
* **Hibernate:** JPA implementation for ORM.
* **Lombok:** Reduces boilerplate code (getters, setters, constructors).
* **Jackson:** High-performance JSON processor for Java.
* **MariaDB / MySQL:** Relational database for persistent storage.
* **Maven:** Build automation tool.

### Frontend (React)

* **React:** JavaScript library for building user interfaces.
* **React Router DOM:** For declarative routing in React applications.
* **Reactstrap:** Bootstrap 5 components for React.
* **JavaScript (ES6+)**
* **CSS**
* **npm / Yarn:** Package managers.

## ⚙️ Prerequisites

Before you begin, ensure you have the following installed on your system:

* **Java Development Kit (JDK) 17 or higher**
* **Maven 3.6.0 or higher**
* **Node.js 18.x or higher**
* **npm 8.x or higher** (or Yarn)
* **MariaDB or MySQL database server** installed and running.
* **An IDE** (e.g., IntelliJ IDEA, VS Code, Eclipse) is recommended.

## 🚀 Getting Started

Follow these steps to set up and run the project locally.

### 1. Clone the Repository

```bash
git clone https://github.com/92hell/library-dashboard.git
```

### 2. Database Configuration
This application is configured to use a **MariaDB/MySQL** database.
You will need to create a database and update the connection properties in `src/main/resources/application.properties`.

Example application.properties for MariaDB/MySQL:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/library_db?useSSL=false&serverTimezone=UTC
spring.datasource.username=your_db_username
spring.datasource.password=your_db_password
spring.jpa.hibernate.ddl-auto=update # Creates/updates schema on startup
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true

# If using MariaDB, you might use:
# spring.datasource.url=jdbc:mariadb://localhost:3306/library_db?useSSL=false&serverTimezone=UTC
# spring.datasource.driver-class-name=org.mariadb.jdbc.Driver
```
Important: Replace `library_db`, `your_db_username`, and `your_db_password` with your actual database credentials.

### 3. Run the Full-Stack Application
Navigate to the project's root directory (where the main pom.xml is located).
The Maven build process will automatically install frontend dependencies, build the React application, and then package it within the Spring Boot JAR.

```bash
# Build the entire project and run
mvn spring-boot:run
```

#### This single command will:

1. Download Node.js and npm (if not already present via Maven plugin).
2. Install React frontend dependencies.
3. Build the React frontend into static assets.
4. Compile the Spring Boot backend.
5. Start the Spring Boot application, serving both the backend API and the static frontend content.
The application will typically be accessible at `http://localhost:8080`.

## 🖥️ Usage

1. Once the application is running (after executing `mvn spring-boot:run`), open your web browser and navigate to `http://localhost:8080`.
2. You will see the navigation bar with links to Authors, Members, Books, and Borrowed Books.
3. Start by adding Authors and Members as these are prerequisites for adding Books and Borrowed Books.
4. Then, add some Books, ensuring you select an existing Author and assign a category.
5. Finally, go to the Borrowed Books section to record new borrowing events, selecting existing Books and Members.
6. Explore the CRUD operations and search functionalities on each page.

## 🤝 Contributing
Contributions are welcome! If you have suggestions for improvements or bug fixes, please open an issue or submit a pull request.