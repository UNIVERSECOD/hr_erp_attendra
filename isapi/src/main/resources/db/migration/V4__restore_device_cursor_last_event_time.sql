UPDATE device_cursors AS cursor
SET last_event_time = latest.latest_event_time
FROM (
    SELECT device_id, MAX(event_time) AS latest_event_time
    FROM acs_raw_events
    WHERE event_time IS NOT NULL
    GROUP BY device_id
) AS latest
WHERE cursor.device_id = latest.device_id
  AND cursor.last_event_time IS NULL;
