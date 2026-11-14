# 5-Minute Project Demonstration Script

**Project:** Fuel Station Fleet Card Billing Engine  
**Problem Statement:** PS-051 – Fuel Station Fleet Card Billing Engine  
**Team ID:** T052  
**Demonstration duration:** Approximately 5 minutes

---

## Before Recording

Prepare the following before starting the recording:

1. Open the project root folder in VS Code.
2. Keep the `pom.xml`, `src` and `data/sample` folders visible in the Explorer.
3. Open a terminal in the project root.
4. Ensure the sample CSV files and `data/sample/commands.txt` are present.
5. Run `mvn clean test` once and confirm that it completes successfully.
6. Keep the generated CSV reports available for inspection after the sample run.
7. Close unrelated windows and notifications.

**Important:** Record the demonstration using the final submission copy. If the source code or tests change after this script is prepared, rerun the tests and update any figures shown in the recording.

## Demonstration Timeline

| Time | Section | What to show |
|---|---|---|
| 0:00–0:30 | Introduction | Project purpose, problem statement and team |
| 0:30–1:10 | Project structure | Packages, models, repositories and tests |
| 1:10–1:45 | Build and tests | Maven test execution and result |
| 1:45–3:15 | Sample execution | Run the command file and show processing |
| 3:15–4:30 | Generated reports | Decision log, invoices, statements and misuse report |
| 4:30–5:00 | Closing | Summarise the implementation and verification |

---

## 1. Introduction — 0:00 to 0:30

**Show:** Project open in VS Code.

**Say:**

“Hello. We are Team T052, and our project is the Fuel Station Fleet Card Billing Engine, developed for problem statement PS-051 under the Mobility – Fuel Retail domain.

The objective of this project is to process fleet fuel-card transactions, validate them against business rules, maintain account billing records, generate invoices, process payments and identify suspicious or exceptional activity.

We implemented the solution as a Java command-line application using CSV files and a command file, as permitted by the problem statement.”

## 2. Project Structure — 0:30 to 1:10

**Show:** Expand `src/main/java/com/t052/fuelbilling/` in VS Code.

Briefly show:

- `command` – command-file parsing and command representation.
- `engine` – transaction processing, invoice generation, payment handling, reversals and command execution.
- `model` – fleet accounts, vehicles, cards, outlets, prices, transactions and payments.
- `repository` – loading the CSV input data.
- `src/test` – automated tests for the application.

**Say:**

“We have separated the application into packages so that command parsing, data loading, domain models and business processing are handled independently. This makes the code easier to maintain and allows the main business rules to be tested separately.”

Do not open every source file. Show only the package structure and, if time permits, briefly open `App.java` and `TransactionProcessor.java`.

## 3. Build and Automated Tests — 1:10 to 1:45

**Show:** VS Code terminal in the project root.

Run:

```powershell
mvn clean test
```

Wait for the command to finish. Scroll to the final Maven test summary.

**Say:**

“We use Maven to compile the application and run the JUnit test suite. The project is configured to compile with Java 17 compatibility. Our development machine has Eclipse Temurin JDK 21 installed.

In our latest recorded run, 72 tests completed with zero failures, zero errors and zero skipped tests, and Maven reported BUILD SUCCESS.”

**Recording note:** Only state the 72-test result if the current run shown in the recording confirms it. If the test count has changed, read the actual result from the terminal instead.

## 4. Execute the Sample Workflow — 1:45 to 3:15

**Show:** The sample command file briefly, then return to the terminal.

Run the sample workflow:

```powershell
mvn exec:java "-Dexec.args=data/sample data/sample/commands.txt"
```

Allow the application to finish. Show the terminal output indicating that input data was loaded and commands were executed.

**Say:**

“The application receives two arguments: the input data directory and the command file.

The command file controls the processing sequence. It can load prices, register trip permits, post transactions, block or unblock cards, report lost cards, generate invoices, apply payments and generate reports.

For this sample dataset, the application loads the supplied master data and processes the transaction batches selected by the commands. It then generates the billing and reporting outputs.”

**Show, if visible in the terminal:**

- Number of accounts, vehicles, cards, outlets and price records loaded.
- Command execution messages.
- Transaction batch processing messages.
- Invoice and payment processing messages.
- Final completion status.

Do not spend time reading every transaction decision aloud.

## 5. Inspect the Generated Reports — 3:15 to 4:30

**Show:** The generated CSV files in `data/sample/`. Open each file briefly in VS Code or a spreadsheet application.

### A. `decision_log.csv`

**Say:**

“This report records the decision for each transaction input row, including approved, declined and duplicate transactions. It also records the engine-calculated amount, reason code and applicable flags.”

Show the header and a few representative rows. If possible, point out one approved transaction and one declined transaction.

### B. `invoices.csv` and `invoice_lines.csv`

**Say:**

“These files contain invoice-level totals and the corresponding transaction-level invoice lines. The invoice calculation includes the applicable fuel discount, LUBE GST, reversal credits, interest and convenience fee.”

Show one invoice and a few invoice-line records. Do not claim a specific amount unless it is visible in the generated file.

### C. `statements.csv`

**Say:**

“The account statement records debits, credits and the running balance for each account.”

Show one account's entries.

### D. `misuse_report.csv`

**Say:**

“The misuse report records flagged activity such as unusual mileage, outlet price variance and blocked-card tamper attempts, where applicable.”

Show the header and a few records. Explain the reason shown in the actual output rather than assuming every planted README example matches the CSV data.

## 6. Closing — 4:30 to 5:00

**Show:** Return to the project structure or terminal summary.

**Say:**

“To summarise, the project provides a complete command-driven workflow for fleet fuel-card authorisation and billing. It covers transaction validation, credit and usage limits, offline transactions, reversals, invoice generation, payment allocation, account statements and misuse reporting.

We have organised the code into separate packages and added automated tests for the main processing components and boundary conditions.

Thank you.”

---

## Recording Checklist

- [ ] Recording is approximately five minutes.
- [ ] Project title, problem statement and team ID are stated.
- [ ] Project structure is shown.
- [ ] `mvn clean test` is run and the actual result is visible.
- [ ] The sample command file is executed.
- [ ] The generated decision log is shown.
- [ ] Invoice and invoice-line reports are shown.
- [ ] Account statement is shown.
- [ ] Misuse report is shown.
- [ ] No passwords, personal files, unrelated browser tabs or private notifications are visible.
- [ ] The recording uses the final project version.
- [ ] The video link is accessible to the evaluators.

## Presentation Tips

- Keep the terminal font large enough to read in the recording.
- Avoid scrolling rapidly through long CSV files.
- Do not read the script word for word; use it as a guide and explain the workflow naturally.
- Do not claim that hidden evaluation cases have passed. Describe the tests and sample run that were actually executed.
- If a report contains a sample-data discrepancy, explain that the engine uses the supplied transaction values and the documented business rules rather than hardcoding transaction IDs.
