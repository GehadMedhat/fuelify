<div align="center">

```
███████╗██╗   ██╗███████╗██╗     ██╗███████╗██╗   ██╗
██╔════╝██║   ██║██╔════╝██║     ██║██╔════╝╚██╗ ██╔╝
█████╗  ██║   ██║█████╗  ██║     ██║█████╗   ╚████╔╝ 
██╔══╝  ██║   ██║██╔══╝  ██║     ██║██╔══╝    ╚██╔╝  
██║     ╚██████╔╝███████╗███████╗██║██║        ██║   
╚═╝      ╚═════╝ ╚══════╝╚══════╝╚═╝╚═╝        ╚═╝   
```

# 🥦 Fuelify — Your Complete Health & Nutrition OS

**The all-in-one Android health platform that thinks like a nutritionist, trains like a coach, and cares like a doctor.**

[![Kotlin](https://img.shields.io/badge/Android-Kotlin-7F52FF?style=for-the-badge&logo=kotlin)](https://kotlinlang.org)
[![Ktor](https://img.shields.io/badge/Backend-Ktor-087CFA?style=for-the-badge&logo=ktor)](https://ktor.io)
[![PostgreSQL](https://img.shields.io/badge/Database-PostgreSQL-4169E1?style=for-the-badge&logo=postgresql)](https://www.postgresql.org)
[![Neon](https://img.shields.io/badge/Hosted_on-Neon.tech-00E5CC?style=for-the-badge)](https://neon.tech)
[![JWT](https://img.shields.io/badge/Auth-JWT-000000?style=for-the-badge&logo=jsonwebtokens)](https://jwt.io)
[![Groq](https://img.shields.io/badge/AI-Groq_LLaMA_3.1-F06400?style=for-the-badge)](https://groq.com)

---

*Built at Alexandria National University, 2026 — a university project that grew into a full production-grade health platform.*

</div>

---

## 📖 Table of Contents

- [✨ What is Fuelify?](#-what-is-fuelify)
- [🏗 Architecture Overview](#-architecture-overview)
- [📱 Android App — Screen by Screen](#-android-app--screen-by-screen)
- [🔌 Backend API — Complete Endpoint Reference](#-backend-api--complete-endpoint-reference)
- [🧠 Intelligence Layer](#-intelligence-layer)
- [🗄 Database Schema](#-database-schema)
- [🔐 Auth API (ktor-auth-api)](#-auth-api-ktor-auth-api)
- [⚙️ Setup & Installation](#️-setup--installation)
- [🌍 Environment Variables](#-environment-variables)
- [📋 Project Structure](#-project-structure)
- [🚀 Feature Roadmap](#-feature-roadmap)

---

## ✨ What is Fuelify?

Fuelify is not just a calorie counter. It is a **complete personal health operating system** built as a native Android app backed by two independent Ktor microservices.

| Layer | Technology | Purpose |
|-------|-----------|---------|
| Android Client | Kotlin + Retrofit + Glide | Native UI, 30+ screens |
| Main Backend | Ktor + Exposed ORM | Health data, meals, workouts, AI features |
| Auth Backend | Ktor + BCrypt + JWT | Authentication, notifications, AI chat |
| Database | Neon.tech PostgreSQL | 25+ tables, auto-migrated |
| AI Chat | Groq LLaMA 3.1 8B | Health assistant "Aura" |
| File Storage | Supabase Storage | Profile pictures, body scan photos |
| Monitoring | Micrometer + Prometheus | Backend metrics |

In total, Fuelify delivers **10 major feature pillars**, each a standalone subsystem with its own data model, logic engine, and UI layer.

---

## 🏗 Architecture Overview

```
┌─────────────────────────────────────────────────────┐
│                   Android App                        │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐            │
│  │Onboarding│ │Dashboard │ │  Diet    │  ... 30+    │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘  screens   │
└───────┼─────────────┼─────────────┼─────────────────┘
        │   Retrofit / REST JSON    │
        ▼                           ▼
┌──────────────────┐     ┌──────────────────────────┐
│  ktor-auth-api   │     │   Main Fuelify Backend   │
│  Port: 8081      │     │   Port: 8080              │
│                  │     │                            │
│  • JWT Auth      │     │  • Dashboard & Meals      │
│  • Email OTP     │     │  • Workouts & Sessions    │
│  • Groq AI Chat  │     │  • Water / Sleep / Mood   │
│  • Notifications │     │  • Medical & Smart Plan   │
│  • Marketplace   │     │  • Eco Scoring            │
│  • Guest Mode    │     │  • Family Groups          │
│  • Supabase Upload│    │  • Doctor Consultations   │
└────────┬─────────┘     └──────────┬───────────────┘
         │                          │
         └──────────────┬───────────┘
                        ▼
              ┌─────────────────┐
              │  Neon.tech       │
              │  PostgreSQL      │
              │  (Shared DB)     │
              └─────────────────┘
```

Both backends share the same PostgreSQL instance. The auth backend owns the `users` table with full credential management; the main backend extends the same user rows with health profile data.

---

## 📱 Android App — Screen by Screen

### 🎯 Onboarding Flow (15 Steps)

The onboarding collects everything needed to personalize the entire experience — no account setup required. At the end, all data is submitted to the backend which calculates BMR, TDEE, macro targets, and generates the first day's meal plan in a single registration call.

| Step | Screen | Data Collected |
|------|--------|----------------|
| 1 | Name | `name` |
| 2 | Gender | `gender` |
| 3 | Age | `age` — drum-roll picker |
| 4 | Height | `height_cm` — drum-roll picker |
| 5 | Weight | `weight_kg` — drum-roll picker |
| 6 | Goal | `goal` — lose weight / gain muscle / get fit |
| 7 | Activity Level | `activity_level` — sedentary → athlete |
| 8 | Motivation | `motivation` — health, looks, sport, etc. |
| 9 | Fitness Level | `fitness_level` — inferred from push-up test |
| 10 | Workout Days | `exercise_days` — drum-roll picker |
| 11 | Training Place | `training_place` — gym / home / hybrid |
| 12 | Meals per Day | `meals_per_day` — 1 to 6 |
| 13 | Liked Foods | `liked_foods[]` — multi-select grid |
| 14 | Allergies | `allergies[]` — multi-select safety filter |
| 15 | Budget | `budget` — budget / standard / premium → submit |

---

### 🏠 Home Dashboard

The central hub of the app. Every data point is calculated fresh on each load.

**What you see:**
- Personalized greeting with the user's first name
- Daily calorie ring — goal vs eaten vs remaining, animated
- Real-time macro bars — Protein / Carbs / Fat with gram targets
- Today's meal highlights with images — tap to open full detail
- Recommended meals scored against the user's profile
- Water intake tracker — glasses logged today vs goal of 8
- Workout session tracker — done vs goal, with today's assigned workout card
- Day streak — consecutive days with any meal logged
- Weekly summary cards — meals eaten / total, workouts done / goal

**How the recommendation engine works:**
The dashboard fetches a pool of allergy-safe meals, scores each one across five dimensions (diet type match, meal time match, difficulty vs fitness level, calorie proximity to slot target, eco motivation), and serves the top results. Each meal slot gets a unique daily rotation seeded on `dayOfYear + userId`.

---

### 🥗 Diet Screen

A full macro-tracking and meal-scheduling hub.

- **Macro Progress Bars** — live protein / carbs / fat with animated fills and gram labels
- **Today's Meals** — grouped by Morning / Noon / Night, each with a one-tap check-off to log the calories instantly
- **Meal Scheduling** — each meal shows its scheduled time with an alarm bell button
- **Quick Access Grid** — four tiles linking to Meal Delivery, Cloud Kitchen, Groceries, and Meal Scan

---

### 🔍 Meal Detail

Full nutritional breakdown for any meal:

- Macro breakdown with fiber and sugar estimates
- Ingredient list with quantities (e.g. "500g chicken breast", "2 cups spinach")
- Step-by-step cooking instructions parsed from the recipe
- Prep time, difficulty badge, diet type badge
- Eco grade (A–D) derived from the meal's eco score
- Calorie quality score
- Video link button (if `has_video` is true)

---

### 💪 Workouts

A full personalized workout planning system.

**Category Browser** — all workout categories with emoji icons and workout counts, filtered by the user's training place and goal.

**Suggested Workouts** — derived from a full BMR/TDEE calculation:
1. Compute daily calorie burn target (TDEE × 20–30% depending on goal)
2. Estimate average calories per session by category (Running ≈ 350 kcal, Yoga ≈ 180 kcal, Gym ≈ 320 kcal)
3. Calculate `sessionsPerDay = ceil(burnTarget / avgCalPerSession)` capped at 3
4. Assign session labels: "Upper Body + Lower Body" for muscle gain, "Cardio + HIIT" for fat loss
5. Populate `workout_plan` for the entire week with correct day offsets (e.g. Mon/Wed/Fri for 3 exercise days)

**Weekly Plan View** — shows every planned session for Mon–Sun with date, status (planned / completed), session label, image, category, difficulty, duration, and estimated calories burned.

**Workout Detail** — full exercise list with reps, sets, rest seconds, muscle group, and image.

**Session Logging** — after completing a workout, log duration, calories burned, and exercises done. This updates `workout_plan` status to `completed` and increments the daily log.

**Recommended Workouts** — a secondary set of suggestions that rotates daily and excludes workouts already in the weekly plan.

---

### 🌊 Water Tracker

A dedicated hydration management subsystem.

- **Home Summary** — today's total, daily goal, progress percentage, weekly total, monthly total, 7-day goal completion %, 7-day daily average in ml and litres
- **Intake Log** — timestamped log of every glass or custom amount added today, with delete per entry
- **Statistics** — four views:
  - **Daily** — hourly breakdown chart of when you drank
  - **Weekly** — per-day bar chart for the last 7 days, goal completion %, daily average
  - **Monthly** — per-day data for the current month
  - **Quarterly** — 3-month comparison; also supports custom month selection via POST
- **Reminders** — add, edit, toggle, and delete water reminder alarms with per-minute precision
- **Auto Reminder** — global toggle to enable automatic hydration reminders

---

### 😴 Sleep Scheduler

Per-day sleep scheduling with bedtime and alarm control.

- Set bedtime hour/minute per day of week (Mon=1 through Sun=7)
- Set hours + minutes of sleep → auto-calculates wake time
- Toggle bedtime reminder and alarm independently per day
- Vibration preference per day
- Repeat days configuration (e.g. only weekdays)
- Live countdown strings: "Bedtime in 2h 15m", "Alarm in 9h 45m"
- Sleep quality percentage — minutes of sleep / 480 (8h ideal), capped at 100%

---

### 😄 Mood Tracker

Daily mood logging with long-term analytics.

**Log moods:** AMAZING 😄 / GOOD 🙂 / OKAY 😐 / BAD 😞

**Home view** — today's mood, day streak (consecutive days logged), total logs count.

**Stats view:**
- Most common mood with emoji
- Per-mood counts and breakdown percentages
- Full calendar heatmap — each date coloured/labelled by mood logged
- Month selector with custom `?month=yyyy-MM` parameter

---

### 🩺 Medical & Smart Plan

A health intelligence layer that adapts the entire app to the user's medical profile.

**Medical Info Setup:** conditions (Diabetes, Hypertension, Thyroid, PCOS, High Cholesterol, IBS, Asthma) + allergies + medications + privacy toggles (hide weight, hide calories).

**Lab Results:** HbA1c, total cholesterol, LDL, HDL, fasting glucose, TSH, blood pressure — each generates targeted text recommendations on save.

**Smart Alerts:** auto-generated warnings when the user's meal plan conflicts with their conditions or allergies (e.g. "High Sodium Alert — today's meal is unsafe for Hypertension").

**Smart Plan Preview & Apply:**
1. Identifies unsafe meals by `diet_tag` (e.g. `high_sugar`, `fried`, `gluten`)
2. Finds condition-safe replacements (e.g. `low_gi`, `lean_protein`, `mediterranean`)
3. Identifies unsafe workout categories (e.g. Gym/Boxing for hypertension)
4. Finds safe replacements (e.g. Yoga/Walking)
5. Preview shows exact before/after swaps — apply writes directly to `meal_plans` and `workout_plan`

**Health Report** — 7-day summary: workouts completed/total, meals logged/total, total and average daily calories, daily progress bars with workout completion dots.

**Privacy controls** — hide weight and hide calories in family-visible data.

---

### 🩸 Blood Pressure & Blood Sugar Tracker

A clinical-grade vital signs log.

**Blood Pressure:**
- Log systolic/diastolic/pulse with full validation (systolic must exceed diastolic, ranges enforced)
- Auto-categorisation: Normal / Elevated / High Stage 1 / High Stage 2 / Hypertensive Crisis / Low
- Monthly stats with systolic and diastolic trend arrows (up ▲ / down ▼ / flat) and delta values
- Weekly averages, highest and lowest readings for the month

**Blood Sugar:**
- Log glucose + meal context: Fasting / Before Meal / After Meal / Bedtime
- Auto-categorisation using context-aware thresholds (fasting glucose < 100 = Normal; post-meal < 140 = Normal)
- Categories: Normal / Prediabetes / Diabetes / Hypoglycemia
- Monthly stats with fasting and after-meal trend analysis

---

### 📷 Body Scan Tracker

Body composition logging and trend analysis.

- Log body fat %, muscle mass %, water %, BMI, body type, and optional photo URI
- Full validation on all percentage fields
- Today's records with latest-first display
- Monthly body fat history — trend over time with change from previous month
- Body fat change delta between last two scans

---

### 👨‍⚕️ Doctor Consultations

An in-app medical consultation system with AI-assisted responses.

**Patient side:**
- Open a case with condition name, affected area, symptoms, and limitations
- Auto-detection of specialty: the system scans the text for diet keywords (e.g. "cholesterol", "digestion") vs workout keywords (e.g. "knee", "strain", "gym") and routes to the right doctor type
- Instant AI-generated first response with condition-specific advice (back pain protocol, knee RICE protocol, fatigue management, etc.)
- Follow-up messaging with contextual responses (getting worse → urgent escalation, feeling better → gradual return plan, asking about medication → OTC options, asking about exercise → safe/unsafe split)
- View all cases as a list with message counts and statuses

**Doctor side (separate app module):**
- Register with specialty (diet / workout / general), qualifications, hospital, years of experience
- Admin approval flow → email notification sent on approval
- Inbox filtered by specialty match — diet doctors see only diet cases, workout doctors see only workout cases
- Respond to cases → first response credits the doctor's wallet (price per case set per doctor)
- Wallet with full transaction history

---

### 🌱 Eco Sustainability Tracker

Rates how environmentally friendly the user's weekly meal plan is.

- Per-meal eco grade (A–D) from the meal's eco score
- Carbon level: Low / Medium / High
- Origin type: Local / Regional / Imported (inferred from ingredient categories and eco score)
- Packaging type: Recyclable / Mixed / Non-recyclable
- Weekly average score and letter grade with motivational message
- Personalised suggestions: "Choose more local ingredients", "Add plant-based proteins 2–3x per week", etc.

---

### 👨‍👩‍👧 Family Groups

Health tracking and accountability with family members.

- Create or auto-join a family group (each user gets one)
- Admin-only invite by email — looks up users in the database, prevents self-invite and duplicate invites
- Preview member before inviting — shows name, goal, and whether already a member
- Per-member dashboard: calories eaten today vs goal (%), water glasses, streak days, workouts this week, meals this week, calories burned this week, online status (logged anything today), Mon–Sun workout dot grid
- Family leaderboard — ranked by `streak × 10 + workouts × 5 + meals × 2` with 🥇🥈🥉 medals
- Group statistics: total calories burned this week, total meals eaten this week, average streak
- Shared grocery list — add items with quantities, check/uncheck with attribution ("Checked by Ahmed"), bulk clear checked items
- Rename the group, remove members, leave group

---

### 🛒 Grocery & Pantry

A full ingredient management system.

**Grocery Catalog** — browse all available ingredients with nutritional info, prices, and categories. Personalized endpoint flags recommended items based on goal (e.g. "Protein" category recommended for muscle gain) and excludes allergen items entirely.

**Grocery List** — personal shopping list with add/check/delete. Prevents duplicate additions by ingredient ID or name.

**Pantry** — track what's at home with expiry dates. Shows days until expiry sorted ascending so expiring items appear first.

**Recipe Suggestions from Pantry** — scans pantry ingredients and finds meals that can be made with them, sorted by ingredient match count. "You have 4 of 6 ingredients for Grilled Salmon."

---

### 🍽 Cloud Kitchen

Order meal prep directly through the app.

- Choose plan: Daily ($29.99) or Weekly ($179.99)
- Choose portion size: Small (−10%) / Regular / Large (+20%)
- Choose spice level
- Add notes
- Price calculated and stored; order ID returned immediately with "preparing" status

---

### 🎰 Spin Wheel

A gamified meal discovery and discount feature.

- Spin to get a random allergy-safe, goal-matched meal with a discount code
- Discount percentage tied to eco score: high eco = better chance at 20% off
- Code format: `FUELIFY-CHICKEN-4521` — unique per spin
- Codes expire after 24 hours
- Daily limit: 2 spins per user
- Redeem endpoint for Cloud Kitchen integration — validates code, checks expiry, marks used

---

### 🎯 Weekly Bingo

A gamification layer keeping users engaged across the full week.

- 3×3 bingo card generated on Monday, persists all week
- Tasks are personalised: workout targets scale with `exercise_days`, meal targets with `meals_per_day`, goal-specific variants (cardio workouts for weight loss, strength sessions for muscle gain)
- Live progress pulled from real data: water from `daily_logs`, workouts from `workout_session`, meals from `meal_plans`, streak from `daily_logs`
- Full bingo detection: rows, columns, and both diagonals
- Reward messages: "🏆 FULL CARD! 20% off next Cloud Kitchen order!" → "🎯 Start checking off tasks"

---

### 🍱 Scanned Pantry

Barcode-scan based product tracking.

- Add products by barcode with full nutritional data (calories, protein, carbs, fat, Nutri-Score)
- Expiry date tracking with days-until-expiry counter
- Sorted by closest expiry to minimise food waste

---

### 💬 AI Health Assistant — Aura

A contextual health chat powered by Groq LLaMA 3.1 8B Instant.

- Full conversation history — Aura remembers the last 10 messages for context
- Personalized system prompt injected with the user's health data (name, goal, points, etc.)
- Quick questions — pre-built prompts organised by Wellness, Fitness, and Favorites categories
- Clear history endpoint
- All messages persisted in `ai_chat` table linked to the user

---

### 🏆 Marketplace (Rewards)

A points-based loyalty and rewards system.

- Users earn points from orders (0.1 points per cent spent)
- Browse reward catalog filtered by category (Lifestyle / Gym)
- Redeem rewards — deducted from balance atomically
- My rewards history with dates
- Admin CRUD for reward management (JWT `isAdmin` claim gate)
- Points balance endpoint

---

### 🔔 Notifications

A full push-notification infrastructure.

- Per-user settings: Hydration / Steps / Sleep / Workout reminders with individual toggles
- Workout frequency: Every 30 Min / Every Hour / Every 2 Hours / Every 3 Hours
- Active hours window: `workoutFreqStart` to `workoutFreqEnd`
- DND mode: suppresses all notifications between configured start/end times (handles overnight windows correctly)
- Notification Center inbox with unread count and mark-as-read (individual + all)
- Welcome notification sequence on first verification (4 messages: welcome, hydration tip, steps goal, explore)
- Daily login notification — sent once per calendar day, personalised by hour (Good morning / afternoon / evening)
- Background scheduler running every 30 minutes to broadcast to all active users

---

## 🔌 Backend API — Complete Endpoint Reference

### Authentication (ktor-auth-api, Port 8081)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/auth/signup` | — | Register with email + password |
| POST | `/api/auth/login` | — | Login, returns JWT access + refresh tokens |
| POST | `/api/auth/refresh` | — | Rotate refresh token |
| POST | `/api/auth/verify-email` | — | Verify 6-digit OTP code |
| POST | `/api/auth/resend-verification` | — | Resend verification OTP |
| POST | `/api/auth/forgot-password` | — | Send password reset OTP |
| POST | `/api/auth/verify-reset-code` | — | Verify reset OTP → get resetToken |
| POST | `/api/auth/reset-password` | — | Set new password using resetToken |
| GET | `/api/auth/me` | JWT | Get current user profile |
| POST | `/api/auth/logout` | JWT | Blacklist current token |
| PUT | `/api/auth/profile` | JWT | Update username, name, email, picture |
| POST | `/api/auth/profile/verify-email-change` | JWT | Confirm email change OTP |
| POST | `/api/auth/change-password` | JWT | Change password (requires current) |
| DELETE | `/api/auth/account` | JWT | Permanently delete account + all data |
| POST | `/api/auth/guest` | — | Start guest session (30-day JWT) |
| POST | `/api/auth/guest/convert` | — | Convert guest to full account |

### Users & Onboarding (Main Backend, Port 8080)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/users/register` | — | Register with full onboarding data |
| GET | `/api/users/{id}` | — | Get user basic info |
| GET | `/api/users/by-email` | — | Lookup user by email |
| PUT | `/api/users/{id}/onboarding` | — | Save/update onboarding data |
| PATCH | `/api/users/{id}` | — | Update onboarding step |

### Dashboard & Logging

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/users/{id}/dashboard` | JWT | Full dashboard: meals, macros, water, workouts, streak, week stats, today's workout |
| POST | `/api/users/{id}/log-water` | JWT | Set water glasses for today |
| POST | `/api/users/{id}/log-meal` | JWT | Mark meal as eaten, add calories to daily log |

### Meals

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/meals/{id}/details` | JWT | Full meal detail: ingredients, instructions, video, eco grade |
| GET | `/api/users/{id}/search-meals?q=` | JWT | Search meals with suitability scoring |
| POST | `/api/users/{id}/switch-meal` | JWT | Swap a meal in today's plan |

### Workouts

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/workouts/categories` | JWT | All categories with emoji and counts |
| GET | `/api/workouts/suggested/{userId}` | JWT | Personalised suggestions + auto-populate weekly plan |
| GET | `/api/workouts/recommended/{userId}` | JWT | Daily-rotating extras outside the plan |
| GET | `/api/workouts` | JWT | Browse with `?category=&difficulty=&limit=` |
| GET | `/api/workouts/{id}` | JWT | Full detail with exercise list |
| GET | `/api/exercises` | JWT | Browse exercises by `?muscle_group=` |
| GET | `/api/exercises/{id}` | JWT | Single exercise detail |
| GET | `/api/users/{id}/workout-plan/week` | JWT | Full week plan with session labels and status |
| POST | `/api/users/{id}/workout-plan` | JWT | Manually schedule a workout |
| GET | `/api/users/{id}/workout-plan` | JWT | Upcoming 7 days |
| POST | `/api/users/{id}/workout-session` | JWT | Save a completed session |
| GET | `/api/users/{id}/workout-sessions` | JWT | Last 20 sessions |
| GET | `/api/users/{id}/workout-progress` | JWT | Today + week progress with percentages |

### Water Tracker

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/water/home` | JWT | Full home summary |
| GET | `/api/water/goal` | JWT | Current daily goal in ml |
| PUT | `/api/water/goal` | JWT | Update daily goal |
| GET | `/api/water/intake/logs` | JWT | Today's intake log entries |
| POST | `/api/water/intake/add` | JWT | Add water log |
| DELETE | `/api/water/intake/{timestamp}` | JWT | Delete a log entry |
| GET | `/api/water/statistics/daily` | JWT | Hourly chart data |
| GET | `/api/water/statistics/weekly` | JWT | 7-day chart data |
| GET | `/api/water/statistics/monthly` | JWT | Current month chart data |
| GET | `/api/water/statistics/quarterly` | JWT | Last 3 months |
| POST | `/api/water/statistics/quarterly/custom` | JWT | Custom month selection |
| GET | `/api/water/reminders` | JWT | All reminders |
| POST | `/api/water/reminders` | JWT | Add reminder |
| PUT | `/api/water/reminders/{id}` | JWT | Edit reminder |
| PATCH | `/api/water/reminders/{id}/toggle` | JWT | Enable/disable |
| DELETE | `/api/water/reminders/{id}` | JWT | Delete reminder |
| GET | `/api/water/reminders/auto` | JWT | Auto-reminder state |
| PUT | `/api/water/reminders/auto` | JWT | Set auto-reminder state |

### Sleep

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/sleep/today` | JWT | Today's schedule with countdowns |
| GET | `/api/sleep/schedules` | JWT | All 7 day schedules |
| GET | `/api/sleep/schedules/{day}` | JWT | Single day (1=Mon, 7=Sun) |
| PUT | `/api/sleep/schedules/{day}` | JWT | Update schedule |
| PATCH | `/api/sleep/schedules/{day}/bedtime-toggle` | JWT | Toggle bedtime alarm |
| PATCH | `/api/sleep/schedules/{day}/alarm-toggle` | JWT | Toggle wake alarm |

### Mood

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/mood/home` | JWT | Streak, total logs, today's mood |
| GET | `/api/mood/entries` | JWT | All entries newest first |
| POST | `/api/mood/entries` | JWT | Log/replace today's mood |
| GET | `/api/mood/entries/today` | JWT | Today's entry or null |
| DELETE | `/api/mood/entries/today` | JWT | Remove today's entry |
| GET | `/api/mood/stats` | JWT | Full stats snapshot |
| GET | `/api/mood/stats/breakdown` | JWT | Per-mood counts and percentages |
| GET | `/api/mood/stats/calendar` | JWT | Calendar data `?month=yyyy-MM` |

### Blood Pressure & Sugar

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/bp/readings` | JWT | All BP readings |
| GET | `/api/bp/readings/latest` | JWT | Latest reading |
| POST | `/api/bp/readings` | JWT | Add reading with validation |
| DELETE | `/api/bp/readings/{id}` | JWT | Delete reading |
| GET | `/api/bp/stats?year=&month=` | JWT | Monthly stats with trends |
| GET | `/api/bp/sugar/readings` | JWT | All blood sugar readings |
| GET | `/api/bp/sugar/readings/latest` | JWT | Latest reading |
| POST | `/api/bp/sugar/readings` | JWT | Add reading |
| DELETE | `/api/bp/sugar/readings/{id}` | JWT | Delete reading |
| GET | `/api/bp/sugar/stats?year=&month=` | JWT | Monthly stats with trends |

### Body Scan

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/bodyscan/records` | JWT | All records newest first |
| GET | `/api/bodyscan/records/latest` | JWT | Latest scan |
| GET | `/api/bodyscan/records/today` | JWT | Today's scans + latest |
| POST | `/api/bodyscan/records` | JWT | Save new scan |
| DELETE | `/api/bodyscan/records/{timestamp}` | JWT | Delete by timestamp |
| GET | `/api/bodyscan/stats` | JWT | Change delta + monthly history |

### Medical

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/users/{id}/medical-info` | JWT | Get conditions, allergies, medications |
| POST | `/api/users/{id}/medical-info` | JWT | Save medical info + process lab results |
| GET | `/api/users/{id}/medical-alerts` | JWT | Active alerts (auto-generated) |
| PATCH | `/api/users/{id}/medical-alerts/{alertId}` | JWT | Dismiss or apply alert |
| GET | `/api/users/{id}/smart-plan` | JWT | Condition-based plan recommendations |
| GET | `/api/users/{id}/smart-plan/preview` | JWT | Preview meal and workout swaps |
| POST | `/api/users/{id}/smart-plan/apply` | JWT | Apply swaps to database |
| GET | `/api/users/{id}/health-report` | JWT | 7-day health summary report |
| PATCH | `/api/users/{id}/health-report/privacy` | JWT | Toggle hide weight/calories |

### Doctor Consultations

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/users/{id}/consultation` | JWT | Open case + get AI response |
| GET | `/api/users/{id}/consultation` | JWT | All case summaries |
| GET | `/api/users/{id}/consultation/{caseId}` | JWT | Case detail with full chat |
| POST | `/api/users/{id}/consultation/{caseId}/message` | JWT | Send follow-up message |
| PATCH | `/api/users/{id}/consultation/{caseId}` | JWT | Update case status |
| POST | `/api/doctor/register` | — | Doctor onboarding |
| POST | `/api/doctor/login` | — | Doctor login |
| POST | `/api/doctor/{id}/approve` | — | Admin: approve doctor + send email |
| GET | `/api/doctor/{id}/profile` | — | Doctor profile |
| GET | `/api/doctor/{id}/inbox` | — | Cases filtered by specialty |
| GET | `/api/doctor/{id}/cases/{caseId}` | — | Full case with patient name |
| POST | `/api/doctor/{id}/cases/{caseId}/respond` | — | Respond + credit wallet |
| GET | `/api/doctor/{id}/wallet` | — | Balance + transaction history |

### Eco, Family, Groceries, Kitchen, Bingo, Spin

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/users/{id}/eco` | Weekly sustainability report |
| GET/POST | `/api/users/{id}/family` | Get/create family group |
| POST | `/api/users/{id}/family/invite` | Invite by email |
| DELETE | `/api/users/{id}/family/member/{memberId}` | Remove member |
| GET | `/api/users/{id}/family/dashboard` | Full family dashboard |
| GET/POST | `/api/users/{id}/family/grocery` | Family shopping list |
| PATCH | `/api/users/{id}/family/grocery/{itemId}/check` | Check/uncheck item |
| GET | `/api/users/{id}/grocery/recommended` | Personalised catalog |
| GET/POST | `/api/users/{id}/grocery` | Personal grocery list |
| GET/POST | `/api/users/{id}/pantry` | Pantry with expiry tracking |
| GET | `/api/users/{id}/pantry/suggestions` | Recipe suggestions from pantry |
| GET/POST | `/api/users/{id}/scanned-pantry` | Barcode product pantry |
| POST | `/api/users/{id}/kitchen-order` | Place Cloud Kitchen order |
| GET | `/api/users/{id}/spin-wheel` | Spin for a discounted meal |
| POST | `/api/users/{id}/spin-wheel/redeem/{code}` | Redeem discount code |
| GET | `/api/users/{id}/bingo` | Get/create weekly bingo card |
| GET | `/api/users/{id}/search-meals?q=` | Search with suitability scoring |

### Notifications & Marketplace (ktor-auth-api)

| Method | Path | Description |
|--------|------|-------------|
| GET/PUT | `/api/notifications/settings` | Notification preferences |
| GET | `/api/notifications` | Notification inbox |
| PUT | `/api/notifications/read-all` | Mark all read |
| PUT | `/api/notifications/{id}/read` | Mark one read |
| GET | `/api/marketplace` | Browse rewards `?category=` |
| GET | `/api/marketplace/points` | User points balance |
| GET | `/api/marketplace/my-rewards` | Redeemed rewards |
| POST | `/api/marketplace/redeem` | Redeem a reward |
| GET/POST/PUT/DELETE | `/api/admin/marketplace` | Admin reward management |
| POST | `/api/chat` | Send message to Aura |
| GET | `/api/chat/history` | Chat history |
| DELETE | `/api/chat/history` | Clear chat |
| GET | `/api/chat/quick-questions` | Quick question categories |
| POST | `/upload-image` | Upload image to Supabase |

---

## 🧠 Intelligence Layer

### Nutrition Engine (`NutritionEngine.kt`)

All calculations are pure functions — no database access, fully testable.

```
BMR (Mifflin-St Jeor):
  Male:   10w + 6.25h − 5a + 5
  Female: 10w + 6.25h − 5a − 161

TDEE = BMR × activity multiplier
  Sedentary → 1.2 | Light → 1.375 | Moderate → 1.55
  Very Active → 1.725 | Athlete → 1.9

Daily Calories:
  Lose Weight  → TDEE − 500
  Gain Muscle  → TDEE + 300
  Maintain     → TDEE
  Floor: 1,200 kcal

Macros (% of daily calories):
  Lose Weight:  35% protein, 40% carbs, 25% fat
  Gain Muscle:  30% protein, 45% carbs, 25% fat
  Default:      25% protein, 50% carbs, 25% fat
  (protein/carbs ÷ 4 kcal/g; fat ÷ 9 kcal/g)

Meal Scoring (5 dimensions):
  Diet type rank match:  +0–30 pts
  Meal time match:       +25 pts
  Beginner + Easy meal:  +10 pts
  Calorie proximity:     −(|diff|/100) × 8 pts
  Eco motivation:        +5 pts if eco_score > 8.5
```

### Workout Plan Engine (`WorkoutPlanEngine.kt`)

```
sessionsPerDay = ceil(TDEE × burnPct / avgCalPerSession)
  burnPct: lose=30%, gain=20%, other=22%
  avgCal:  Running=350, Yoga=180, Gym=320
  cap: max 3 sessions per day

Day offsets by exercise_days:
  1 → [Mon]
  2 → [Mon, Thu]
  3 → [Mon, Wed, Fri]
  4 → [Mon, Tue, Thu, Fri]
  5 → [Mon–Fri]
  6 → [Mon–Sat]
  7 → every day

Session labels by goal:
  Gain:  Upper Body | Lower Body | Chest&Tris | Back&Bis | Legs&Core
  Lose:  Cardio Burn | Cardio | HIIT | Full Body
  Other: Full Body | Strength | Cardio | Upper Body
```

### Allergy Safety Filter

Fuelify maintains a keyword map across 8 allergen categories. Before any meal is served to a user, its name is checked against the union of their onboarding allergies and their linked allergy type records.

```
dairy    → yogurt, cheese, butter, milk, cream, whey
gluten   → pasta, bread, toast, wrap, pancake, oat, wheat, meatball
nuts     → almond, walnut, cashew, pistachio, nut
soy      → tofu, soy, edamame
eggs     → egg, omelette, frittata
shellfish→ shrimp, prawn, crab, lobster, scallop
peanuts  → peanut
fish     → salmon, tuna, cod, tilapia, fish, anchovy, sardine
```

---

## 🗄 Database Schema

### Core Tables

| Table | Rows | Description |
|-------|------|-------------|
| `users` | — | Full user profile — 30+ columns including onboarding data, auth fields, and points |
| `meal` | 39 | Meals with macros, eco score, diet type, image, difficulty, price |
| `recipe` | 39 | Instructions, video URL, calorie quality score |
| `ingredient` | 20 | Base ingredients with per-unit macros, eco score, allergen flag |
| `meal_ingredient` | 150 | Ingredient-meal links with quantities |
| `meal_plans` | — | Daily meal schedules per user with scaled calories |
| `daily_logs` | — | Per-user per-day: calories eaten, water, workouts, streak |
| `workout` | — | Workout catalog with categories and calorie estimates |
| `exercise` | — | Exercise library with muscle groups |
| `workout_exercise` | — | Workout-exercise links with reps/sets/rest |
| `workout_plan` | — | Weekly workout schedule per user with session labels |
| `workout_session` | — | Completed workout sessions with duration and calories |

### Health Tracking Tables

| Table | Description |
|-------|-------------|
| `health_water_logs` | Timestamped water intake entries |
| `health_water_goal` | Single-row user water goal |
| `health_water_reminders` | Scheduled reminder alarms |
| `health_water_auto_reminder` | Auto-reminder global toggle |
| `health_sleep_schedules` | Per-weekday sleep/wake configuration |
| `health_mood_entries` | Daily mood logs with dateKey |
| `health_bp_readings` | Blood pressure readings with auto-category |
| `health_bs_readings` | Blood sugar readings with context |
| `health_body_scan_records` | Body composition scans |

### Social & Commerce Tables

| Table | Description |
|-------|-------------|
| `family_group` | Family group metadata |
| `family_member` | User-to-group mapping with roles |
| `family_grocery_item` | Shared shopping list |
| `grocery_catalog` | Ingredient store catalog |
| `grocery_list` / `grocery_item` | Personal shopping lists |
| `pantry_item` | Home pantry with expiry dates |
| `scanned_pantry_item` | Barcode-scanned products |
| `kitchen_order` | Cloud kitchen orders |
| `spin_discounts` | Spin wheel codes with expiry |
| `bingo_cards` | Weekly bingo cards with progress |

### Medical & Consultation Tables

| Table | Description |
|-------|-------------|
| `user_medical_info` | Conditions, allergies, medications, privacy flags |
| `medical_alert` | Generated alerts with dismiss/apply state |
| `doctor_profile` | Doctor accounts with specialty and approval |
| `doctor_wallet` | Doctor earnings balance |
| `doctor_wallet_transaction` | Per-case credit history |
| `doctor_consultation` | Patient cases with specialty routing |
| `consultation_message` | Chat messages per case |

### Auth Tables (ktor-auth-api)

| Table | Description |
|-------|-------------|
| `users` | Shared with main backend — auth fields only |
| `verification_tokens` | OTP and reset tokens with type and expiry |
| `token_blacklist` | Invalidated JWTs |
| `login_attempts` | Rate limiting per email/IP |
| `guest_sessions` | Guest JWT sessions |
| `notifications` | Notification center inbox |
| `notification_settings` | Per-user notification preferences |
| `ai_chat` | Aura conversation history |
| `reward` | Marketplace items |
| `user_reward` | Redemption records |

---

## 🔐 Auth API (ktor-auth-api)

The auth backend is a fully independent Ktor microservice with its own database pool, its own JWT configuration, and its own email service.

### Security Features

- **BCrypt password hashing** with cost factor 12
- **JWT access tokens** (short-lived) + **refresh tokens** (long-lived), rotated on each use
- **Token blacklist** — logout invalidates tokens immediately, checked on every authenticated request
- **Rate limiting** — configurable max login attempts + lockout duration
- **IP tracking** on login attempts
- **Input sanitisation** — all user-supplied strings are HTML-entity-escaped
- **Email validation** — restricted to real email domains: gmail, outlook, hotmail, live
- **Password requirements** — min 8 characters, 1 uppercase, 1 digit
- **Email verification** required before first login
- **OTP-based flows** for email verification, email change, and password reset — all expire in 15 minutes
- **Admin flag** in JWT claim — admin-only routes gate with `isAdmin` claim check
- **Guest tokens** — 30-day JWT with `type: "guest"`, convertible to full account
- **Supabase Storage** — images never stored in the database

### JWT Claims

```json
Access Token: {
  "userId": 42,
  "email": "user@example.com",
  "type": "access",
  "isAdmin": false
}

Refresh Token: {
  "userId": 42,
  "email": "user@example.com",
  "type": "refresh"
}

Guest Token: {
  "guestId": "guest_abc123...",
  "type": "guest"
}
```

---

## ⚙️ Setup & Installation

### Prerequisites

| Requirement | Version |
|-------------|---------|
| JDK | 17+ |
| Gradle | 8+ |
| Android Studio | Hedgehog (2023.1)+ |
| Android Min SDK | 24 |
| Android Target SDK | 34 |

### Run the Main Backend

```bash
cd fuelify_project/backend
gradle run
# Server starts on port 8080
```

### Run the Auth Backend

```bash
cd fuelify_project/backend/ktor-auth-api
gradle run
# Server starts on port 8081
```

### Run the Android App

1. Open Android Studio → **Open** → select `fuelify_project/android/`
2. Let Gradle sync complete
3. Edit `RetrofitClient.kt`:

```kotlin
// Android Emulator
const val BASE_URL = "http://10.0.2.2:8080/"

// Real Device (replace with your machine's local IP)
const val BASE_URL = "http://192.168.1.100:8080/"
```

### Android Dependencies

```gradle
implementation 'com.squareup.retrofit2:retrofit:2.9.0'
implementation 'com.squareup.retrofit2:converter-gson:2.9.0'
implementation 'com.github.bumptech.glide:glide:4.16.0'
implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0'
```
Note: The Groq API key has been removed from the config for GitHub. To enable Aura AI chat, add your own groq.apiKey in application.conf — get a free key at console.groq.com.
---

## 🌍 Environment Variables

### Main Backend (`application.conf`)

| Variable | Description |
|----------|-------------|
| `database.url` | Neon.tech JDBC URL |
| `database.user` | Database username |
| `database.password` | Database password |
| `database.maxPoolSize` | HikariCP pool size |
| `jwt.secret` | HMAC256 signing secret |
| `jwt.issuer` | Token issuer string |
| `jwt.audience` | Token audience string |
| `jwt.realm` | Authentication realm |
| `PORT` | Server port (default: 8080) |

### Auth Backend (`application.conf`)

| Variable | Description |
|----------|-------------|
| `database.url` | PostgreSQL JDBC URL |
| `database.user` | Database username |
| `database.password` | Database password |
| `database.mode` | `postgres` or `h2` (for local dev) |
| `jwt.secret` | HMAC256 signing secret |
| `jwt.accessTokenExpiry` | Access token TTL in ms |
| `jwt.refreshTokenExpiry` | Refresh token TTL in ms |
| `email.mock` | `true` to log emails instead of sending |
| `email.host` | SMTP host (e.g. smtp.gmail.com) |
| `email.username` | SMTP username |
| `email.password` | SMTP app password |
| `groq.apiKey` | Groq API key for LLaMA 3.1 |
| `supabase.url` | Supabase project URL |
| `supabase.key` | Supabase service role key |
| `supabase.bucket` | Storage bucket name |
| `security.maxLoginAttempts` | Lockout threshold (default: 5) |
| `security.lockoutDurationMinutes` | Lockout window (default: 15) |

---



---

## 🚀 Feature Roadmap

| Feature | Status |
|---------|--------|
| Google Fit / Apple Health Integration | 🔜 Pending |
| Streak restoration challenges | 🔜 Pending |
| AI Meal Image Recognition | 🔜 Pending |
| Multi-language support (Arabic first) | 🔜 Pending |

---

## 💡 Customisation Tips

**Change the backend URL** → edit `RetrofitClient.BASE_URL`

**Add more meals** → insert into `meal`, `recipe`, and `meal_ingredient` tables in Neon; all scoring and allergy filtering picks them up automatically.

**Add authentication to all endpoints** → the JWT plugin is already configured as `auth-jwt`; wrap any route block in `authenticate("auth-jwt") { ... }`.

**Enable real email delivery** → set `email.mock = false` in `application.conf` and provide SMTP credentials. Gmail App Passwords work out of the box.

**Extend the Smart Plan** → add new condition keywords to `buildSmartPlan()` in `MedicalRoutes.kt` and corresponding unsafe tags to `getUnsafeTags()`.

**Meal Delivery Search** → requires a Google Custom Search API key enabled in Google Console (`cx=b71de9a88248243f6`).

---

## 👥 Team

Built as a university capstone project — **Alexandria National University, 2026.**

---

## 📄 License

This project is for educational purposes only. All rights reserved.

---

<div align="center">

*Made with 🥦 and way too many late nights in Alexandria.*

</div>
