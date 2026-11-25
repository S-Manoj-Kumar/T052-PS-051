# Fuel Station Fleet Card Billing Engine

**Problem Statement:** PS-051 -- Fuel Station Fleet Card Billing Engine\
**Team:** T052\
**Domain:** Mobility -- Fuel Retail\
**Language:** Java\
**Build Tool:** Apache Maven\
**Test Framework:** JUnit 4

------------------------------------------------------------------------

## 1. Project Overview

The Fuel Station Fleet Card Billing Engine is a command-driven Java
application that manages fuel authorisation and billing for fleet
vehicles.

It allows a fleet operator to maintain accounts, vehicles, fuel cards,
outlets and daily fuel prices. When a transaction is received from a
fuel outlet, the engine checks the applicable business rules and records
an approval or decline decision. Approved transactions are included in
the account ledger and fortnightly invoices. The system also supports
reversals, offline transactions, payments, card status changes and
misuse reporting.

The application is designed as a **CLI and CSV-based backend system**.
It does not require a graphical user interface or a web framework.

## 2. Main Features

-   Load fleet accounts, vehicles, fuel cards and outlet details from
    CSV files.
-   Load daily fuel prices by date, city and fuel product.
-   Authorise fuel transactions and record approval or decline reasons.
-   Validate card status, product permissions, vehicle class, tank
    capacity, daily limits and monthly limits.
-   Validate odometer readings and flag unusual mileage without
    automatically declining an otherwise valid transaction.
-   Apply account credit limits and the permitted night-time diesel
    overdraft.
-   Enforce account state restrictions and active trip permits.
-   Process offline transactions and identify late postings.
-   Process reversals, including the different treatment of reversals
    received within and after 24 hours.
-   Generate fortnightly invoices with applicable discounts, LUBE GST,
    interest and cheque convenience fees.
-   Apply full and partial payments against the oldest outstanding
    invoices first.
-   Generate decision logs, account statements and misuse reports.

## 3. Technology and Prerequisites

The project uses:

  Technology           Purpose
  -------------------- -------------------------------------------------------
  Java 17 or later     Application development and execution
  Apache Maven         Dependency management, compilation and test execution
  JUnit 4              Automated unit testing
  Apache Commons CSV   CSV file handling

Install a compatible JDK and Maven before running the project.

Check the installed versions:

``` bash
java -version
mvn -version
```

> **Version note:** The project brief requires Java 17 or later. Use a
> JDK version compatible with the `maven.compiler.release` configured in
> the project's `pom.xml`.

## 4. Project Structure

The main project structure is:

``` text
fuel-billing-engine/
│
├── data/
│   └── sample/
│       ├── accounts.csv
│       ├── vehicles.csv
│       ├── outlets.csv
│       ├── prices.csv
│       ├── transactions.csv
│       ├── payments_cycle1.csv
│       ├── payments_cycle2.csv
│       ├── commands.txt
│       └── [generated CSV reports]
│
├── src/
│   ├── main/
│   │   └── java/com/t052/fuelbilling/
│   │       ├── App.java
│   │       ├── command/
│   │       ├── engine/
│   │       ├── model/
│   │       │   └── enums/
│   │       └── repository/
│   │
│   └── test/
│       └── java/com/t052/fuelbilling/
│
├── pom.xml
└── README.md
```

### Package responsibilities

-   **`command`** -- Reads and parses the command file and represents
    the commands to be executed.
-   **`engine`** -- Contains transaction authorisation, billing,
    payment, reversal, command execution and shared engine state.
-   **`model`** -- Contains the main domain objects, such as fleet
    accounts, vehicles, fuel cards, outlets, prices, transactions and
    payments.
-   **`model.enums`** -- Contains enumerated values used for card
    status, fuel products and transaction types.
-   **`repository`** -- Loads and provides access to the CSV-based input
    data.
-   **`App.java`** -- Validates the input paths, loads master data and
    starts command execution.

## 5. Input Files

The sample data is stored in `data/sample/`.

  -----------------------------------------------------------------------
  File                                Description
  ----------------------------------- -----------------------------------
  `accounts.csv`                      Fleet account details, credit
                                      limits, billing cycle, discount
                                      inputs, permitted states and
                                      monthly vehicle cap

  `vehicles.csv`                      Vehicle and fuel-card mapping,
                                      vehicle class, tank capacity,
                                      permitted products and initial card
                                      status

  `outlets.csv`                       Fuel outlet identifiers, names,
                                      cities and states

  `prices.csv`                        Daily fuel price for each product
                                      and city

  `transactions.csv`                  Sale and reversal requests received
                                      from outlets

  `payments_cycle1.csv`               Payments for the first invoice
                                      cycle

  `payments_cycle2.csv`               Payments for the second invoice
                                      cycle

  `commands.txt`                      Ordered list of operations to
                                      execute
  -----------------------------------------------------------------------

### Input conventions

-   Dates use `YYYY-MM-DD`.
-   Timestamps use `YYYY-MM-DD HH:MM:SS` in IST, without a timezone
    suffix.
-   Monetary values are entered as plain decimal numbers in rupees,
    without currency symbols or commas.
-   Boolean values use `true` or `false`.
-   List values in CSV fields use semicolon-separated entries.
-   The command file uses the pipe character (`|`) to separate command
    arguments.

The detailed field definitions and planted sample scenarios are
documented in the sample pack at `data/sample/README.md`.

## 6. Running the Application

Open a terminal in the project root directory --- the directory
containing `pom.xml`.

### Step 1: Run the automated tests

``` bash
mvn clean test
```

This command cleans the previous build, compiles the application and
test sources, and runs the JUnit test suite.

### Step 2: Execute the sample command file

On Windows PowerShell:

``` powershell
mvn exec:java "-Dexec.args=data/sample data/sample/commands.txt"
```

On macOS/Linux:

``` bash
mvn exec:java "-Dexec.args=data/sample data/sample/commands.txt"
```

The application takes two arguments:

1.  The directory containing the input CSV files.
2.  The command file to execute.

General format:

``` bash
mvn exec:java "-Dexec.args=<data-directory> <commands-file>"
```

For example, to use a different dataset, place its CSV files in a
separate directory and provide that directory and its command file as
the two arguments.

## 7. Command File Reference

Each non-comment line in `commands.txt` contains one command. Arguments
are separated by `|`. Commands are executed in the order in which they
appear. Blank lines and lines beginning with `#` are ignored.

  ------------------------------------------------------------------------------------------------------
  Command                 Format                                                 Purpose
  ----------------------- ------------------------------------------------------ -----------------------
  `LOAD_PRICES`           `LOAD_PRICES|file`                                     Loads the daily outlet
                                                                                 price data

  `TRIP`                  `TRIP|card_id|state|from_ts|to_ts`                     Registers a trip permit
                                                                                 for a card and state
                                                                                 during a specified
                                                                                 period

  `POST_TRANSACTIONS`     `POST_TRANSACTIONS|file|from_posted_ts|to_posted_ts`   Processes transactions
                                                                                 posted within the
                                                                                 inclusive time window

  `BLOCK_CARD`            `BLOCK_CARD|ts|card_id|who`                            Blocks a card

  `UNBLOCK_CARD`          `UNBLOCK_CARD|ts|card_id`                              Reactivates a blocked
                                                                                 card, subject to card
                                                                                 status rules

  `REPORT_LOST`           `REPORT_LOST|ts|card_id`                               Marks a card as lost

  `RUN_INVOICE`           `RUN_INVOICE|invoice_date`                             Generates invoices for
                                                                                 all accounts

  `APPLY_PAYMENTS`        `APPLY_PAYMENTS|file`                                  Reads and allocates
                                                                                 payments from a payment
                                                                                 file

  `PRINT_DECISION_LOG`    `PRINT_DECISION_LOG`                                   Writes the transaction
                                                                                 decision log

  `PRINT_STATEMENT`       `PRINT_STATEMENT|account_id`                           Writes the specified
                                                                                 account's statement

  `PRINT_MISUSE_REPORT`   `PRINT_MISUSE_REPORT|from_date|to_date`                Writes misuse and
                                                                                 exception records for
                                                                                 the date range
  ------------------------------------------------------------------------------------------------------

For `POST_TRANSACTIONS`, the posting-time range is inclusive.
Transactions within the selected window are processed by `posted_at`,
with `txn_id` used to order transactions having the same posting time.
Business-rule checks use the transaction's original timestamp.

## 8. Business Rules Covered

The engine implements the principal authorisation and billing rules
specified for PS-051.

### 8.1 Product and vehicle validation

-   A card can purchase only products permitted for its vehicle and card
    configuration.
-   Diesel-only and petrol-only vehicle restrictions are enforced.
-   CNG vehicles cannot purchase liquid fuels.
-   LUBE purchases are subject to the monthly rupee cap and are handled
    separately from fuel-volume limits.
-   Invalid product codes are rejected.

### 8.2 Volume and usage limits

-   A single transaction cannot exceed the vehicle's tank capacity.
-   A card is limited to two fuelling transactions per day.
-   Daily fuel quantity cannot exceed 1.5 times the tank capacity.
-   The account's per-vehicle monthly litre cap is enforced.
-   LUBE quantity is excluded from fuel-volume limit calculations.

### 8.3 Odometer validation

-   The new odometer reading must be greater than the previous recorded
    reading.
-   Implied mileage for petrol and diesel is checked against the
    permitted range of 2--25 km/L.
-   An unusual mileage value is approved with a flag when the other
    authorisation conditions pass; it is also included in the misuse
    report.

### 8.4 Credit and geographic controls

-   Transactions are declined when the applicable credit exposure
    exceeds the account's credit limit.
-   A temporary 10% overdraft is permitted for DIESEL transactions
    between 22:00 and 06:00, subject to the specified conditions.
-   Transactions outside an account's permitted states are declined
    unless a valid trip permit covers the state and transaction time.

### 8.5 Price and offline transaction handling

-   The engine amount is calculated using the applicable outlet-city
    product price for the transaction date.
-   A pump amount variance greater than Rs 1 is flagged; the
    engine-calculated amount is used.
-   Offline transactions are checked against the 48-hour posting window.
-   Eligible offline transactions are identified in the decision output.
    Transactions that exceed the permitted window are rejected.

### 8.6 Reversals and billing

-   Reversals are validated against the original transaction.
-   A reversal received within 24 hours restores the applicable usage
    limits.
-   A later reversal is credited through billing but does not restore
    the monthly litre cap.
-   Invoices are generated on the 1st and 16th for the period ending on
    the previous day.
-   Diesel discounts are based on the applicable account tier.
-   LUBE attracts 18% GST.
-   Cheque payments attract the specified convenience fee.
-   Overdue balances attract interest at the specified monthly rate,
    calculated on a daily basis.
-   Payments are allocated to the oldest outstanding invoice first.
    Partial and full payments are supported.

### 8.7 Card lifecycle and misuse monitoring

-   Cards can be ACTIVE, BLOCKED or LOST.
-   A manager can block and unblock a card, subject to lifecycle
    restrictions.
-   Three qualifying declines in a day trigger automatic blocking.
-   Attempts to use a blocked card are recorded for tamper monitoring.
-   LOST is a terminal card state.

## 9. Generated Output Files

The command file can generate the following CSV reports in the data
directory used for the run.

  -----------------------------------------------------------------------
  Output file                         Contents
  ----------------------------------- -----------------------------------
  `decision_log.csv`                  One decision record for each input
                                      transaction row, including rejected
                                      and duplicate rows

  `invoices.csv`                      Invoice-level amounts, credits,
                                      interest, fees and due dates

  `invoice_lines.csv`                 Transaction-level invoice lines and
                                      credits

  `statements.csv`                    Account ledger entries, debits,
                                      credits and running balances

  `misuse_report.csv`                 Flagged transactions, outlet price
                                      variances, tamper alerts and
                                      applicable offline risk records
  -----------------------------------------------------------------------

The output column order follows the format specified in the PS-051
sample data documentation. The reports are written as CSV files with a
header row.

## 10. Testing

Run the complete automated test suite with:

``` bash
mvn clean test
```

The test suite covers command parsing and execution, engine state,
transaction authorisation, boundary conditions, offline posting,
reversals, invoice processing, payments and statement generation.

The latest recorded local test run completed with:

-   **72 tests**
-   **0 failures**
-   **0 errors**
-   **0 skipped**
-   **Build: SUCCESS**

This result refers to the recorded local test run. Re-run the command
above on the final submission copy to confirm its current status.

## 11. Important Implementation Decisions

The following decisions are documented where the project brief leaves
room for interpretation:

-   Declined transactions do not consume the daily transaction count.
-   LUBE litres are excluded from fuel-volume limits; LUBE is controlled
    by its monthly rupee cap.
-   CNG quantity is treated as litres for limit calculations, as
    directed by the sample pack.
-   The offline posting window is checked against the original
    transaction timestamp and the exact 48-hour limit.
-   Payments are allocated to the oldest outstanding invoice first.
-   The sample pack's Sunday due-date case is handled without
    automatically moving the due date to the next working day.
    Therefore, a payment made on the following Monday may incur one
    additional day of interest. This is an implementation choice because
    the brief does not specify a weekend or bank-holiday adjustment.

Refer to the design document for the domain model, processing flows,
business-rule register, validation approach and detailed design
decisions.

## 12. Limitations and Evaluation Notes

-   The application is a command-line backend; it does not provide a
    graphical user interface.
-   Input data is supplied through CSV files and a command file.
-   The sample pack does not include expected output files. Validation
    is performed using the automated test suite and the business rules
    in the project brief.
-   Final evaluation may use a separate hidden dataset with the same
    input schema. Output field names, ordering and formatting must
    therefore be preserved.

## 13. Team

**Team ID:** T052\
**Problem Statement:** PS-051 -- Fuel Station Fleet Card Billing Engine

  Team member
  ----------------
  S. Manoj Kumar ,
  Likith Kumar K and 
  Sumanth G S

------------------------------------------------------------------------

**Project:** Fuel Station Fleet Card Billing Engine\
**Submission:** 10 SECONDS Capstone Project

## 14. Project Demonstration

### Demo Video

[Watch the Fuel Station Fleet Card Billing Engine Demonstration](https://drive.google.com/file/d/1XpoUESvRMtb6Z_c_m-o7ylOWXmAhZSiw/view?usp=sharing)

### Design Document

[View T052_PS051_Design_Document.pdf](https://drive.google.com/file/d/1vJ6DpF7YsPZdf5Kw3yBnljTFP3S7W2Uh/view?usp=sharing)
