# ADR-0018: Use NUMERIC(19,4) for monetary values and TIMESTAMPTZ for timestamps

**Date**: 2026-11-22
**Status**: Accepted

## Context

Financial and payment systems require exact arithmetic without binary floating-point rounding errors. Additionally, distributed services and databases running across different environments require unambiguous timestamp storage that is independent of server or session time zones.

## Decision

Standardize monetary and temporal column types across the PostgreSQL schema:

- **Monetary values**: Use `NUMERIC(19,4)` (`19` digits total: `15` integer digits + `4` fractional digits) for all monetary amounts (payment amounts, account balances, holds, and ledger entries). This is a well-established best practice for financial systems, providing exact base-10 arithmetic, safe precision, and sufficient range.
- **Timestamps**: Use `TIMESTAMPTZ` (`TIMESTAMP WITH TIME ZONE`) for all timestamp columns so values are stored and compared unambiguously in UTC.

## Consequences

### Positive

- Eliminates binary floating-point rounding drift in balance, hold, and ledger calculations.
- Supports up to 4 fractional digits (sub-cent precision) and large monetary values up to 15 integer digits.
- Prevents timezone-related bugs across application instances, database sessions, and regions.

### Negative

- `NUMERIC` storage and arithmetic carry slightly higher CPU and storage overhead than native binary floating-point types and require `BigDecimal` mapping in Java.

### Neutral

- All Flyway migrations and persistence/domain models must consistently use `NUMERIC(19,4)` and `TIMESTAMPTZ`.
