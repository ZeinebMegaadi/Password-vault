#  Secure Vault, a Encrypted Password Manager

## Overview

Secure Vault is a full-stack Java application that allows users to safely store and manage sensitive data such as passwords, API keys, notes, and more.
It follows modern security practices by encrypting all stored data and protecting user credentials with strong hashing algorithms.

---

##  Features

### Security

* AES-256-GCM encryption for all stored secrets
* PBKDF2-HMAC-SHA256 password hashing (310,000 iterations)
* Automatic re-encryption of all data when password changes
* 30-minute session timeout (in-memory only)
* Password strength checker with live feedback

###  Core Functionality

* User registration and login
* Add, view, update, and delete secrets
* Categorized storage (Password, API Key, Note, SSH Key, Credit Card)
* Search and filter system
* Audit log for tracking all actions

###  Extras

* Secure password generator
* CSV export for audit logs
* Dark-themed modern UI (Java Swing)
* Responsive and non-blocking UI (SwingWorker)

---

##  Project Structure

### Security Layer

* `CryptoUtils.java` — Encryption (AES-256-GCM), key derivation
* `PasswordHasher.java` — Secure password hashing
* `PasswordStrength.java` — Password strength evaluation
* `SessionManager.java` — Session handling and expiration

### Server & Database

* `Server.java` — Handles incoming connections
* `ClientHandler.java` — Processes commands (REGISTER, LOGIN, etc.)
* `DatabaseUtility.java` — Database connection and schema initialization

### Client & GUI

* `VaultClient.java` — Client-server communication
* `LoginFrame.java` — Login and registration UI
* `MainFrame.java` — Main dashboard
* `AddSecretDialog.java` — Add/edit secrets
* `PasswordGeneratorDialog.java` — Generate secure passwords
* `AuditLogDialog.java` — View activity logs
* `Theme.java` — UI styling

---

##  Getting Started

###  Requirements

* Java JDK 17 or higher
* MySQL Server
* MySQL Workbench (optional)

---

### 🗄️ 1. Database Setup

Run the provided SQL schema:

```sql
SOURCE path/to/vault_schema.sql;
```

Update database credentials in:

```java
DatabaseUtility.java
```

---

### 🔨 2. Compile the Project

```bash
javac -d out src/**/*.java
```

(Windows alternative)

```bash
javac -d out src\**\*.java
```

---

### 🖥️ 3. Run the Server

```bash
java -cp out Server
```

---

### 4. Run the Client

Open a new terminal:

```bash
java -cp out LoginFrame
```

---

##  Usage

1. Register a new account
2. Log in
3. Add and manage secrets
4. Use the password generator for strong credentials
5. View audit logs for activity tracking

---

##  Security Notes

* All sensitive data is encrypted before being stored
* Passwords are never stored in plain text
* Session keys are kept in memory only
* Every action is logged for auditing

---

##  Common Issues

**Database connection failed**

* Ensure MySQL is running
* Verify credentials in `DatabaseUtility.java`

**Class not found error**

* Check compilation path and classpath (`-cp out`)

**Client not working**

* Make sure the server is running first

---

##  Future Improvements

* Cloud deployment support
* Multi-device synchronization
* Two-factor authentication (2FA)
* Improved UI/UX

---

##  Author

Zeineb Megaadi, Software engineering student at Medtech SMU. :))

---
