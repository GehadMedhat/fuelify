## How to plug the notification module into your existing project

### 1. Register tables in DatabaseFactory.kt

Add `NotificationSettings` and `Notifications` to your existing `SchemaUtils.createMissingTablesAndColumns` call:

```kotlin
// DatabaseFactory.kt — inside transaction { ... }
SchemaUtils.createMissingTablesAndColumns(
    Users, VerificationTokens, TokenBlacklist, LoginAttempts,
    NotificationSettings, Notifications   // ← add these two
)
```

---

### 2. Wire service + scheduler in Application.module()

```kotlin
// In your Application.kt / module()

val notificationService = NotificationService()
val notificationScheduler = NotificationScheduler(notificationService)

// Register routes (alongside your existing authRoutes(...))
notificationRoutes(notificationService)

// Start/stop scheduler with the app lifecycle
notificationScheduler.start()
environment.monitor.subscribe(ApplicationStopped) {
    notificationScheduler.stop()
}
```

---

### 3. Check your JWT authenticate block name

In `NotificationRoutes.kt`, the auth block is `authenticate("jwt-auth")`.
Make sure this matches whatever name you used in your existing routes. For example:

```kotlin
// If your existing routes use:
authenticate("auth-jwt") { ... }

// Then change NotificationRoutes.kt to:
authenticate("auth-jwt") { ... }
```

---

### 4. No new dependencies needed

Your existing `build.gradle.kts` already has everything:
- ✅ Ktor server + auth + JWT
- ✅ Exposed ORM + HikariCP
- ✅ PostgreSQL driver
- ✅ kotlinx-coroutines (for the scheduler)
- ✅ kotlinx-serialization

---

### 5. API summary

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/notifications/settings` | Fetch user's notification settings |
| PUT | `/api/notifications/settings` | Update toggles, DND, timing |
| GET | `/api/notifications` | Notification center (supports `?limit=&offset=`) |
| PUT | `/api/notifications/read-all` | Mark all notifications as read |
| PUT | `/api/notifications/{id}/read` | Mark one notification as read |

---

### Sample PUT /api/notifications/settings body

```json
{
  "hydrationEnabled": true,
  "stepsEnabled": true,
  "sleepEnabled": true,
  "workoutEnabled": true,
  "workoutTiming": "EVERY_HOUR",
  "workoutFreqStart": "08:00",
  "workoutFreqEnd": "20:00",
  "dndEnabled": true,
  "dndStartTime": "22:00",
  "dndEndTime": "07:00"
}
```

Valid `workoutTiming` values: `EVERY_30_MIN`, `EVERY_HOUR`, `EVERY_2_HOURS`, `EVERY_3_HOURS`
