# 🥦 Fuelify — Android App + Ktor Backend
---

## 📱 Features

### 🏠 Home Dashboard
- Personalized daily calorie goal with real-time progress
- Today's meal highlights with images (tap → full meal detail)
- Recommended meals based on user profile, allergies, and goal
- Water intake and workout session tracking
- Day streak tracker

### 🥗 Diet Screen
- Dynamic macro tracking (Protein / Carbs / Fat) with progress bars
- Today's meals with one-tap check-off calorie logging
- Meal scheduling grouped by Morning / Noon / Night with alarm button
- Quick access grid: Meal Delivery, Cloud Kitchen, Groceries, Meal Scan

### 🎯 Onboarding (15 Steps)
- Custom drum-roll pickers for age, height, weight, workout days, meals/day
- Allergy and food preference selection
- BMR/TDEE/macro calculation on registration

---

## 🏗 Project Structure

```
fuelify_project/
├── android/                          ← Android app (Kotlin)
│   └── app/src/main/
│       ├── java/com/example/fuelify/
│       │   ├── MainActivity.kt
│       │   ├── onboarding/
│       │   │   ├── OnboardingActivity.kt
│       │   │   ├── OnboardingViewModel.kt
│       │   │   └── fragments/         (15 step fragments)
│       │   ├── home/
│       │   │   ├── HomeActivity.kt
│       │   │   ├── HomeViewModel.kt
│       │   │   ├── DietActivity.kt
│       │   │   ├── MealDetailActivity.kt
│       │   │   ├── MealSchedulingActivity.kt
│       │   │   ├── MealsListActivity.kt
│       │   │   ├── RecipesActivity.kt
│       │   │   ├── MealDeliveryActivity.kt
│       │   │   ├── CloudKitchenActivity.kt
│       │   │   └── CloudKitchenStatusActivity.kt
│       │   ├── data/
│       │   │   ├── api/               (Retrofit, FuelifyApi, RetrofitClient)
│       │   │   └── models/            (ApiModels)
│       │   └── utils/
│       │       └── UserPreferences.kt
│       └── res/
│           ├── layout/                (all activity + item XMLs)
│           ├── drawable/              (all bg_* drawables)
│           └── values/
└── backend/                          ← Ktor server
    └── src/main/kotlin/com/example/fuelify/
        ├── Application.kt
        ├── plugins/Plugins.kt
        ├── routes/
        │   ├── UserRoutes.kt
        │   ├── DashboardRoutes.kt
        │   ├── MealDetailRoutes.kt
        │   ├── MealSearchRoutes.kt
        │   └── KitchenOrderRoutes.kt
        ├── models/
        │   ├── Users.kt
        │   ├── Tables.kt
        │   └── NutritionEngine.kt
        └── db/DatabaseFactory.kt
```

---

## ⚙️ Android Setup

### Prerequisites
- Android Studio Hedgehog (2023.1) or newer
- Min SDK 24, Target SDK 34

### Import Project
1. Open Android Studio → **Open** → select `fuelify_project/android/`
2. Let Gradle sync complete
3. Set your IP in `RetrofitClient.kt`:
   - Emulator: `http://10.0.2.2:8080/`
   - Real device: `http://YOUR_PC_LOCAL_IP:8080/`

### Key Dependencies (`build.gradle`)
```gradle
implementation 'com.squareup.retrofit2:retrofit:2.9.0'
implementation 'com.squareup.retrofit2:converter-gson:2.9.0'
implementation 'com.github.bumptech.glide:glide:4.16.0'
implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0'
```

---

## ⚙️ Backend Setup

### Prerequisites
- JDK 17+
- Gradle 8+

### Run Locally
```bash
cd fuelify_project/backend
gradle run
# Server starts on port 8080
```

### Environment Variables
| Variable | Default |
|----------|---------|
| `PORT` | `8080` |
| `DATABASE_URL` | Neon.tech JDBC URL (in application.conf) |
| `DB_USER` | `team_dev` |
| `DB_PASSWORD` | set in application.conf |

---

## 🔌 API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/health` | Health check |
| POST | `/api/users/register` | Submit onboarding data |
| GET | `/api/users/{id}/dashboard` | Full dashboard: meals, macros, streak, water |
| POST | `/api/users/{id}/log-water` | Log water glasses |
| POST | `/api/users/{id}/log-meal` | Mark meal as eaten |
| GET | `/api/meals/{id}/details` | Ingredients, instructions, video URL |
| GET | `/api/users/{id}/search-meals?q=` | Search meals with suitability scoring |
| POST | `/api/users/{id}/switch-meal` | Swap a meal in today's plan |
| POST | `/api/users/{id}/kitchen-order` | Place cloud kitchen order |

---

## 🗄 Database (Neon.tech PostgreSQL)

Tables are auto-created by Exposed ORM on first backend start.

| Table | Description |
|-------|-------------|
| `users` | User profile — 15 onboarding fields |
| `meal` | 39 meals with image URLs, macros, diet type |
| `recipe` | Instructions + YouTube search URLs for all 39 meals |
| `meal_ingredient` | 150 ingredient-meal links |
| `ingredient` | 20 base ingredients with macros |
| `meal_plans` | AI-generated daily meal schedules with scaled calories |
| `daily_logs` | Calories eaten, water, workouts, streak per user per day |
| `allergy_type` | Allergy categories |
| `user_allergy_type` | User ↔ allergy mapping |
| `kitchen_order` | Cloud kitchen orders (plan, portion, spice, status, price) |

---

## 🧠 Nutrition Engine

Personalized nutrition calculated at registration and on every dashboard load:

| Calculation | Method |
|-------------|--------|
| BMR | Mifflin-St Jeor equation |
| TDEE | BMR × activity level multiplier |
| Daily calories | TDEE adjusted ±500 kcal for goal |
| Macros | Goal-specific protein/carbs/fat split |
| Meal scoring | Diet type match + allergy safety + calorie proximity + eco score |
| Calorie scaling | Meal portions scaled to match user's daily goal per slot |

---

## 🎯 Onboarding Flow

| Step | Screen | Data saved |
|------|--------|------------|
| 1 | Name | `name` |
| 2 | Gender | `gender` |
| 3 | Age | `age` |
| 4 | Height | `height_cm` |
| 5 | Weight | `weight_kg` |
| 6 | Goal | `goal` |
| 7 | Activity Level | `activity_level` |
| 8 | Motivation | `motivation` |
| 9 | Push-ups | `fitness_level` |
| 10 | Workout Days | `exercise_days` |
| 11 | Training Place | `training_place` |
| 12 | Meals per day | `meals_per_day` |
| 13 | Liked Foods | `liked_foods[]` |
| 14 | Allergies | `allergies[]` |
| 15 | Budget | `budget` → submit to backend |

---

## 📋 Pending Features

- [ ] Workouts tab
- [ ] Statistics tab
- [ ] Profile tab
- [ ] Push notifications for meal reminders
- [ ] Backend cloud hosting

---

## 💡 Customisation Tips

- **Change backend URL** — edit `RetrofitClient.BASE_URL`
- **Add more meals** — insert into `meal`, `recipe`, and `meal_ingredient` tables in Neon
- **Add auth** — add email/password fields or a separate `AuthActivity` before onboarding
- **Meal delivery search** — requires Google Custom Search API key enabled in Google Console (`cx=b71de9a88248243f6`)

---

## 👥 Team

Built as a university project — Alexandria University, 2026.

---

## 📄 License

This project is for educational purposes only.
