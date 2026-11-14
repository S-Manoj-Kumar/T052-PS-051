# Requirements Compliance Audit

**Project:** Fuel Station Fleet Card Billing Engine  
**Problem Statement:** PS-051 – Fuel Station Fleet Card Billing Engine  
**Team ID:** T052  
**Review date:** 14 November 2026

---

## 1. Purpose

This document maps the requirements stated in the PS-051 problem brief and sample data documentation to the project implementation and available verification evidence.

The purpose is to make the scope of the implementation clear and to identify areas that need attention during final validation. A requirement is not marked as fully verified merely because related code exists; verification status is based on the recorded automated tests and sample-run evidence available to the team.

## 2. Status Definitions

| Status | Meaning |
|---|---|
| **Implemented** | The functionality is present in the application based on the reviewed implementation information. |
| **Tested** | A relevant automated test or recorded sample execution provides evidence for the requirement. |
| **Partially verified** | Functionality is present, but available evidence does not establish every required boundary or scenario. |
| **Clarification required** | The brief or supplied sample data is ambiguous or internally inconsistent. |

## 3. Functional Requirement Audit

| ID | Requirement | Implementation area | Status / evidence |
|---|---|---|---|
| FR-01 | Load fleet accounts and account-level billing and credit information. | CSV repositories and data loading | Implemented; sample run loaded 6 accounts. |
| FR-02 | Load vehicles, fuel cards, vehicle classes, tank capacities and product permissions. | Vehicle and fuel-card repositories | Implemented; sample run loaded 40 vehicles and 40 cards. |
| FR-03 | Load outlet details and daily outlet prices by city and product. | Outlet and fuel-price repositories; price command | Implemented; sample run loaded 9 outlets and 360 price records. |
| FR-04 | Read and execute the supported command-file operations in order. | Command parser and command executor | Tested by command parser and executor test classes; sample run executed 24 commands. |
| FR-05 | Authorise sale transactions and record approval or decline reasons. | Transaction processor and decision model | Tested; transaction processor test class and sample decision log. |
| FR-06 | Post approved transactions to account billing records. | Transaction and invoice processing | Implemented; sample run generated invoices and statements. |
| FR-07 | Support transaction reversals. | Reversal processor | Tested by reversal processor tests; sample data also contains invalid reversal references. |
| FR-08 | Support eligible offline transactions and late-posting checks. | Transaction processor and offline risk handling | Tested by offline risk ledger tests; sample run exercised offline cases. |
| FR-09 | Generate fortnightly invoices. | Invoice processor and invoice command | Tested by invoice processor tests; sample run generated 12 invoices across two invoice dates. |
| FR-10 | Apply full and partial payments. | Payment processor | Tested by payment processor tests; sample run applied 8 payment rows across the two payment files. |
| FR-11 | Generate transaction decision logs, invoices, invoice lines, statements and misuse reports. | Command executor and report writers | Implemented; sample run generated all five report types. |
| FR-12 | Support card block, unblock and lost-card operations. | Command executor and card lifecycle logic | Tested by command executor tests; sample execution exercised card lifecycle commands. |
| FR-13 | Support trip permits for state-restricted accounts. | Trip permit model and command executor | Implemented; sample run executed trip-permit commands. |
| FR-14 | Produce CSV outputs in the required field order. | Report writers | Output headers were checked against the specified schemas in the recorded sample run. Recheck the final generated files before submission. |

## 4. Business Rule Compliance Audit

### 4.1 Product restrictions

| Rule | Required behaviour | Evidence / status |
|---|---|---|
| BR-01 | Enforce product permissions and vehicle-class restrictions; CNG cards cannot purchase liquid fuels. | Transaction processor tests and sample invalid-product / product-restriction cases. |
| BR-02 | Permit LUBE subject to the monthly monetary cap; do not include LUBE litres in fuel-volume limits. | LUBE handling is implemented separately. Sample scenario TXN-0226 is expected to exercise the LUBE cap. Confirm the final decision row is `LUBE_CAP`. |

### 4.2 Transaction volume and usage limits

| Rule | Required behaviour | Evidence / status |
|---|---|---|
| BR-03 | A transaction cannot exceed tank capacity. | Boundary scenario TXN-0028; exact tank-capacity quantity is expected to be accepted. |
| BR-04 | Limit fuelling to two transactions per card per day. | Transaction processor tests; inspect the final sample decision log for the relevant daily-count cases. |
| BR-05 | Limit daily fuel volume to 1.5 times tank capacity. | Boundary scenario CARD-004: 145 L + 290 L = 435 L, exactly 1.5 × 290 L, expected to be accepted. |
| BR-06 | Enforce the account's per-vehicle monthly litre cap. | Transaction processor tests and planted monthly-cap scenarios, including CARD-029. |
| BR-07 | Treat CNG quantity as litres for quantity limits. | Implemented according to the sample pack's explicit instruction. |

### 4.3 Odometer and mileage

| Rule | Required behaviour | Evidence / status |
|---|---|---|
| BR-08 | New odometer reading must be greater than the previous recorded reading. | Transaction processor tests and sample invalid-odometer cases. |
| BR-09 | Petrol/diesel implied mileage outside 2–25 km/L must be approved with a flag when other checks pass. | Odometer boundary tests cover the boundary behaviour; sample data includes mileage anomaly cases. |
| BR-10 | Exactly 2 km/L and 25 km/L are valid boundary values. | Odometer boundary test class is present; sample README and CSV data contain conflicting examples that need clarification. |

### 4.4 Credit and geographic restrictions

| Rule | Required behaviour | Evidence / status |
|---|---|---|
| BR-11 | Decline when the applicable credit exposure exceeds the credit limit. | Transaction processor tests and sample credit-limit scenario. |
| BR-12 | Permit the specified 10% diesel overdraft only from 22:00 to 06:00. | Sample scenarios TXN-0113 at 21:59 (decline) and TXN-0114 at 22:00 (eligible for the exception). |
| BR-13 | Enforce account state restrictions unless a valid trip permit covers the state and transaction time. | Trip command and sample ACC-004 / CARD-025 scenario. |

### 4.5 Price and offline handling

| Rule | Required behaviour | Evidence / status |
|---|---|---|
| BR-14 | Calculate the engine amount from litres and the applicable date/city/product price. | Price loading and transaction processing; sample execution generated engine amounts in the decision log. |
| BR-15 | Flag pump variance only when the absolute difference is greater than Rs 1; use engine amount. | Sample cases include an exact Rs 1 difference (no variance flag) and a Rs 3.50 difference (variance flag). |
| BR-16 | Accept offline posting within 48 hours; reject posting beyond 48 hours. | Offline risk ledger tests and sample scenarios TXN-0116 (47h59) and TXN-0118 (48h01). |
| BR-17 | Re-evaluate an eligible offline transaction using the original transaction timestamp; route transactions that would have failed authorisation to the outlet-risk treatment specified in the brief. | Offline risk ledger test class is present. Verify the final sample output and risk-ledger records for the planted cases. |

### 4.6 Discounts, invoices and tax

| Rule | Required behaviour | Evidence / status |
|---|---|---|
| BR-18 | Determine discount tier from previous-month litres using the stated thresholds. | Invoice processing tests; sample ACC-002 has exactly 20,000 L and is expected to be GOLD. |
| BR-19 | Apply the tier's per-litre discount to DIESEL only. | Invoice processor tests and invoice-line output. |
| BR-20 | Generate invoices on the 1st and 16th for periods ending the previous day. | Invoice processor tests; sample execution generated 6 invoices for each of two invoice dates. |
| BR-21 | Treat fuel VAT as already included and informational. | Documented implementation decision; verify the final invoice schema and any requested informational breakdown against mentor guidance. |
| BR-22 | Apply 18% GST separately to LUBE. | Invoice processor tests and invoice output fields for LUBE GST. |
| BR-23 | Apply the 0.5% convenience fee to cheque invoices. | Invoice processing implementation and tests; inspect a cheque-mode invoice in the generated output. |

### 4.7 Payments and overdue balances

| Rule | Required behaviour | Evidence / status |
|---|---|---|
| BR-24 | Payments are due seven days after invoice date. | Invoice processor implementation and generated due-date fields. |
| BR-25 | Apply overdue interest at 2% per month, prorated daily. | Payment processor tests and invoice/statement calculations. |
| BR-26 | Reduce credit limit by 25% after two consecutive overdue invoices until both are cleared. | Requires explicit final-output verification for the planted multi-invoice scenario; do not treat the general payment tests alone as proof of every sequence. |
| BR-27 | Allocate payments to the oldest outstanding invoice first. | Payment processor tests cover payment allocation. |
| BR-28 | Support partial payments and `FULL` payments. | Payment processor tests cover partial and full payment handling. |
| BR-29 | Retain excess payment as account credit. | Payment processor test output records excess payment retained as account credit. |

### 4.8 Card lifecycle and reversals

| Rule | Required behaviour | Evidence / status |
|---|---|---|
| BR-30 | Support ACTIVE → BLOCKED and BLOCKED → ACTIVE through permitted commands. | Command executor tests and sample command execution. |
| BR-31 | Automatically block after three qualifying declines in a day. | Transaction processor tests and sample CARD-030 scenario. |
| BR-32 | LOST is terminal and cannot be unblocked. | Command executor test verifies that an attempt to unblock a lost card is rejected. |
| BR-33 | Record blocked-card attempts and raise a tamper alert at the specified threshold. | Sample CARD-016 scenario and command executor test coverage; confirm final misuse report output. |
| BR-34 | A valid reversal within 24 hours restores applicable usage limits. | Reversal processor tests. |
| BR-35 | A valid reversal after 24 hours gives billing credit without restoring monthly litres. | Reversal processor tests; sample TXN-0219 is a late-reversal scenario. |
| BR-36 | Reject reversals that do not reference an eligible original sale. | Reversal processor tests and sample invalid-reference cases. |

## 5. Required Output Audit

The expected output headers are taken from the sample pack documentation.

| Output file | Required columns | Verification note |
|---|---|---|
| `decision_log.csv` | `txn_id,card_id,account_id,timestamp,product,litres,engine_amount,decision,reason_code,variance_flag,offline_late` | The recorded sample run generated 333 transaction decisions, including a row for the duplicate input. |
| `invoices.csv` | `invoice_id,account_id,invoice_date,period_from,period_to,tier,fuel_amount,lube_amount,lube_gst,discount_credit,reversal_credit,prior_balance,interest,convenience_fee,total_due,due_date` | The recorded sample run generated 12 invoices. |
| `invoice_lines.csv` | `invoice_id,line_no,line_type,txn_id,txn_date,product,litres,rate,amount` | Generated during invoice processing; compare header and line calculations with the final output. |
| `statements.csv` | `account_id,entry_date,entry_type,reference,debit,credit,balance` | The recorded sample run generated statements for all 6 accounts. |
| `misuse_report.csv` | `txn_id,card_id,account_id,outlet_id,timestamp,flag_type,detail` | The recorded sample run generated 16 records. The count differs from the README's stated 15 expected examples and needs review, as described below. |

## 6. Recorded Automated Test Evidence

The latest user-provided local run on 14 November 2026 reported:

| Test class | Tests run |
|---|---:|
| `AppTest` | 1 |
| `CommandFileParserTest` | 5 |
| `CommandExecutorTest` | 8 |
| `EngineContextTest` | 6 |
| `EngineStateTest` | 12 |
| `InvoiceProcessorTest` | 4 |
| `OdometerBoundaryTest` | 2 |
| `OfflineRiskLedgerTest` | 4 |
| `PaymentProcessorTest` | 4 |
| `ReversalProcessorTest` | 5 |
| `TransactionProcessorTest` | 21 |
| **Total** | **72** |

| Result | Count |
|---|---:|
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |
| Build | **SUCCESS** |

The run compiled 39 main source files and 11 test source files using `javac [debug release 17]` on Eclipse Temurin OpenJDK 21.0.13 LTS.

## 7. Sample Data Observations Requiring Attention

These are observations from comparing the sample pack's descriptive README with the supplied transaction data and recorded execution. They should not be hidden by hardcoding transaction IDs or altering business logic to force expected counts.

### 7.1 Mileage boundary examples

The sample README describes TXN-0075 and TXN-0076 as exact mileage boundary cases. The supplied transaction values and preceding odometer readings calculate to approximately 65.41 km/L and 134.10 km/L respectively, not 2.0 and 25.0 km/L.

The engine should calculate mileage from the actual input values. Request clarification if the descriptive examples are expected to override the CSV records.

### 7.2 Mileage anomaly list

The sample README identifies 13 transactions as mileage anomalies. Recalculation from the supplied odometer sequence indicates that six of the named rows are within the 2–25 km/L range:

- TXN-0217 / CARD-001
- TXN-0240 / CARD-006
- TXN-0229 / CARD-028
- TXN-0268 / CARD-008
- TXN-0282 / CARD-014
- TXN-0304 / CARD-020

The remaining named rows are outside the range. The implementation uses calculated mileage rather than forcing all 13 named IDs to be flagged.

### 7.3 Misuse report count

The sample README describes 15 misuse records as 13 mileage flags and 2 price variances. The recorded sample run generated 16 records. The report also includes a blocked-card tamper alert, which is a separately required misuse event. The difference in count should be reconciled with the mentor and the final CSV before submission.

### 7.4 Reversal references

Some planted reversal rows refer to transactions that were invalid or declined. These are rejected because a reversal cannot create a credit for an ineligible original transaction. Examples identified in the sample data include TXN-0237, TXN-0259 and TXN-0295.

### 7.5 Duplicate transaction

TXN-0180 appears more than once. The repeated row is recorded as a duplicate rather than being processed as a second sale. The first row's own authorisation outcome is determined by its transaction data and applicable rules.

## 8. Final Pre-Submission Checklist

Use this checklist against the exact copy that will be submitted.

- [ ] Confirm the final `pom.xml` retains `<maven.compiler.release>17</maven.compiler.release>`.
- [ ] Run `java -version` and confirm a supported JDK is installed.
- [ ] Run `mvn clean test` and retain the final successful test output.
- [ ] Run the complete sample command file from a clean output directory.
- [ ] Confirm all 24 commands execute in the required order.
- [ ] Check all five generated CSV files exist.
- [ ] Check every output header and column order against the sample pack.
- [ ] Inspect the planted edge cases listed in the sample pack README.
- [ ] Review the 16-row misuse report and document the count discrepancy.
- [ ] Confirm invoice totals, payment allocation and statement balances for representative accounts.
- [ ] Confirm the README, design document and decision log match the final source code.
- [ ] Confirm the Git repository contains the required source, tests and documents.
- [ ] Add the final design document PDF and demo video link when ready.
- [ ] Verify the repository link and submission email before sending.

## 9. Overall Assessment

The project has implemented the main command-driven transaction, billing, payment, reversal and reporting workflows required by PS-051. The latest recorded automated test run completed successfully with 72 tests and no failures, errors or skipped tests. The supplied sample command sequence was also reported to execute all 24 commands and generate the required report files.

This audit does not claim that every hidden evaluation scenario has been independently verified. In particular, the sample README/CSV mileage inconsistencies, misuse-report count difference, and selected invoice/payment boundary cases should be reviewed against the final generated outputs and clarified where necessary.

---

**Team ID:** T052  
**Problem Statement:** PS-051 – Fuel Station Fleet Card Billing Engine
