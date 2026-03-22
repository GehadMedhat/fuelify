# 🥦 Fuelify — Android App + Ktor Backend

## Project Structure

```
fuelify_project/
├── android/          ← Android app (Kotlin + Fragments)
│   └── app/src/main/
│       ├── java/com/example/fuelify/
│       │   ├── MainActivity.kt
│       │   ├── onboarding/
│       │   │   ├── OnboardingActivity.kt
│       │   │   ├── OnboardingViewModel.kt
│       │   │   └── fragments/  (15 step fragments)
│       │   └── data/
│       │       ├── api/        (Retrofit, FuelifyApi)
│       │       └── models/     (UserOnboardingData)
│       └── res/
│           ├── layout/         (15 fragment XMLs + activity)
│           ├── drawable/       (all bg_* drawables)
│           └── values/         (colors, strings, themes)
└── backend/          ← Ktor server
    └── src/main/kotlin/com/example/fuelify/
        ├── Application.kt
        ├── plugins/Plugins.kt
        ├── routes/UserRoutes.kt
        ├── db/DatabaseFactory.kt
        └── models/Users.kt
```

---

## Android Setup

### Prerequisites
- Android Studio Hedgehog (2023.1) or newer
- Min SDK 24, Target SDK 34

### Import Project
1. Open Android Studio → **Open** → select `fuelify_project/android/`
2. Let Gradle sync complete
3. Change the `BASE_URL` in `RetrofitClient.kt` if needed:
   - Emulator: `http://10.0.2.2:8080/` ✅ (already set)
   - Real device: `http://YOUR_PC_LOCAL_IP:8080/`

### Run
- Select an emulator or device → ▶ Run

---

## Backend Setup

### Prerequisites
- JDK 17+
- Gradle 8+

### Run Locally
```bash
cd fuelify_project/backend
./gradlew run
```
The server starts on **port 8080**.

### Environment Variables (optional overrides)
| Variable | Default |
|---|---|
| `PORT` | `8080` |
| `DATABASE_URL` | Neon.tech JDBC URL (already in application.conf) |
| `DB_USER` | `fuelify_app` |
| `DB_PASSWORD` | `AppStrongPass2024!` |

### API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/health` | Health check |
| POST | `/api/users/register` | Submit onboarding data |
| GET | `/api/users/{id}` | Get user by ID |

#### POST /api/users/register — Request body
```json
{
  "name": "Ali",
  "gender": "Male",
  "age": 25,
  "height_cm": 178,
  "weight_kg": 75,
  "goal": "build muscle",
  "activity_level": "moderately active",
  "motivation": "strength & endurance",
  "fitness_level": "Intermediate",
  "exercise_days": 4,
  "training_place": "GYM",
  "meals_per_day": 4,
  "liked_foods": ["Chicken", "Rice", "Eggs"],
  "allergies": ["Dairy"],
  "budget": "standard"
}
```

---

## Onboarding Flow

---

## Database (Neon.tech PostgreSQL)

The `users` table is **auto-created** by Exposed on first backend start.

Connection string is in `backend/src/main/resources/application.conf`.
Change credentials if you rotate the Neon password.

---

## Customisation Tips

- **Add auth** — add email/password fields on step 1 or a separate AuthActivity before onboarding.
- **Change backend URL** — edit `RetrofitClient.BASE_URL` in the Android project.
- **Add more screens** — extend `steps` list in `OnboardingActivity` and create a new Fragment.
- **Progress dots** — currently shows 15 dots. The active dot is wider (32dp) and black.
