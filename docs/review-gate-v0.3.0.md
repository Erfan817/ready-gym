# v0.3.0 independent review gate

Status: **failed — fixes in progress; not a final delivery**.

Independent read-only reviews returned through delegation `deleg_f4507fb3`. The backend review re-ran 10 existing tests successfully but reproduced uncovered failures with clearly labelled httpx MockTransport; Android review used actual loopback probes for deletion issues and static tracing for stale-safety re-entry. Neither review used a real provider key or claimed device execution.

## Required fixes

- Backend safety denial must reject nested negation, quotation, withdrawal and uncertainty, not match an arbitrary substring. Ambiguous safety statements cannot produce or preserve confirmable symptom-free status.
- Highest acute risk found either in original input or a validated provider patch must latch for the session; subsequent lower-risk messages must not downgrade it.
- A user's explicit correction, including an unsupported value, must make the corresponding prior value unconfirmable until clarification. Ignoring the model's clarifying prose must not allow confirmation of a knowingly contradicted older profile.
- Huge finite JSON integers for height/weight must not raise uncaught OverflowError; reject with the sanitised provider-invalid-response contract.
- Android local risk must survive returning to the retained coach summary, re-entry, offline navigation and configuration recreation; old symptom-free snapshots must not override newer risk.
- Deletion recovery must allow retry after an already completed DELETE and a lost response/failed readback, but still GET the exact target and require actual HTTP404, not merely an error body code.
- `session_unauthorized` must offer fresh-consent session recovery while retaining unsent input.

## Delivery gate

Fixers own separate backend and Android files. Each behavior must have an executed runtime RED→GREEN regression. Parent runs the unfiltered suite, builds and verifies the new APK/ZIP, and requests independent re-review. Only the reviewed revision is committed/pushed to the existing private `Erfan817/ready-gym`; remote HEAD and target content are read back. Credentials, private health input, build caches and signing keys are excluded.

Model integration remains unverified without the dedicated Ready Gym DeepSeek key and a limited real-provider request. HTTPS/access-control deployment and real-device acceptance are separate pending gates, not masked by fixture tests.
