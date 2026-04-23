# run-logs fixture index

These logs are inputs for validating the `failure-analyst` subagent. Each file represents a distinct failure category.

## mechanical-missing-import.txt

**Illustrates:** A compilation failure caused by a missing import (`Unresolved reference: Truth`).

**Expected analyst classification:** `mechanical` — the fix is mechanical: add the missing import `import com.google.common.truth.Truth.assertThat`. No production code change is needed; the test itself is not wrong.

## substantive-assertion-mismatch.txt

**Illustrates:** A test that compiles and runs but fails at assertion time — the actual error message returned by the code (`"unknown"`) does not match the AC-2-required message (`"Wrong email or password"`).

**Expected analyst classification:** `substantive` — the code surfaces a generic error string instead of the AC-2-required message. The analyst should flag this as "code likely not matching AC-2; human review required." A mechanical fix (editing the test assertion) would mask a real product defect.
