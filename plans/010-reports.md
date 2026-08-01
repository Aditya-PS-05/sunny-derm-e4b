# 010 — Add report generation and document-loading continuity

- **Status**: DONE
- **Commit**: 409b801
- **Severity**: MEDIUM
- **Category**: Feedback and empty/loading graphics
- **Estimated scope**: 3 files, ~230 lines

## Problem

Reports empty state is text-only (`ReportsScreen.kt:40-55`), Report Detail begins
blank while pages render (`ReportDetailScreen.kt:47-52,80-94`), and report
generation starts asynchronously without a durable generating state
(`GenerateReportScreen.kt:217-300`).

## Target

- Reports: code-native PDF/document empty illustration and restrained page-stack visual for rows; no repeated list entrances.
- Report Detail: document skeleton, explicit error/empty state, page count, and page opacity reveal over 180ms. Do not move rendered pages.
- Generate: live compact report-cover preview tied to selected scan/photo counts;
  visible generating→saved state; prevent duplicate generation.
- Reduced motion uses identical loading information without skeleton shimmer or movement.

## Boundaries

- Do not write plaintext report files or change secure sharing/storage.
- Do not animate sensitive report contents or add clinical scores.

## Verification

- **Mechanical**: compile/tests/lint, report Android tests compile.
- **Feel check**: no reports, generate success/failure, slow page rendering, share/delete.
- **Done when**: no report operation presents a blank or apparently frozen screen.
