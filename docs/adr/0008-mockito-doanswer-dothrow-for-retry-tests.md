# ADR-0008: Mockito doAnswer/doThrow pattern for retry tests

**Date**: 2024-01-01
**Status**: Accepted

## Context

When testing retryable flows (such as rail call timeouts or transient database failures), unit tests frequently configure mocks with sequential behaviors (e.g. throw an exception on attempt 1, return a response on attempt 2) or re-stub an existing mock during test execution. When using Mockito's `when(...).thenAnswer(...)` or `when(...).thenThrow(...)` syntax, Mockito actually invokes the mock's currently registered answer during the stubbing setup call. Because argument matchers (such as `any()`) evaluate to placeholder values (`null`), any existing answer that dereferences arguments encounters a `NullPointerException` during the `when(...)` setup step itself.

## Decision

Use the **`doAnswer(...).when(mock).method(...)` and `doThrow(...).when(mock).method(...)` pattern** instead of `when(...).thenAnswer/thenThrow` when stubbing or re-stubbing methods in retry and sequential mock test scenarios.

## Consequences

### Positive

- Avoids `NullPointerException` during stub setup because `doAnswer` / `doThrow` puts the mock into stubbing mode before invoking the method, preventing execution of previous answers.
- Reliable sequential and conditional stubbing for complex failure-recovery and retry test cases.
- Consistent stubbing idiom across all tests involving mocked callbacks or retry logic.

### Negative

- Less readable or less intuitive than the standard `when(...).thenReturn(...)` syntax for engineers unfamiliar with Mockito stubbing internals.

### Neutral

- Standard `when(...).thenReturn(...)` remains acceptable for static, single-value stubs without argument-dependent answers.
