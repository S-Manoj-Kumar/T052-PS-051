# Design Decision Log

**Project:** Fuel Station Fleet Card Billing Engine  
**Problem Statement:** PS-051 – Fuel Station Fleet Card Billing Engine  
**Team ID:** T052  
**Technology:** Java 17+ · Apache Maven · JUnit 4  
**Document date:** 14 November 2026

---

## 1. Purpose

This document records the important design and implementation decisions made while developing the Fuel Station Fleet Card Billing Engine.

The log explains how the project handles requirements where an implementation choice was necessary, how the chosen approach supports the stated business rules, and which assumptions should be kept in mind during evaluation.

The decisions are based on the project brief, sample data documentation, implemented command-driven workflow and the team's recorded test results.

## 2. Decision Register

| ID | Area | Decision | Reason / effect |
|---|---|---|---|
| D-01 | Application interface | Implement the solution as a Java command-line application driven by CSV input files and a command file. | The problem statement permits a CLI/file-driven backend and does not require a graphical interface or web framework. |
| D-02 | Java compatibility | Configure Maven compilation with `maven.compiler.release` set to `17`. | This meets the Java 17+ requirement while allowing development on a newer installed JDK. |
| D-03 | Project structure | Separate command parsing, domain models, repositories and processing logic into packages. | This keeps file parsing, business data and rule processing distinct and easier to test. |
| D-04 | Monetary calculations | Use `BigDecimal` for monetary values and price/amount calculations. | Decimal arithmetic is appropriate for billing and avoids relying on binary floating-point calculations for currency. |
| D-05 | Transaction processing order | Select transactions using the requested `posted_at` window, then process them by posting time and transaction ID. | This follows the sample pack's command-processing requirement while keeping the business checks tied to the original transaction time. |
| D-06 | Business-rule timestamp | Evaluate time-dependent business rules using the transaction's original timestamp. | Limits, trip permits and the night-time overdraft relate to when the fuelling transaction occurred, not when the outlet later submitted it. |
| D-07 | Duplicate transactions | Process a transaction ID once; record a repeated transaction ID as a duplicate decision. | Prevents a repeated input row from creating a second financial or usage impact while retaining an auditable decision for the duplicate row. |
| D-08 | Declined transaction usage | A declined transaction does not consume the card's daily fuelling count or fuel-volume allowance. | A declined authorisation must not be treated as a completed fuelling event. |
| D-09 | LUBE volume treatment | Exclude LUBE litres from the fuel tank, daily fuel-volume and monthly fuel-litre calculations; enforce the separate monthly LUBE rupee cap. | The sample pack specifies a separate monetary cap for LUBE rather than treating it as vehicle fuel consumption. |
| D-10 | CNG quantity | Treat CNG quantity as litres for the applicable quantity-limit calculations. | This follows the sample pack's explicit instruction for limit calculations. |
| D-11 | Odometer anomaly | Require the new odometer reading to exceed the previous reading. If implied petrol/diesel mileage is outside 2–25 km/L, approve the transaction with a flag when other checks pass. | An unusual mileage reading is a misuse indicator, but the specified rule calls for an approved-and-flagged outcome rather than an automatic decline. |
| D-12 | Mileage boundaries | Treat 2 km/L and 25 km/L as valid boundary values. | The stated permitted range includes both endpoints. |
| D-13 | Credit exposure | Decline a transaction when the resulting applicable credit exposure exceeds the account's permitted limit, except where the specified temporary overdraft applies. | Keeps authorisation tied to the account's available credit and the stated exception. |
| D-14 | Night overdraft | Apply the temporary 10% overdraft only to DIESEL transactions in the 22:00–06:00 time window, subject to the other authorisation conditions. | Implements the narrow exception in the business rules; it is not a general increase to the account's credit limit. |
| D-15 | Price calculation | Calculate the engine amount from transaction litres multiplied by the applicable outlet-city product price for the transaction date. | The engine's amount is based on the configured price record rather than the amount supplied by the pump. |
| D-16 | Price variance | Flag a pump/engine amount difference only when the absolute difference is greater than Rs 1; use the engine-calculated amount. | A difference of exactly Rs 1 is within the stated tolerance. A larger difference is reported as an outlet variance. |
| D-17 | Offline posting window | Accept an offline transaction only within the permitted 48-hour interval from its original transaction timestamp; identify eligible offline postings in the decision output. | Enforces the sample pack's exact time window and distinguishes late outlet submission from the original transaction event. |
| D-18 | Offline risk | Where the offline transaction is received within the permitted window but would have failed the applicable authorisation checks at the original transaction time, retain it as a posted outlet-risk transaction as required by the brief. | The offline rule assigns the resulting exposure to the outlet rather than silently treating it as a normal successful card authorisation. |
| D-19 | Reversal within 24 hours | For a valid reversal received within 24 hours, restore the applicable usage limits and issue the corresponding reversal credit. | This follows the reversal rule for timely reversals. |
| D-20 | Reversal after 24 hours | For a valid reversal received after 24 hours, apply the credit through billing but do not restore the monthly litre cap. | Preserves the different treatment required for late reversals. |
| D-21 | Reversal validation | A reversal must refer to a valid eligible original transaction; an invalid, declined or otherwise ineligible original sale cannot be reversed. | Prevents a reversal from creating an unsupported credit or restoring usage for a transaction that was never successfully posted. |
| D-22 | Invoice schedule | Generate invoices for all accounts on the 1st and 16th, covering the period ending on the day before the invoice date. | Follows the fortnightly billing schedule in the problem statement. |
| D-23 | Discount tier | Apply the account's applicable tier discount to DIESEL litres only, using the tier thresholds and per-litre rates in the brief. | Keeps the discount calculation product-specific and consistent with the defined tier rules. |
| D-24 | LUBE tax | Calculate 18% GST separately on LUBE purchases. | The brief specifies separate GST treatment for LUBE. |
| D-25 | Fuel VAT | Treat fuel VAT as already included in the fuel price and show it as informational rather than adding it again to the invoice. | Avoids double-counting tax in the invoice total. |
| D-26 | Cheque fee | Apply the specified 0.5% convenience fee to invoices paid by cheque. | Implements the payment-mode fee described in the billing rules. |
| D-27 | Interest | Calculate overdue interest at 2% per month on a daily prorated basis, using the outstanding balance and applicable overdue period. | Implements the stated overdue-interest rule. |
| D-28 | Invoice due date on a non-working day | Do not automatically move a Sunday due date to the next working day because the brief does not define a weekend or bank-holiday adjustment. | Keeps the implementation aligned with the stated due date instead of introducing an unsupported calendar rule. The sample Sunday case can therefore result in one additional day of interest when payment is made on Monday. |
| D-29 | Payment allocation | Allocate payments to the oldest outstanding invoice first; support both partial amounts and `FULL` payments. | Follows the payment-allocation instruction and gives a predictable order when an account has multiple unpaid invoices. |
| D-30 | Excess payment | Retain payment amount left after outstanding invoices are cleared as account credit. | Avoids discarding money received from an account and allows it to be considered in subsequent account balances. |
| D-31 | Statement carry-forward | Carry forward the previous outstanding balance separately from the current invoice's newly calculated interest. | Prevents the carry-forward credit from incorrectly cancelling interest charged on the new invoice. |
| D-32 | Card lifecycle | Support ACTIVE, BLOCKED and LOST states; LOST is terminal and cannot be unblocked. | Enforces the lifecycle restrictions in the business rules. |
| D-33 | Automatic blocking | Automatically block a card after three qualifying declines in a day. | Implements the specified repeated-decline control. |
| D-34 | Blocked-card attempts | Record attempts to use a blocked card and raise the specified tamper alert when the threshold is reached. | Retains evidence of attempted use after a card has been blocked. |
| D-35 | Output files | Generate the decision log, invoices, invoice lines, statements and misuse report as CSV files with the required column order. | Supports auditability and the field-for-field output format expected by the evaluation. |
| D-36 | Test strategy | Use JUnit tests for individual processing rules and boundary conditions, and run the supplied sample command sequence as an integration check. | Combines focused rule testing with a broader end-to-end execution of the file-driven workflow. |

## 3. Clarifications and Data Observations

The following points were identified while examining the supplied sample pack. They are recorded separately from implementation decisions because the source material does not fully resolve them.

### 3.1 Mileage examples in the sample pack

Some mileage examples described in the sample pack's README do not appear to match the mileage calculated from the corresponding transaction rows and preceding odometer readings in the supplied CSV data.

The implementation applies the mileage rule to the actual transaction values and does not hardcode transaction IDs or force a particular outcome to match a descriptive example. The discrepancy should be clarified with the project mentor if the sample README and CSV data are expected to agree exactly.

### 3.2 Reversal references

The sample data contains reversal rows whose referenced transactions are not eligible successful sales, including transactions that were invalid or declined. Such reversals are rejected as invalid reversal references rather than generating credits.

### 3.3 Weekend due-date handling

The brief specifies a due date seven days after invoice generation but does not specify a weekend or bank-holiday adjustment. The implementation therefore retains the calculated due date without rolling it forward.

### 3.4 Fuel VAT representation

The brief states that fuel VAT is already included and informational. The implementation does not add a second fuel VAT amount to the invoice total. The sample schema should be consulted if a separate informational VAT breakdown is required in a future version.

## 4. Verification Record

The latest recorded local verification on **14 November 2026** used:

```powershell
java -version
mvn clean test
```

Environment and result:

| Item | Recorded result |
|---|---|
| Installed JDK | Eclipse Temurin OpenJDK 21.0.13 LTS |
| Maven compiler release | 17 |
| Main Java source files compiled | 39 |
| Test source files compiled | 11 |
| Tests run | 72 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |
| Maven result | BUILD SUCCESS |

This verification record describes the local run shown above. The test command should be run again against the exact final submission copy before submission.

## 5. Change Control

If a business rule or implementation decision changes:

1. Update the relevant code and automated tests.
2. Re-run `mvn clean test`.
3. Update the corresponding decision in this document.
4. Record the reason for the change and the date.

---

**Team:** T052  
**Project:** Fuel Station Fleet Card Billing Engine  
**Problem Statement:** PS-051
