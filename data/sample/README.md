# PS-051 – Fuel Station Fleet Card Billing Engine — Sample Data Pack

Prepared by 10 SECONDS for Team T052.

Domain: Mobility — Fuel Retail. Build the fleet-card authorisation and billing engine an oil company uses so transport companies can fuel vehicles at outlets on credit, with per-vehicle and per-card limits, product restrictions, odometer checks, fuel-price capture at the pump, and fortnightly invoicing with discounts, GST and interest. Fleet managers use it to catch misuse and reconcile bills. Command-file driven; no UI.

## Files

| File | What it holds | Rows |
|---|---|---|
| `accounts.csv` | Fleet accounts with credit limit, discount tier inputs and geo-fence | 6 |
| `vehicles.csv` | Fleet cards, one per vehicle | 40 |
| `outlets.csv` | Fuel outlets (3 cities) | 9 |
| `prices.csv` | Daily pump price per product per city, 2026-10-01 to 2026-10-30 | 360 |
| `transactions.csv` | Pump authorisation requests and reversals (process in posted_at order within each POST_TRANSACTIONS window) | 333 |
| `payments_cycle1.csv` | Payments against the 2026-10-16 invoice | 4 |
| `payments_cycle2.csv` | Payments against the 2026-11-01 invoice | 4 |
| `commands.txt` | Command file; process strictly in file order. | 24 |

Row counts exclude the header line (CSV) and comment lines (commands.txt).

## Column reference

Conventions: dates `YYYY-MM-DD`; timestamps `YYYY-MM-DD HH:MM:SS` (24h, IST, no timezone suffix); money in Rs as plain decimals with at most 2 decimals (no commas, no symbol); percentages as plain numbers (12.5 = 12.5%); booleans `true`/`false`; IDs uppercase with a fixed prefix.

### `accounts.csv`

| Column | Type | Units / format | Allowed values | Notes |
|---|---|---|---|---|
| `account_id` | string | ACC-nnn |  | Primary key |
| `account_name` | string |  |  |  |
| `credit_limit` | decimal | Rs | >0 | Hard limit before the 22:00–06:00 DIESEL overdraft |
| `billing_cycle` | enum |  | FORTNIGHTLY | Invoices on the 1st and 16th |
| `initial_tier` | enum |  | SILVER/GOLD/PLATINUM | Tier on file before October; recompute from prev_month_litres |
| `payment_mode` | enum |  | NEFT/CHEQUE | CHEQUE accounts pay the 0.5% convenience fee |
| `allowed_states` | list | semicolon-separated state names |  | Geo-fence; empty never occurs here |
| `per_vehicle_monthly_cap_litres` | integer | litres | >0 | Monthly litre cap per vehicle |
| `prev_month_litres` | integer | litres | >=0 | September DIESEL litres; decides the October discount tier |

### `vehicles.csv`

| Column | Type | Units / format | Allowed values | Notes |
|---|---|---|---|---|
| `card_id` | string | CARD-nnn |  | Primary key; used in transactions |
| `vehicle_id` | string | VEH-nnn |  |  |
| `account_id` | string | ACC-nnn | accounts.csv | FK |
| `registration_no` | string |  |  |  |
| `vehicle_class` | enum |  | DIESEL_TRUCK/PETROL_CAR/CNG_VAN | Drives product restriction |
| `tank_capacity_litres` | integer | litres (kg for CNG) | >0 | Per-transaction litre limit |
| `allowed_products` | list | semicolon-separated | PETROL/DIESEL/CNG/LUBE | LUBE allowed on all cards up to Rs 2,000 per month |
| `card_status` | enum |  | ACTIVE | Status at 2026-10-01 00:00:00 |

### `outlets.csv`

| Column | Type | Units / format | Allowed values | Notes |
|---|---|---|---|---|
| `outlet_id` | string | OUT-nnn |  | Primary key |
| `outlet_name` | string |  |  |  |
| `city` | string |  | Bengaluru/Chennai/Hyderabad | Price lookup key |
| `state` | string |  |  | Geo-fence key |

### `prices.csv`

| Column | Type | Units / format | Allowed values | Notes |
|---|---|---|---|---|
| `price_date` | date | YYYY-MM-DD |  |  |
| `city` | string |  |  | matches outlets.csv city |
| `product` | enum |  | PETROL/DIESEL/CNG/LUBE | LUBE priced per litre |
| `price_per_litre` | decimal | Rs | >0 | VAT-inclusive pump price |

### `transactions.csv`

| Column | Type | Units / format | Allowed values | Notes |
|---|---|---|---|---|
| `txn_id` | string | TXN-nnnn |  | Unique except the planted duplicate |
| `txn_type` | enum |  | SALE/REVERSAL | REVERSAL rows carry the original sale in ref_txn_id |
| `ref_txn_id` | string | TXN-nnnn |  | Empty for SALE |
| `timestamp` | timestamp | YYYY-MM-DD HH:MM:SS |  | Pump time; limits are evaluated as of this time |
| `posted_at` | timestamp | YYYY-MM-DD HH:MM:SS |  | When the outlet posted it; > timestamp + 48 h means late offline |
| `card_id` | string | CARD-nnn | vehicles.csv | FK |
| `outlet_id` | string | OUT-nnn | outlets.csv | FK |
| `product` | string |  | PETROL/DIESEL/CNG/LUBE (one invalid value planted) |  |
| `litres` | integer | litres | >0 | Quantity requested |
| `pump_amount` | decimal | Rs | >0 | Amount reported by the pump; compare with litres x price |
| `odometer_km` | integer | km | >0 | Odometer reading entered at the pump |
| `offline` | boolean |  | true/false | true = posted later by the outlet with OFFLINE flag |

### `payments_cycle1.csv`

| Column | Type | Units / format | Allowed values | Notes |
|---|---|---|---|---|
| `payment_date` | date | YYYY-MM-DD |  | Value date |
| `account_id` | string | ACC-nnn | accounts.csv | FK |
| `amount` | decimal or FULL | Rs | >0 or the literal FULL | FULL = pay the entire outstanding balance as of payment_date |
| `mode` | enum |  | NEFT/CHEQUE |  |
| `reference` | string |  |  | UTR or cheque number |

### `payments_cycle2.csv`

| Column | Type | Units / format | Allowed values | Notes |
|---|---|---|---|---|
| `payment_date` | date | YYYY-MM-DD |  | Value date |
| `account_id` | string | ACC-nnn | accounts.csv | FK |
| `amount` | decimal or FULL | Rs | >0 or the literal FULL | FULL = pay the entire outstanding balance as of payment_date |
| `mode` | enum |  | NEFT/CHEQUE |  |
| `reference` | string |  |  | UTR or cheque number |

### General notes

- Interest, tier and convenience-fee computations use the invoice date; the October tier is decided once from prev_month_litres and the November tier from October litres.
- Where the brief is silent: a declined transaction does not consume daily transaction count; LUBE litres never count toward volume limits; CNG quantity is treated as litres for all limit arithmetic.

## Command file format

`commands.txt` holds one command per line in the form `COMMAND_NAME|arg1|arg2|...`. Commands MUST be processed strictly in file order. Lines starting with `#` are comments and blank lines are ignored. Every command used in this pack, with its argument order:

| Command | Arguments (in order) | Meaning |
|---|---|---|
| `LOAD_PRICES` | `file` | Load the daily price table |
| `TRIP` | `card_id|state|from_ts|to_ts` | Open a trip permit allowing the card to fuel in that state during the window |
| `POST_TRANSACTIONS` | `file|from_posted_ts|to_posted_ts` | Authorise/post every row of the file whose posted_at falls in [from, to], in posted_at order (ties by txn_id); limits are evaluated as of the row's timestamp |
| `BLOCK_CARD` | `ts|card_id|who` | Fleet-manager block effective at ts |
| `UNBLOCK_CARD` | `ts|card_id` | Return a BLOCKED card to ACTIVE; must be rejected for LOST cards |
| `REPORT_LOST` | `ts|card_id` | Terminal LOST state from ts |
| `RUN_INVOICE` | `invoice_date` | Generate fortnightly invoices for all accounts for the period ending the day before invoice_date |
| `APPLY_PAYMENTS` | `file` | Apply the payment file (oldest invoice first) |
| `PRINT_DECISION_LOG` | `` | Write decision_log.csv |
| `PRINT_STATEMENT` | `account_id` | Append the account's ledger to statements.csv |
| `PRINT_MISUSE_REPORT` | `from_date|to_date` | Write misuse_report.csv |

## Planted scenarios

Every edge case from the brief is planted at the location given. Line numbers count the header as line 1 for CSVs and count every physical line (including comments) for commands.txt.

- **EC1: Litres exactly equal to tank capacity** — transactions.csv TXN-0028 (line 29) card CARD-003 on 2026-10-03 07:15:00 buys exactly 330 L (tank_capacity_litres=330 in vehicles.csv)
- **EC2: Second transaction of the day bringing litres to exactly 1.5x tank capacity** — transactions.csv TXN-0054 (line 55) card CARD-004 2026-10-05: 145 L at 08:00:00 then TXN-0058 (line 59) 290 L at 18:30:00 = 435 L = 1.5 x 290 (allowed, not over)
- **EC3: Implied km per litre exactly 2.0 and 25.0** — transactions.csv TXN-0075 (line 76) card CARD-005 2026-10-07 09:00:00: 100 L after exactly 200 km (2.0 km/L, in range); TXN-0076 (line 77) card CARD-009 2026-10-07 10:00:00: 20 L after exactly 500 km (25.0 km/L, in range). Both must be APPROVED, not flagged.
- **EC4: Transaction at 21:59 vs 22:00 relying on the overdraft** — ACC-006 (credit_limit 60000.00) has six 100 L DIESEL fills 2–9 Oct at OUT-001 (~54,000–55,800 unbilled). transactions.csv TXN-0113 (line 114) CARD-038 at 2026-10-10 21:59:00 for 80 L pushes exposure over 60,000 → DECLINED (no overdraft before 22:00); TXN-0114 (line 115) CARD-039 at 2026-10-10 22:00:00 for 80 L DIESEL is within the 10% overdraft (66,000) → APPROVED.
- **EC5: Offline transaction posted at 47 h 59 min versus 48 h 01 min** — transactions.csv TXN-0116 (line 117) CARD-012 timestamp 2026-10-11 06:30:00 posted_at 2026-10-13 06:29:00 (47 h 59 min, OFFLINE accepted); TXN-0118 (line 119) CARD-013 timestamp 2026-10-11 07:30:00 posted_at 2026-10-13 07:31:00 (48 h 01 min, outside the 48 h window — must be rejected as LATE_OFFLINE)
- **EC6: Previous month litres exactly 20,000** — accounts.csv ACC-002 prev_month_litres = 20000 → tier for October must be GOLD (20,000–60,000 band), not SILVER
- **EC7: Invoice due on a Sunday with payment on Monday** — Invoice dated 2026-11-01 (RUN_INVOICE in commands.txt) is due 2026-11-08, a Sunday; payments_cycle2.csv has ACC-001 paying in full on 2026-11-09 (Monday). Your engine must state whether 1 day of interest accrues (brief is silent on holiday roll — document your choice).
- **EC8: Third decline of the day where the third is on a BLOCKED card** — CARD-016 on 2026-10-12: TXN-0132 (line 133) 09:00:00 PETROL on a DIESEL_TRUCK (DECLINED product), TXN-0133 (line 134) 10:00:00 CNG (DECLINED product), then commands.txt BLOCK_CARD at 11:00:00, then TXN-0135 (line 136) 12:00:00 DIESEL (third decline, reason BLOCKED), TXN-0137 (line 138) 13:00:00 DIESEL (second attempt on a BLOCKED card → tamper alert). UNBLOCK_CARD on 2026-10-14.
- **Extra: auto-block after 3 declines** — CARD-030 (CNG_VAN) attempts DIESEL at 08:05, 11:05, 15:05 on 2026-10-18 → three product declines auto-BLOCK the card; TXN-0203 (line 205) at 17:05:00 must be DECLINED with reason BLOCKED (no UNBLOCK command follows, so card stays BLOCKED for the rest of the month)
- **Extra: geo-fence with and without TRIP** — ACC-004 allows Karnataka only. TXN-0149 (line 150) CARD-026 at OUT-002 (Chennai, Tamil Nadu) 2026-10-14 11:20:00 → DECLINED GEO_FENCE; TXN-0162 (line 163) CARD-025 at OUT-002 2026-10-15 12:40:00 → APPROVED because commands.txt opens TRIP|CARD-025|Tamil Nadu|2026-10-15 00:00:00|2026-10-16 23:59:59
- **Extra: pump amount variance boundary** — TXN-0099 (line 100) CARD-021 2026-10-09 14:10:00 pump_amount is exactly Rs 1.00 above litres x price (not more than Rs 1 → no variance flag); TXN-0101 (line 102) CARD-022 2026-10-09 15:10:00 pump_amount is Rs 3.50 below → APPROVED at engine amount, outlet flagged for variance
- **Extra: implied km/L outside 2–25 (APPROVED_FLAGGED, misuse report)** — 13 transactions on 2026-10-19 … 2026-10-27 at 16:45:00 on cards CARD-001, CARD-002, CARD-006, CARD-007, CARD-008, CARD-011, CARD-014, CARD-019, CARD-020, CARD-027, CARD-028, CARD-033, CARD-034 have km-since-last-fill / litres of 1.5 or 1.33 (below 2) or 27–30 (above 25); they must be APPROVED_FLAGGED and appear on the misuse report, together with the two variance rows above = 15 flagged
- **Extra: LUBE monthly cap Rs 2,000** — TXN-0043 (line 44) CARD-010 2026-10-04 11:00:00 LUBE 4 L (Rs 1,400) APPROVED; TXN-0226 (line 228) CARD-010 2026-10-20 11:00:00 LUBE 3 L (Rs 1,050) would take the month to Rs 2,450 → DECLINED LUBE_CAP. LUBE attracts 18% GST on the invoice.
- **Extra: per-vehicle monthly litre cap** — CARD-029 (ACC-004 CNG_VAN, tank 90, per_vehicle_monthly_cap_litres=1200) is filled with 70 every other day at 07:40:00 in addition to its regular fills; the first transaction that would take October litres above 1200 must be DECLINED MONTHLY_CAP, and later ones too
- **Extra: transaction on a LOST card** — commands.txt REPORT_LOST|2026-10-20 10:00:00|CARD-035; TXN-0250 (line 252) CARD-035 at 2026-10-22 09:30:00 must be DECLINED CARD_LOST; UNBLOCK_CARD|2026-10-25 09:00:00|CARD-035 must be rejected (LOST is terminal)
- **Extra: invalid product code** — TXN-0264 (line 266) CARD-002 2026-10-23 12:12:00 has product KEROSENE which is not a valid product → reject the line with INVALID_PRODUCT and do not count it as a decline for blocking
- **Extra: duplicate txn_id** — TXN-0180 (line 182) is an exact duplicate of the previous line (same txn_id) → process once, report DUPLICATE for the second
- **Extra: posted_at earlier than timestamp** — TXN-0232 (line 234) has posted_at 45 minutes BEFORE its pump timestamp → reject as INVALID_POSTING (clock skew), do not authorise
- **Extra: reversal at exactly 24 h 00 m 01 s** — TXN-0219 (line 221) is a REVERSAL of TXN-0204 posted 24 h 00 m 01 s after the sale → late reversal: credit on next invoice, monthly litre cap NOT restored; the other 9 reversals are TXN-0237, TXN-0220, TXN-0051, TXN-0295, TXN-0171, TXN-0259, TXN-0165, TXN-0041, TXN-0056 (6 within 24 h, 3 after)

## What is NOT in this pack

Expected outputs are not provided for this set. Validate your engine with your own test suite against the rules in your brief (BR-01 … BR-nn). A separate hidden input set with the same schema will be used at final evaluation; outputs are compared field-for-field, so follow the column formats above exactly in your own output.

## Output format expected at evaluation

Produce these files (CSV, header row, same conventions as above, rows in the order stated). Numeric columns must be plain decimals with exactly the decimals stated.

### `decision_log.csv`

One row per transactions.csv row in file order (including rejected/duplicate rows).

Columns in order: `txn_id`, `card_id`, `account_id`, `timestamp`, `product`, `litres`, `engine_amount`, `decision`, `reason_code`, `variance_flag`, `offline_late`

### `invoices.csv`

One row per account per RUN_INVOICE, ordered by invoice_date then account_id. Money to 2 decimals.

Columns in order: `invoice_id`, `account_id`, `invoice_date`, `period_from`, `period_to`, `tier`, `fuel_amount`, `lube_amount`, `lube_gst`, `discount_credit`, `reversal_credit`, `prior_balance`, `interest`, `convenience_fee`, `total_due`, `due_date`

### `invoice_lines.csv`

Fuel, LUBE, credit-note and reversal lines per invoice in transaction order.

Columns in order: `invoice_id`, `line_no`, `line_type`, `txn_id`, `txn_date`, `product`, `litres`, `rate`, `amount`

### `statements.csv`

Ledger per account in date order (one block per PRINT_STATEMENT).

Columns in order: `account_id`, `entry_date`, `entry_type`, `reference`, `debit`, `credit`, `balance`

### `misuse_report.csv`

APPROVED_FLAGGED transactions, variance outlets, tamper alerts and offline risk-ledger postings in timestamp order.

Columns in order: `txn_id`, `card_id`, `account_id`, `outlet_id`, `timestamp`, `flag_type`, `detail`
