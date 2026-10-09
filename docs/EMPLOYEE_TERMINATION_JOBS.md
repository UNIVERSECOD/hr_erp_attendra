# Employee termination device cleanup

Termination persists the employee's `TERMINATED` status and one removal job per assigned
device in a single database transaction. History and photos are retained. New migrations
only add the job table; previously terminated employees are not silently enrolled in
device deletion. Use the existing retry action once to enqueue cleanup for those employees.

After commit, the request attempts outstanding jobs. A scheduled worker checks due jobs
every minute, processing at most 25 jobs per tenant per pass. Failed jobs remain pending
without a retry limit. The backend must be running for retries to execute.
Removal retries use a dedicated scheduler thread so offline devices do not delay
attendance synchronization or other scheduled maintenance.

Each job snapshots the terminal employee number, bridge ID, IP and device name. Before
deletion the worker checks tenant ownership, termination status, unchanged identities,
the bridge's device record and online status. Identity changes require administrator
review; the worker does not redirect old jobs to a different terminal. Upstream failures
are stored as safe summaries. The dedicated bridge client has a 5-second connection
timeout and 20-second response timeout.

Database row locks serialize job attempts across manual retries and worker instances.
Completed jobs are skipped. If the process stops after a device deletion but before the
completion commit, the existing bridge delete-by-employee-number contract accepts an
already missing user on retry. No bridge user-row ID is used as the terminal identity.

`failedDevices` in the existing termination response remains backward compatible and
represents outstanding jobs. Employee responses expose `pendingDeviceRemovals` for the
persistent warning in the terminated list, which refreshes every 15 seconds while open.
An offline terminal may still allow access until cleanup succeeds.
