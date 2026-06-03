package com.example.fuelify.data.api

import com.example.fuelify.data.api.models.*
import retrofit2.Response
import retrofit2.http.*
import com.example.fuelify.statistics.HomeData
import com.example.fuelify.statistics.GoalData
import com.example.fuelify.statistics.GoalRequest
import com.example.fuelify.statistics.WaterLog
import com.example.fuelify.statistics.IntakeRequest
import com.example.fuelify.statistics.DailyStats
import com.example.fuelify.statistics.WeeklyStats
import com.example.fuelify.statistics.MonthlyStats
import com.example.fuelify.statistics.QuarterlyStats
import com.example.fuelify.statistics.QuarterlyRequest
import com.example.fuelify.statistics.ReminderItem
import com.example.fuelify.statistics.ReminderRequest
import com.example.fuelify.statistics.ToggleReminderRequest
import com.example.fuelify.statistics.AutoReminderData
import com.example.fuelify.statistics.AutoReminderRequest
import com.example.fuelify.statistics.SleepScheduleData
import com.example.fuelify.statistics.UpdateSleepRequest
import com.example.fuelify.statistics.SleepToggleRequest
import com.example.fuelify.statistics.MoodEntry
import com.example.fuelify.statistics.AddMoodRequest
import com.example.fuelify.statistics.MoodHomeData
import com.example.fuelify.statistics.MoodStatsData
import com.example.fuelify.statistics.MoodBreakdownItem
import com.example.fuelify.statistics.BloodPressureReadingData
import com.example.fuelify.statistics.BloodSugarReadingData
import com.example.fuelify.statistics.AddBpReadingRequest
import com.example.fuelify.statistics.AddBsReadingRequest
import com.example.fuelify.statistics.BpStatsData
import com.example.fuelify.statistics.BsStatsData
import com.example.fuelify.statistics.BodyScanRecordData
import com.example.fuelify.statistics.AddBodyScanRequest
import com.example.fuelify.statistics.BodyScanStatsData
import com.example.fuelify.statistics.TodayBodyScanData

interface FuelifyApi {

    @POST("api/users/register")
    suspend fun registerUser(@Body request: RegisterUserRequest): Response<ApiResponse<UserResponse>>

    @GET("api/users/{id}/dashboard")
    suspend fun getDashboard(@Path("id") userId: Int): Response<ApiResponse<DashboardData>>

    @POST("api/users/{id}/kitchen-order")
    suspend fun createKitchenOrder(
        @Path("id") userId: Int,
        @Body request: KitchenOrderRequest
    ): Response<ApiResponse<KitchenOrderResponse>>

    @POST("api/users/{id}/log-water")
    suspend fun logWater(@Path("id") userId: Int, @Body request: LogWaterRequest): Response<ApiResponse<Int>>

    @POST("api/users/{id}/log-meal")
    suspend fun logMeal(@Path("id") userId: Int, @Body request: LogMealRequest): Response<ApiResponse<Int>>

    @GET("api/meals/{id}/details")
    suspend fun getMealDetail(@Path("id") mealId: Int): Response<ApiResponse<MealDetailData>>

    @GET("api/users/{id}/search-meals")
    suspend fun searchMeals(
        @Path("id") userId: Int,
        @Query("q") query: String = ""
    ): Response<ApiResponse<List<SearchMealItem>>>

    @POST("api/users/{id}/switch-meal")
    suspend fun switchMeal(
        @Path("id") userId: Int,
        @Body request: SwitchMealRequest
    ): Response<ApiResponse<Int>>

    // ── Grocery Catalog ───────────────────────────────────────────────────────
    @GET("api/grocery/catalog")
    suspend fun getGroceryCatalog(
        @Query("category") category: String? = null
    ): Response<CatalogResponse>

    @GET("api/users/{id}/grocery/recommended")
    suspend fun getPersonalizedCatalog(
        @Path("id") userId: Int,
        @Query("category") category: String? = null
    ): Response<CatalogResponse>

    // ── User Grocery List ─────────────────────────────────────────────────────
    @GET("api/users/{id}/grocery")
    suspend fun getGroceryList(@Path("id") userId: Int): Response<GroceryListResponse>

    @POST("api/users/{id}/grocery")
    suspend fun addToGroceryList(
        @Path("id") userId: Int,
        @Body request: AddGroceryItemRequest
    ): Response<ApiResponse<Nothing>>

    @PATCH("api/users/{id}/grocery/{itemId}/check")
    suspend fun checkGroceryItem(
        @Path("id") userId: Int,
        @Path("itemId") itemId: Int,
        @Query("checked") checked: Boolean
    ): Response<ApiResponse<Nothing>>

    @DELETE("api/users/{id}/grocery/clear-checked")
    suspend fun clearCheckedItems(@Path("id") userId: Int): Response<ApiResponse<Nothing>>

    @DELETE("api/users/{id}/grocery/{itemId}")
    suspend fun deleteGroceryItem(
        @Path("id") userId: Int,
        @Path("itemId") itemId: Int
    ): Response<ApiResponse<Nothing>>

    // ── Pantry ────────────────────────────────────────────────────────────────
    @GET("api/users/{id}/pantry")
    suspend fun getPantry(@Path("id") userId: Int): Response<PantryResponse>

    @POST("api/users/{id}/pantry")
    suspend fun addPantryItem(
        @Path("id") userId: Int,
        @Body request: AddPantryRequest
    ): Response<ApiResponse<Nothing>>

    @DELETE("api/users/{id}/pantry/{itemId}")
    suspend fun deletePantryItem(
        @Path("id") userId: Int,
        @Path("itemId") itemId: Int
    ): Response<ApiResponse<Nothing>>

    @GET("api/users/{id}/pantry/suggestions")
    suspend fun getPantrySuggestions(@Path("id") userId: Int): Response<SuggestionsResponse>

    // ── Eco Sustainability ────────────────────────────────────────────────────
    @GET("api/users/{id}/eco")
    suspend fun getEcoSustainability(@Path("id") userId: Int): Response<EcoResponse>

    // ── Doctor Consultation ──────────────────────────────────────────────────
    @POST("api/users/{id}/consultation")
    suspend fun createConsultation(
        @Path("id") userId: Int,
        @Body request: CreateConsultationRequest
    ): Response<CreateConsultationResponse>

    // Get ALL cases as a list (for the cases list screen)
    @GET("api/users/{id}/consultation")
    suspend fun getConsultationList(@Path("id") userId: Int): Response<ConsultationListResponse>

    // Get a single case with full chat (for the chat screen)
    @GET("api/users/{id}/consultation/{caseId}")
    suspend fun getConsultationCase(
        @Path("id") userId: Int,
        @Path("caseId") caseId: Int
    ): Response<ConsultationResponse>

    @POST("api/users/{id}/consultation/{caseId}/message")
    suspend fun sendConsultationMessage(
        @Path("id") userId: Int,
        @Path("caseId") caseId: Int,
        @Body request: SendMessageRequest
    ): Response<ApiResponse<Nothing>>

    @PATCH("api/users/{id}/consultation/{caseId}")
    suspend fun updateConsultationCase(
        @Path("id") userId: Int,
        @Path("caseId") caseId: Int,
        @Body request: UpdateCaseRequest
    ): Response<ApiResponse<Nothing>>

    // ── Medical ───────────────────────────────────────────────────────────────
    @GET("api/users/{id}/medical-info")
    suspend fun getMedicalInfo(@Path("id") userId: Int): Response<MedicalInfoResponse>

    @POST("api/users/{id}/medical-info")
    suspend fun saveMedicalInfo(
        @Path("id") userId: Int,
        @Body request: SaveMedicalInfoRequest
    ): Response<ApiResponse<Nothing>>

    @GET("api/users/{id}/medical-alerts")
    suspend fun getMedicalAlerts(@Path("id") userId: Int): Response<AlertsApiResponse>
    // AlertsApiResponse.data is AlertsResponse which maps to backend AlertsResponseDto

    @PATCH("api/users/{id}/medical-alerts/{alertId}")
    suspend fun updateMedicalAlert(
        @Path("id") userId: Int,
        @Path("alertId") alertId: Int,
        @Body request: AlertActionRequest
    ): Response<ApiResponse<Nothing>>

    @GET("api/users/{id}/smart-plan/preview")
    suspend fun getSmartPlanPreview(@Path("id") userId: Int): Response<SmartPlanPreviewResponse>

    @POST("api/users/{id}/smart-plan/apply")
    suspend fun applySmartPlan(@Path("id") userId: Int): Response<ApplyResultResponse>

    @GET("api/users/{id}/smart-plan")
    suspend fun getSmartPlan(@Path("id") userId: Int): Response<SmartPlanResponse>

    @GET("api/users/{id}/health-report")
    suspend fun getHealthReport(@Path("id") userId: Int): Response<HealthReportResponse>

    @PATCH("api/users/{id}/health-report/privacy")
    suspend fun updatePrivacy(
        @Path("id") userId: Int,
        @Body request: PrivacyRequest
    ): Response<ApiResponse<Nothing>>

    // ── Workout Sessions ──────────────────────────────────────────────────────
    @POST("api/users/{id}/workout-session")
    suspend fun saveWorkoutSession(
        @Path("id") userId: Int,
        @Body request: SaveWorkoutSessionRequest
    ): Response<ApiResponse<Int>>

    @GET("api/users/{id}/workout-sessions")
    suspend fun getWorkoutSessions(@Path("id") userId: Int): Response<ApiResponse<List<Any>>>

    @GET("api/users/{id}/workout-progress")
    suspend fun getWorkoutProgress(@Path("id") userId: Int): Response<ApiResponse<WorkoutProgress>>

    @GET("api/users/{id}/workout-plan/week")
    suspend fun getWeekWorkoutPlan(@Path("id") userId: Int): Response<WeekPlanResponse>

    @POST("api/users/{id}/workout-plan")
    suspend fun saveWorkoutPlan(
        @Path("id") userId: Int,
        @Body request: SaveWorkoutPlanRequest
    ): Response<ApiResponse<Int>>

    // ── Workouts ──────────────────────────────────────────────────────────────
    @GET("api/workouts/categories")
    suspend fun getWorkoutCategories(): Response<CategoryListResponse>

    @GET("api/workouts/suggested/{userId}")
    suspend fun getSuggestedWorkouts(@Path("userId") userId: Int): Response<SuggestedWorkoutsResponse>

    @GET("api/workouts/recommended/{userId}")
    suspend fun getRecommendedWorkouts(@Path("userId") userId: Int): Response<SuggestedWorkoutsResponse>

    @GET("api/workouts")
    suspend fun getWorkouts(
        @Query("category") category: String? = null,
        @Query("difficulty") difficulty: String? = null,
        @Query("limit") limit: Int? = null
    ): Response<WorkoutListResponse>

    @GET("api/workouts/{id}")
    suspend fun getWorkoutDetail(@Path("id") workoutId: Int): Response<WorkoutDetailResponse>

    @GET("api/exercises")
    suspend fun getExercises(
        @Query("muscle_group") muscleGroup: String? = null,
        @Query("limit") limit: Int? = null
    ): Response<ExerciseListResponse>

    @GET("api/exercises/{id}")
    suspend fun getExerciseDetail(@Path("id") exerciseId: Int): Response<ExerciseDetailResponse>
    
    // ── Scanned Pantry ────────────────────────────────────────────────────────
    @GET("api/users/{id}/scanned-pantry")
    suspend fun getScannedPantry(@Path("id") userId: Int): Response<ApiResponse<List<ScannedPantryItem>>>

    @POST("api/users/{id}/scanned-pantry")
    suspend fun addScannedPantryItem(
        @Path("id") userId: Int,
        @Body request: AddScannedPantryRequest
    ): Response<ApiResponse<Nothing>>

    @DELETE("api/users/{id}/scanned-pantry/{itemId}")
    suspend fun deleteScannedPantryItem(
        @Path("id") userId: Int,
        @Path("itemId") itemId: Int
    ): Response<ApiResponse<Nothing>>

// ── Family ────────────────────────────────────────────────────────────────
    @GET("api/users/{id}/family/lookup")
    suspend fun lookupFamilyMember(
        @Path("id") userId: Int,
        @Query("email") email: String
    ): Response<MemberPreviewResponse>

    @GET("api/users/{id}/family")
    suspend fun getFamily(@Path("id") userId: Int): Response<FamilyGroupResponse>

    @GET("api/users/{id}/family/dashboard")
    suspend fun getFamilyDashboard(@Path("id") userId: Int): Response<FamilyDashboardResponse>

    @POST("api/users/{id}/family/invite")
    suspend fun inviteFamilyMember(
        @Path("id") userId: Int,
        @Body request: InviteMemberRequest
    ): Response<ApiResponse<Nothing>>

    @DELETE("api/users/{id}/family/member/{memberId}")
    suspend fun removeFamilyMember(
        @Path("id") userId: Int,
        @Path("memberId") memberId: Int
    ): Response<ApiResponse<Nothing>>

    @PATCH("api/users/{id}/family/name")
    suspend fun renameFamilyGroup(
        @Path("id") userId: Int,
        @Body request: RenameFamilyRequest
    ): Response<ApiResponse<Nothing>>

    @GET("api/users/{id}/family/grocery")
    suspend fun getFamilyGrocery(@Path("id") userId: Int): Response<FamilyGroceryResponse>
    
       @POST("api/users/{id}/family/grocery")
    suspend fun addFamilyGroceryItem(
        @Path("id") userId: Int,
        @Body request: AddFamilyGroceryRequest
    ): Response<ApiResponse<Nothing>>

    @PATCH("api/users/{id}/family/grocery/{itemId}/check")
    suspend fun checkFamilyGroceryItem(
        @Path("id") userId: Int,
        @Path("itemId") itemId: Int,
        @Body request: CheckFamilyGroceryRequest
    ): Response<ApiResponse<Nothing>>

    @DELETE("api/users/{id}/family/grocery/clear-checked")
    suspend fun clearCheckedFamilyItems(@Path("id") userId: Int): Response<ApiResponse<Nothing>>

    @DELETE("api/users/{id}/family/grocery/{itemId}")
    suspend fun deleteFamilyGroceryItem(
        @Path("id") userId: Int,
        @Path("itemId") itemId: Int
    ): Response<ApiResponse<Nothing>>
    
    @GET("api/users/{id}/spin-wheel")
suspend fun spinWheel(@Path("id") userId: Int): Response<SpinWheelResponse>

@GET("api/users/{id}/bingo")
suspend fun getBingoCard(@Path("id") userId: Int): Response<BingoResponse>

    
    // ── Doctor ────────────────────────────────────────────────────────────────
    @POST("api/doctor/register")
    suspend fun doctorRegister(@Body request: DoctorOnboardRequest): Response<DoctorRegisterResponse>

    @POST("api/doctor/login")
    suspend fun doctorLogin(@Body request: DoctorLoginRequest): Response<DoctorLoginResponse>

    @GET("api/doctor/{id}/profile")
    suspend fun getDoctorProfile(@Path("id") doctorId: Int): Response<DoctorLoginResponse>

    @GET("api/doctor/{id}/inbox")
    suspend fun getDoctorInbox(@Path("id") doctorId: Int): Response<InboxResponse>

    @GET("api/doctor/{id}/cases/{caseId}")
    suspend fun getDoctorCase(
        @Path("id") doctorId: Int,
        @Path("caseId") caseId: Int
    ): Response<CaseDetailResponse>

    @POST("api/doctor/{id}/cases/{caseId}/respond")
    suspend fun doctorRespond(
        @Path("id") doctorId: Int,
        @Path("caseId") caseId: Int,
        @Body request: DoctorRespondRequest
    ): Response<ApiResponse<Nothing>>

    @GET("api/doctor/{id}/wallet")
    suspend fun getDoctorWallet(@Path("id") doctorId: Int): Response<WalletResponse>
    
    
    // ── Water Tracker ─────────────────────────────────────────────────────────
    @GET("api/water/home")
    suspend fun getWaterHome(): Response<ApiResponse<HomeData>>

    @GET("api/water/goal")
    suspend fun getWaterGoal(): Response<ApiResponse<GoalData>>

    @PUT("api/water/goal")
    suspend fun setWaterGoal(@Body body: GoalRequest): Response<ApiResponse<Unit>>

    @GET("api/water/intake/logs")
    suspend fun getWaterLogs(): Response<ApiResponse<List<WaterLog>>>

    @POST("api/water/intake/add")
    suspend fun addWaterLog(@Body body: IntakeRequest): Response<ApiResponse<WaterLog>>

    @DELETE("api/water/intake/{timestamp}")
    suspend fun deleteWaterLog(@Path("timestamp") timestamp: Long): Response<ApiResponse<Unit>>

    @GET("api/water/statistics/daily")
    suspend fun getWaterDailyStats(): Response<ApiResponse<DailyStats>>

    @GET("api/water/statistics/weekly")
    suspend fun getWaterWeeklyStats(): Response<ApiResponse<WeeklyStats>>

    @GET("api/water/statistics/monthly")
    suspend fun getWaterMonthlyStats(): Response<ApiResponse<MonthlyStats>>

    @GET("api/water/statistics/quarterly")
    suspend fun getWaterQuarterlyStats(): Response<ApiResponse<QuarterlyStats>>

    @POST("api/water/statistics/quarterly/custom")
    suspend fun getWaterCustomQuarterlyStats(@Body body: QuarterlyRequest): Response<ApiResponse<QuarterlyStats>>

    @GET("api/water/reminders")
    suspend fun getWaterReminders(): Response<ApiResponse<List<ReminderItem>>>

    @POST("api/water/reminders")
    suspend fun addWaterReminder(@Body body: ReminderRequest): Response<ApiResponse<ReminderItem>>

    @PUT("api/water/reminders/{id}")
    suspend fun editWaterReminder(@Path("id") id: String, @Body body: ReminderRequest): Response<ApiResponse<ReminderItem>>

    @PATCH("api/water/reminders/{id}/toggle")
    suspend fun toggleWaterReminder(@Path("id") id: String, @Body body: ToggleReminderRequest): Response<ApiResponse<Unit>>

    @DELETE("api/water/reminders/{id}")
    suspend fun deleteWaterReminder(@Path("id") id: String): Response<ApiResponse<Unit>>

    @GET("api/water/reminders/auto")
    suspend fun getAutoReminder(): Response<ApiResponse<AutoReminderData>>

    @PUT("api/water/reminders/auto")
    suspend fun setAutoReminder(@Body body: AutoReminderRequest): Response<ApiResponse<Unit>>

    // ── Sleep Tracker ─────────────────────────────────────────────────────────
    @GET("api/sleep/today")
    suspend fun getSleepToday(): Response<ApiResponse<SleepScheduleData>>

    @GET("api/sleep/schedules")
    suspend fun getAllSleepSchedules(): Response<ApiResponse<List<SleepScheduleData>>>

    @GET("api/sleep/schedules/{day}")
    suspend fun getSleepSchedule(@Path("day") day: Int): Response<ApiResponse<SleepScheduleData>>

    @PUT("api/sleep/schedules/{day}")
    suspend fun updateSleepSchedule(@Path("day") day: Int, @Body body: UpdateSleepRequest): Response<ApiResponse<SleepScheduleData>>

    @PATCH("api/sleep/schedules/{day}/bedtime-toggle")
    suspend fun toggleBedtime(@Path("day") day: Int, @Body body: SleepToggleRequest): Response<ApiResponse<Unit>>

    @PATCH("api/sleep/schedules/{day}/alarm-toggle")
    suspend fun toggleAlarm(@Path("day") day: Int, @Body body: SleepToggleRequest): Response<ApiResponse<Unit>>

    // ── Mood Tracker ──────────────────────────────────────────────────────────
    @GET("api/mood/home")
    suspend fun getMoodHome(): Response<ApiResponse<MoodHomeData>>

    @GET("api/mood/entries")
    suspend fun getMoodEntries(): Response<ApiResponse<List<MoodEntry>>>

    @POST("api/mood/entries")
    suspend fun addMoodEntry(@Body body: AddMoodRequest): Response<ApiResponse<MoodEntry>>

    @GET("api/mood/entries/today")
    suspend fun getTodayMoodEntry(): Response<ApiResponse<MoodEntry?>>

    @DELETE("api/mood/entries/today")
    suspend fun deleteTodayMoodEntry(): Response<ApiResponse<Unit>>

    @GET("api/mood/stats")
    suspend fun getMoodStats(): Response<ApiResponse<MoodStatsData>>

    @GET("api/mood/stats/breakdown")
    suspend fun getMoodBreakdown(): Response<ApiResponse<List<MoodBreakdownItem>>>

    // ── Blood Pressure & Sugar ────────────────────────────────────────────────
    @GET("api/bp/readings")
    suspend fun getBpReadings(): Response<ApiResponse<List<BloodPressureReadingData>>>

    @GET("api/bp/readings/latest")
    suspend fun getLatestBpReading(): Response<ApiResponse<BloodPressureReadingData?>>

    @POST("api/bp/readings")
    suspend fun addBpReading(@Body body: AddBpReadingRequest): Response<ApiResponse<BloodPressureReadingData>>

    @DELETE("api/bp/readings/{id}")
    suspend fun deleteBpReading(@Path("id") id: Long): Response<ApiResponse<Unit>>

    @GET("api/bp/stats")
    suspend fun getBpStats(
        @Query("year") year: Int,
        @Query("month") month: Int
    ): Response<ApiResponse<BpStatsData>>

    @GET("api/bp/sugar/readings")
    suspend fun getBsReadings(): Response<ApiResponse<List<BloodSugarReadingData>>>

    @GET("api/bp/sugar/readings/latest")
    suspend fun getLatestBsReading(): Response<ApiResponse<BloodSugarReadingData?>>

    @POST("api/bp/sugar/readings")
    suspend fun addBsReading(@Body body: AddBsReadingRequest): Response<ApiResponse<BloodSugarReadingData>>

    @DELETE("api/bp/sugar/readings/{id}")
    suspend fun deleteBsReading(@Path("id") id: Long): Response<ApiResponse<Unit>>

    @GET("api/bp/sugar/stats")
    suspend fun getBsStats(
        @Query("year") year: Int,
        @Query("month") month: Int
    ): Response<ApiResponse<BsStatsData>>


@GET("api/users/by-email")
suspend fun getUserByEmail(
    @Query("email") email: String
): Response<ApiResponse<UserResponse>>

@PUT("api/users/{id}/onboarding")
suspend fun updateUserOnboarding(
    @Path("id") userId: Int,
    @Body request: RegisterUserRequest
): Response<ApiResponse<UserResponse>>

    // ── Body Scan ─────────────────────────────────────────────────────────────
    @GET("api/bodyscan/records")
    suspend fun getBodyScanRecords(): Response<ApiResponse<List<BodyScanRecordData>>>

    @GET("api/bodyscan/records/latest")
    suspend fun getLatestBodyScanRecord(): Response<ApiResponse<BodyScanRecordData?>>

    @GET("api/bodyscan/records/today")
    suspend fun getTodayBodyScanRecords(): Response<ApiResponse<TodayBodyScanData>>

    @POST("api/bodyscan/records")
    suspend fun saveBodyScanRecord(@Body body: AddBodyScanRequest): Response<ApiResponse<BodyScanRecordData>>

    @DELETE("api/bodyscan/records/{timestamp}")
    suspend fun deleteBodyScanRecord(@Path("timestamp") timestamp: Long): Response<ApiResponse<Unit>>

    @GET("api/bodyscan/stats")
    suspend fun getBodyScanStats(): Response<ApiResponse<BodyScanStatsData>>
    
}
