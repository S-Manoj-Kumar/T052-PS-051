# Final Submission Checklist

**Project:** Fuel Station Fleet Card Billing Engine  
**Problem Statement:** PS-051  
**Team ID:** T052  
**Target repository name:** `T052-PS-051`

Use this checklist against the exact project folder and Git repository that will be submitted. Tick an item only after checking it yourself.

---

## 1. Required project files

- [ ] `README.md` is in the repository root.
- [ ] Final design document is included as **PDF** in the repository.
- [ ] `DECISION_LOG.md` is in the repository root.
- [ ] `COMPLIANCE_AUDIT.md` is in the repository root.
- [ ] `DEMO_SCRIPT.md` is in the repository root (supporting document).
- [ ] `pom.xml` is in the repository root.
- [ ] All application source files under `src/main/java/` are included.
- [ ] All automated tests under `src/test/java/` are included.
- [ ] Required sample/input data is included, if permitted by the submission instructions.
- [ ] The 5-minute demo video is uploaded and its link is included in the README or submission notes.

**Important:** The project brief specifies the design document in PDF format. Keep the editable Word copy for future changes, but export the final reviewed version to PDF before submission.

## 2. Final code and build verification

- [ ] Confirm `pom.xml` has `<maven.compiler.release>17</maven.compiler.release>`.
- [ ] Confirm the required Java source files and tests are present.
- [ ] Run `java -version`.
- [ ] Run `mvn clean test` from the repository root.
- [ ] Confirm Maven reports `BUILD SUCCESS`.
- [ ] Record the actual test count, failures, errors and skipped tests.
- [ ] Run the complete sample command file from a clean state.
- [ ] Confirm the sample command file completes all commands.
- [ ] Check that the five required CSV reports are generated.
- [ ] Check output headers and column order against the sample pack.
- [ ] Inspect representative approved, declined, duplicate, reversal, offline and flagged transactions.
- [ ] Check invoice totals, payment allocation and account statement balances.

Sample execution command:

```powershell
mvn exec:java "-Dexec.args=data/sample data/sample/commands.txt"
```

## 3. Required generated reports

Check that the application generates the following files in the expected data/output directory:

- [ ] `decision_log.csv`
- [ ] `invoices.csv`
- [ ] `invoice_lines.csv`
- [ ] `statements.csv`
- [ ] `misuse_report.csv`

Do not submit stale output files from an earlier run as if they came from the final source version. Regenerate them after the final code changes.

## 4. Documentation accuracy

- [ ] README commands match the actual application entry point and argument format.
- [ ] README test count matches the latest final test run.
- [ ] Design document diagrams match the implemented architecture and processing flow.
- [ ] Design document is exported to PDF and opens correctly.
- [ ] Decision log describes actual implementation choices.
- [ ] Compliance audit distinguishes tested requirements from partially verified or ambiguous cases.
- [ ] Demo script matches the final code and report names.
- [ ] Team member names and team ID are correct in every document.
- [ ] No placeholder text, unfinished sections or incorrect dates remain.

## 5. Git repository hygiene

- [ ] Repository is named `T052-PS-051`.
- [ ] All required files are committed.
- [ ] Commit history shows the team's actual development work.
- [ ] No generated `target/` build directory is committed unless explicitly required.
- [ ] No IDE-specific temporary files, local caches or unrelated files are committed.
- [ ] No passwords, API keys, tokens or private credentials are present.
- [ ] `.gitignore` excludes build output and local-only files.
- [ ] Repository can be cloned and built independently.
- [ ] The repository visibility/access setting allows evaluators to open it.

Useful final Git commands:

```powershell
git status
git add .
git commit -m "Finalize PS-051 fuel billing engine submission"
git push
```

Only run the commit and push commands after reviewing the changes shown by `git status`.

## 6. Demo video

- [ ] Video is approximately five minutes.
- [ ] Project title, PS-051 and Team T052 are introduced.
- [ ] Project structure is shown.
- [ ] The test command and actual successful result are shown.
- [ ] The sample command file is executed.
- [ ] Decision log is demonstrated.
- [ ] Invoices and invoice lines are demonstrated.
- [ ] Account statement is demonstrated.
- [ ] Misuse report is demonstrated.
- [ ] Video link opens without requesting unauthorised access.
- [ ] Video link is included with the final submission.

## 7. Submission email

The project instructions specify that the final submission should be sent by replying to the existing email thread.

- [ ] Confirm the final Git repository URL.
- [ ] Confirm the repository contains the final code, tests and required documents.
- [ ] Include the design document PDF in the repository.
- [ ] Include the demo video link.
- [ ] Verify the recipient and any required CC recipients from the original email.
- [ ] Use the subject: **`T052 – Final Submission`**.
- [ ] Send the email by replying to the original project email thread.

The project instructions identify `support@10seconds.co.in` for sharing the repository link at the Week 2 checkpoint. Verify the final submission recipient and process against the latest official email before sending; do not assume the checkpoint address is necessarily the only final-submission recipient.

## 8. Known points to review before submission

- [ ] Recheck the sample README's mileage boundary descriptions against the actual transaction CSV values.
- [ ] Reconcile the sample README's expected misuse-report count with the generated report and its tamper-alert entries.
- [ ] Confirm the LUBE cap case returns the expected reason code in the final output.
- [ ] Confirm the Sunday invoice due-date interpretation is documented consistently.
- [ ] Confirm invalid reversal references do not generate credits.
- [ ] Confirm duplicate transaction rows are represented correctly in the decision log.
- [ ] If any ambiguity remains, retain it in the compliance audit and raise it with the mentor rather than hardcoding sample transaction IDs.

---

## Final sign-off

| Check | Name / confirmation |
|---|---|
| Code and tests reviewed | ______________________________ |
| Sample run reviewed | ______________________________ |
| Documentation reviewed | ______________________________ |
| Repository reviewed | ______________________________ |
| Demo video checked | ______________________________ |
| Final submission sent | ______________________________ |

**Final reminder:** A successful local test run is strong evidence, but it is not a guarantee that every hidden evaluation case will pass. Submit the exact version that was built, tested, documented and demonstrated.
