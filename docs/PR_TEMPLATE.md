## Summary

Briefly explain what this PR changes.

Example:
This PR adds an idempotency key to the reward redemption call and a debounce on the redeem button.

## Why

Explain the reason behind the change.

Example:
Rapid double-taps on the redeem button could send two redemption requests before the first response arrived.

## Changes Made

* List each meaningful change as its own bullet
* Include refactors, new files, and removed code
* Mention updated documentation and tests

## Screenshots / Demo

Add screenshots, videos, or before/after images if the PR changes the UI.

## How to Test

1. Step-by-step instructions a reviewer can follow on an emulator or device.
2. Include any required test account or backend environment.
3. State the expected result at each step.

## Test Results

* Unit tests (`.\gradlew.bat test`): Passed / Failed / Not run (reason)
* Build (`.\gradlew.bat assembleDebug`): Passed / Failed / Not run (reason)
* Manual test on device/emulator: Passed / Failed / Not run (reason)

## Performance / Security Notes

State any performance impact (query limits, threading, listener lifecycle) and any security-relevant change (points flow, idempotency, Firestore rules, token handling). Write "None" if not applicable.

## Notes for Reviewers

Mention anything important reviewers should focus on: risky areas, trade-offs, or follow-up work.

## Related Issues / Tasks

Closes #123
Related to #120
