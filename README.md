# SCMS - Starter Project

Seherconi Management System (SCMS) — initial skeleton.

## What this contains
- Java 25 + JavaFX (dark theme) desktop app skeleton
- Maven build
- Local MySQL initialization on first run (creates `scms_db`, `users`, `roles`, `materials`, `assignments`)
- Default admin user created: `admin` / `admin` (hashed in DB)
- Placeholder CameraService (for later OpenCV integration)
- Authentication flow skeleton (login → dashboard)

## Hardcoded config (for initial dev)
DB host: `localhost:3306`  
DB name: `scms_db`  
DB user: `admin`  
DB pass: `admin`

> Update `DatabaseConfig` constants if your local MySQL uses different credentials.

## How to run (IntelliJ)
1. Make sure you have **Java 25** installed and configured in IntelliJ (File → Project Structure → SDKs).
2. Import project as a **Maven** project (Open `pom.xml`).
3. Ensure MySQL server is running on your machine and the credentials in `DatabaseConfig` are correct.
4. Build & run:
    - From terminal (IN ROOT FOLDER (SeherConiManagementSystem)): `mvn clean javafx:run`
    - Or use IntelliJ Run Config — run `com.scms.Main` (Maven plugin above also works).

On first run the app will create the DB and tables and insert a default admin user.

## Notes for teammates
- `src/main/java/com/scms/service/CameraService.java` is a placeholder. To add camera/OpenCV:
    1. Add OpenCV native library + Java binding dependency (we recommend using OpenCV + JavaCV or OpenCV Java).
    2. Implement recognition logic; record recognized material via `assignments` table.
- Passwords use BCrypt via jBCrypt.

## Suggested next tasks (split among team)
- [UI] Improve dashboard: materials list, search/filter, assignment form (1-2 devs)
- [DB] Add migrations (Flyway) and seeders (1 dev)
- [Auth] User management screens (admin) (1 dev)
- [Camera] OpenCV integration + tests (1 dev)
- [Reports] PDF export (iText/PDFBox) (1 dev)
- [Tests] Add unit/integration tests (1 dev)

## Git workflow (recommended)
- `main` — stable (only merge tested features)
- `dev` — integration branch
- `feature/<name>` — feature branches off `dev`
- Pull Request → Code review → Merge into `dev`, then `dev` → `main` when ready.

## Troubleshooting
- If the DB init fails with authentication errors, verify `root` credentials or change them in `DatabaseConfig`.
- If JavaFX classes fail to resolve, reimport Maven dependencies (`Maven` tool window → Reimport All Maven Projects).

## Creating scms_db manually (if needed)
If you need to create the database manually, run the following SQL commands in your MySQL client:
CREATE DATABASE IF NOT EXISTS scms_db CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE scms_db;

CREATE TABLE IF NOT EXISTS users (
id INT AUTO_INCREMENT PRIMARY KEY,
username VARCHAR(100) NOT NULL UNIQUE,
password_hash VARCHAR(128) NOT NULL,
role VARCHAR(50) NOT NULL,
created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS roles (
id INT AUTO_INCREMENT PRIMARY KEY,
name VARCHAR(50) NOT NULL UNIQUE,
description VARCHAR(255)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS materials (
id INT AUTO_INCREMENT PRIMARY KEY,
name VARCHAR(150) NOT NULL,
quantity DOUBLE DEFAULT 0,
unit VARCHAR(30),
supplier VARCHAR(100),
updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS assignments (
id INT AUTO_INCREMENT PRIMARY KEY,
user_id INT,
material_id INT,
quantity DOUBLE,
assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
FOREIGN KEY (user_id) REFERENCES users(id),
FOREIGN KEY (material_id) REFERENCES materials(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS recipes (
id INT AUTO_INCREMENT PRIMARY KEY,
name VARCHAR(150) NOT NULL,
description TEXT,
created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS recipe_items (
id INT AUTO_INCREMENT PRIMARY KEY,
recipe_id INT NOT NULL,
material_id INT NOT NULL,
quantity DOUBLE,
unit VARCHAR(30),
FOREIGN KEY (recipe_id) REFERENCES recipes(id) ON DELETE CASCADE,
FOREIGN KEY (material_id) REFERENCES materials(id)
) ENGINE=InnoDB;

-- default roles and admin user (admin password will be 'admin' hashed):
INSERT IGNORE INTO roles (name, description) VALUES ('admin','Administrator'),('worker','Worker'),('storekeeper','Storekeeper');

