# SETTLE — Splitwise Architecture & Complete Codebase Guide

**SETTLE** is a modern, offline-first Android application designed to track shared expenses, split bills with customizable algorithms, minimize multi-party debts, and settle seamlessly with friends and groups via UPI and Razorpay.

---

## Table of Contents
1. [Tech Stack & Architecture](#tech-stack--architecture)
2. [Complete Project File Tree](#complete-project-file-tree)
3. [Component-by-Component Code Contribution Guide](#component-by-component-code-contribution-guide)
   - [1. Root Configuration & Build System](#1-root-configuration--build-system)
   - [2. App Entry, Lifecycle & Manifest](#2-app-entry-lifecycle--manifest)
   - [3. Dependency Injection Layer (`di/`)](#3-dependency-injection-layer-di)
   - [4. Data Layer (`data/`)](#4-data-layer-data)
     - [Local Room Persistence (`data/local/`)](#local-room-persistence-datalocal)
     - [Remote Firestore Models (`data/remote/model/`)](#remote-firestore-models-dataremotemodel)
     - [Repository Implementations (`data/repository/`)](#repository-implementations-datarepository)
   - [5. Domain Layer (`domain/`)](#5-domain-layer-domain)
     - [Business Entities (`domain/model/`)](#business-entities-domainmodel)
     - [Repository Contracts (`domain/repository/`)](#repository-contracts-domainrepository)
     - [Algorithms & Use Cases (`domain/usecase/`)](#algorithms--use-cases-domainusecase)
     - [Payment Integrations (`domain/payment/`)](#payment-integrations-domainpayment)
     - [Utilities & Validators (`domain/util/`)](#utilities--validators-domainutil)
   - [6. Presentation Layer (`presentation/`)](#6-presentation-layer-presentation)
     - [Design System & Theme (`presentation/theme/`)](#design-system--theme-presentationtheme)
     - [Shared UI Components (`presentation/components/`)](#shared-ui-components-presentationcomponents)
     - [Navigation Graph (`presentation/navigation/`)](#navigation-graph-presentationnavigation)
     - [Feature Screens & ViewModels](#feature-screens--viewmodels)
   - [7. XML Resources (`res/`)](#7-xml-resources-res)
   - [8. Test Suites (`app/src/test/`)](#8-test-suites-appsrctest)
   - [9. Cloud Backend & Security Rules](#9-cloud-backend--security-rules)
4. [Getting Started & Local Setup](#getting-started--local-setup)

---

## Tech Stack & Architecture

- **Platform & Language**: Android (Kotlin 2.0+), Coroutines & StateFlow
- **UI Framework**: Jetpack Compose (Material 3)
- **Architecture**: MVVM with Clean Architecture (Domain, Data, and Presentation separation)
- **Dependency Injection**: Dagger Hilt
- **Local Database**: Room with TypeConverters & SQLite
- **Cloud Backend**:
  - Firebase Authentication (Email/Password)
  - Cloud Firestore (Real-time NoSQL snapshot listeners)
  - Firebase Cloud Functions (TypeScript/Node.js)
  - Firebase Storage (Receipt & avatar attachments)
- **Payment Gateways**:
  - Razorpay Checkout SDK
  - UPI Intent Deep-Linking (GPay, PhonePe, Paytm, BHIM)

```
┌────────────────────────────────────────────────────────┐
│           Presentation Layer (Jetpack Compose)         │
│     Screens (HomeScreen, AddExpense, GroupDetails)     │
│        ViewModels (StateFlow, UI Events, Hilt)         │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│                       Domain Layer                     │
│    UseCases: SettlementEngine (Min-Cash-Flow Graph)    │
│    Payment: RazorpayCheckoutManager, UpiPayment        │
│    Repository Interfaces & Pure Business Models        │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│                        Data Layer                      │
│   Repository Impls (Offline-First Single Source Truth) │
│   Local: Room DB, DAOs, Entities, Encrypted Session    │
│   Remote: Firestore NoSQL, Cloud Functions, Storage    │
└────────────────────────────────────────────────────────┘
```

---

## Complete Project File Tree

```text
Splitwsie/
│
├── build.gradle.kts                            # Root build script defining plugins (Kotlin, Android, Hilt, KSP)
├── settings.gradle.kts                         # Dependency repository management & project modules
├── gradle.properties                           # JVM allocation flags, AndroidX & Jetifier options
├── gradlew                                     # Unix Gradle wrapper shell script
├── gradlew.bat                                 # Windows Gradle wrapper batch script
├── .gitignore                                  # Git exclusion rules (.gradle, build, .kotlin, .idea)
├── firebase.json                               # Firebase CLI deployment mapping (functions, firestore, storage)
├── firestore.rules                             # Production security rules for Firestore NoSQL database
├── storage.rules                               # Storage access policies for receipts and profile images
│
├── gradle/
│   ├── libs.versions.toml                      # Gradle Version Catalog (centralized library versions)
│   ├── gradle-daemon-jvm.properties            # JDK version configuration for Gradle Daemon
│   └── wrapper/
│       ├── gradle-wrapper.jar                  # Binary Gradle wrapper archive
│       └── gradle-wrapper.properties           # Wrapper distribution URL & Gradle runtime version
│
├── functions/                                  # Firebase Cloud Functions (Backend Microservices)
│   ├── package.json                            # Node.js dependencies (firebase-admin, firebase-functions)
│   ├── package-lock.json                       # Locked NPM dependency graph
│   ├── tsconfig.json                           # TypeScript compiler configurations
│   └── src/
│       └── index.ts                            # Cloud Functions (invite code redemption, group memberships)
│
└── app/                                        # Main Android Application Module
    ├── build.gradle.kts                        # Module build file (dependencies, SDK versions, compose compiler)
    ├── proguard-rules.pro                      # R8 code shrinking and ProGuard obfuscation rules
    ├── google-services.json                    # Firebase project credentials & API keys
    │
    ├── src/
    │   ├── main/
    │   │   ├── AndroidManifest.xml             # Android system manifest (permissions, activities, application)
    │   │   │
    │   │   ├── keepRules/
    │   │   │   └── rules.keep                  # Resource keeper rules for asset compilation
    │   │   │
    │   │   ├── res/                            # Android XML Resources
    │   │   │   ├── drawable/
    │   │   │   │   ├── ic_launcher_background.xml  # Vector background for adaptive launcher icon
    │   │   │   │   └── ic_launcher_foreground.xml  # Vector foreground glyph for launcher icon
    │   │   │   ├── mipmap-anydpi-v26/
    │   │   │   │   ├── ic_launcher.xml         # Adaptive square app icon definition
    │   │   │   │   └── ic_launcher_round.xml   # Adaptive round app icon definition
    │   │   │   ├── values/
    │   │   │   │   ├── colors.xml              # Base XML color palettes and splash branding
    │   │   │   │   ├── strings.xml             # Localized string resources (app name, base labels)
    │   │   │   │   └── themes.xml              # System window style & splash screen theme
    │   │   │   └── xml/
    │   │   │       ├── backup_rules.xml        # Cloud backup rules filtering sensitive data
    │   │   │       └── data_extraction_rules.xml # Android 12+ device-to-device migration rules
    │   │   │
    │   │   └── java/com/example/splitwsie/
    │   │       ├── SettleApplication.kt        # Hilt-annotated Application class (app initialization)
    │   │       ├── MainActivity.kt             # Single activity hosting Jetpack Compose NavGraph
    │   │       ├── AppEntryViewModel.kt        # Startup routing (evaluates active session & profile)
    │   │       │
    │   │       ├── di/                         # Dependency Injection Modules (Dagger Hilt)
    │   │       │   ├── DatabaseModule.kt       # Provides Room Database, DAOs, and SessionManager
    │   │       │   ├── FirebaseModule.kt       # Provides FirebaseAuth, Firestore, Storage, Functions
    │   │       │   ├── PaymentModule.kt        # Provides Razorpay managers and payment helpers
    │   │       │   └── RepositoryModule.kt     # Binds domain repository interfaces to implementations
    │   │       │
    │   │       ├── data/                       # Data Layer (Local & Remote Data Sources)
    │   │       │   ├── local/                  # Room SQLite Database & Local Session
    │   │       │   │   ├── SettleDatabase.kt   # Room Database class with table definitions
    │   │       │   │   ├── Converters.kt       # TypeConverters for Lists, Dates, Enums, BigDecimals
    │   │       │   │   ├── SessionManager.kt   # Encrypted SharedPreferences for user auth tokens
    │   │       │   │   ├── dao/                # Data Access Objects (SQL queries)
    │   │       │   │   │   ├── UserDao.kt      # Query and upsert local user records
    │   │       │   │   │   ├── GroupDao.kt     # Query groups, memberships, and categories
    │   │       │   │   │   ├── ExpenseDao.kt   # Expense queries, category filters, soft deletes
    │   │       │   │   │   ├── SettlementDao.kt# Settlement and payment history queries
    │   │       │   │   │   ├── FriendDao.kt    # 1-on-1 friendship balances and relations
    │   │       │   │   │   └── CommentDao.kt   # Expense discussion comments
    │   │       │   │   └── entity/             # Room Database Tables (@Entity)
    │   │       │   │       ├── UserEntity.kt   # Local user profile table
    │   │       │   │       ├── GroupEntity.kt  # Shared group table
    │   │       │   │       ├── MemberEntity.kt # Group-to-User cross-reference table
    │   │       │   │       ├── ExpenseEntity.kt# Bill/expense table with amounts and dates
    │   │       │   │       ├── SplitEntity.kt  # Individual split breakdown table
    │   │       │   │       ├── SettlementEntity.kt # Settle-up payment record table
    │   │       │   │       ├── FriendEntity.kt # Direct 1-on-1 friend relationship table
    │   │       │   │       └── CommentEntity.kt# Comment thread table on expenses
    │   │       │   │
    │   │       │   ├── remote/model/           # Firestore Cloud Data Transfer Objects (DTOs)
    │   │       │   │   ├── FirestoreUser.kt    # DTO mapping /users/{id}
    │   │       │   │   ├── FirestoreGroup.kt   # DTO mapping /groups/{id}
    │   │       │   │   ├── FirestoreGroupMember.kt # DTO for group member details
    │   │       │   │   ├── FirestoreExpense.kt # DTO mapping /groups/{id}/expenses/{id}
    │   │       │   │   ├── FirestoreSplit.kt   # DTO mapping expense split breakdown
    │   │       │   │   └── FirestoreSettlement.kt # DTO mapping /groups/{id}/settlements/{id}
    │   │       │   │
    │   │       │   └── repository/             # Concrete Repository Implementations
    │   │       │       ├── FirebaseAuthRepositoryImpl.kt # Firebase Auth sign in, sign up, sign out
    │   │       │       ├── UserRepositoryImpl.kt   # User profile syncing (Firestore <-> Room)
    │   │       │       ├── GroupRepositoryImpl.kt  # Real-time group listener and invite code logic
    │   │       │       ├── ExpenseRepositoryImpl.kt# Offline-first expense syncing & persistence
    │   │       │       ├── SettlementRepositoryImpl.kt # Debt settlement synchronization
    │   │       │       ├── FriendRepositoryImpl.kt # Friend requests and direct debt tracking
    │   │       │       └── FileRepositoryImpl.kt   # Firebase Storage upload for receipts and avatars
    │   │       │
    │   │       ├── domain/                     # Domain Layer (Pure Business Logic & Models)
    │   │       │   ├── model/                  # Domain Entities
    │   │       │   │   ├── User.kt             # Pure user model
    │   │       │   │   ├── Group.kt            # Pure group model
    │   │       │   │   ├── Expense.kt          # Bill and expense data model
    │   │       │   │   ├── Split.kt            # Split calculation details
    │   │       │   │   ├── Settlement.kt       # Settle-up payment model
    │   │       │   │   ├── Debt.kt             # Directed graph edge: Debtor -> Creditor
    │   │       │   │   ├── Payment.kt          # Payment transaction detail model
    │   │       │   │   ├── ActivityItem.kt     # Audit feed entry (expense added, debt settled)
    │   │       │   │   ├── ExpenseCategory.kt  # Enum: Food, Travel, Utilities, Life, etc.
    │   │       │   │   ├── GroupCategory.kt    # Enum: Trip, Home, Couple, Other
    │   │       │   │   ├── RepeatInterval.kt   # Enum: Daily, Weekly, Monthly, Yearly
    │   │       │   │   ├── ExpenseFilter.kt    # Filter parameters for expense queries
    │   │       │   │   ├── FriendRelationship.kt # 1-on-1 friendship model
    │   │       │   │   ├── FriendStatus.kt     # Enum: Pending, Accepted, Blocked
    │   │       │   │   ├── CloudSettlement.kt  # Cloud-synced settlement entity
    │   │       │   │   └── Comment.kt          # Expense comment entity
    │   │       │   │
    │   │       │   ├── repository/             # Domain Repository Interfaces
    │   │       │   │   ├── AuthRepository.kt   # Authentication contract
    │   │       │   │   ├── UserRepository.kt   # User profile contract
    │   │       │   │   ├── GroupRepository.kt  # Group management contract
    │   │       │   │   ├── ExpenseRepository.kt# Expense management contract
    │   │       │   │   ├── SettlementRepository.kt # Settlement contract
    │   │       │   │   ├── FriendRepository.kt # Direct friend interaction contract
    │   │       │   │   └── FileRepository.kt   # File upload contract
    │   │       │   │
    │   │       │   ├── usecase/                # Core Business Logic & Algorithms
    │   │       │   │   ├── SettlementEngine.kt # Debt Simplification Min-Cash-Flow Algorithm
    │   │       │   │   ├── BalanceCalculator.kt# Net member balance calculator
    │   │       │   │   └── RecurrenceEvaluator.kt # Recurring billing interval scheduler
    │   │       │   │
    │   │       │   ├── payment/                # Payment Gateway Integrations
    │   │       │   │   ├── RazorpayCheckoutManager.kt # Razorpay SDK lifecycle bridge
    │   │       │   │   ├── UpiPayment.kt       # Deep-link URI generator for UPI apps
    │   │       │   │   └── PaymentProvider.kt  # Common interface for payment providers
    │   │       │   │
    │   │       │   └── util/                   # Domain Utilities
    │   │       │       ├── Money.kt            # High-precision currency formatting and rounding
    │   │       │       ├── Validators.kt       # Input validation (email, password, split sum)
    │   │       │       └── InviteCodes.kt      # Alphanumeric invite code generation
    │   │       │
    │   │       └── presentation/               # Presentation Layer (Jetpack Compose UI)
    │   │           ├── navigation/
    │   │           │   ├── Screen.kt           # Sealed class defining routes & arguments
    │   │           │   └── NavGraph.kt         # Jetpack Compose navigation controller & graph
    │   │           ├── theme/
    │   │           │   ├── Color.kt            # Tailored HSL color palette & surface tones
    │   │           │   ├── Theme.kt            # SettleTheme Material3 dynamic color configuration
    │   │           │   └── Type.kt             # Typography scale (Inter / Roboto font families)
    │   │           ├── components/
    │   │           │   ├── SettleComponents.kt # Common buttons, cards, balance chips, badges
    │   │           │   ├── ActivityFeedItem.kt # Render component for activity stream logs
    │   │           │   ├── Charts.kt           # Canvas donut charts and category graphs
    │   │           │   └── FilterBottomSheet.kt# Modal sheet for filtering expenses
    │   │           ├── util/
    │   │           │   └── ImageUtils.kt       # Bitmap compression & URI conversion for receipts
    │   │           │
    │   │           ├── welcome/
    │   │           │   └── WelcomeScreen.kt    # Onboarding carousel introducing features
    │   │           ├── auth/
    │   │           │   ├── AuthScreen.kt       # Login and sign-up tabs with validation
    │   │           │   └── AuthViewModel.kt    # Handles auth state and login flows
    │   │           ├── home/
    │   │           │   ├── HomeScreen.kt       # Dashboard: total balance, groups, activity feed
    │   │           │   └── HomeViewModel.kt    # Aggregates overall balances & recent streams
    │   │           ├── group/
    │   │           │   ├── GroupCreationScreen.kt # Form to create group or enter invite code
    │   │           │   ├── GroupDetailsScreen.kt  # Group overview: expenses, balances, settle up
    │   │           │   ├── GroupSettingsScreen.kt # Group members, invite sharing, leave group
    │   │           │   └── GroupViewModel.kt      # Manages group expenses, balances & members
    │   │           ├── expense/
    │   │           │   ├── AddExpenseScreen.kt    # Add expense: Equal, Exact, %, Shares, Receipt
    │   │           │   ├── ExpenseDetailsScreen.kt# Detailed bill breakdown, receipt, comments
    │   │           │   ├── ExpenseDetailsViewModel.kt # Comment posting & delete handlers
    │   │           │   └── ExpenseViewModel.kt    # Dynamic split calculations & validation
    │   │           ├── friends/
    │   │           │   ├── FriendsScreen.kt       # 1-on-1 friend list and individual balances
    │   │           │   └── FriendsViewModel.kt    # Manages friend invitations and settlements
    │   │           ├── settle/
    │   │           │   ├── SettleUpScreen.kt      # Settle debts: choose recipient and amount
    │   │           │   ├── SettleUpViewModel.kt   # Executes debt settlement transactions
    │   │           │   └── PaymentHistoryScreen.kt# History log of all settled debts
    │   │           ├── payment/
    │   │           │   ├── PaymentScreen.kt       # Choose method: UPI intent or Razorpay
    │   │           │   ├── PaymentViewModel.kt    # Coordinates payment verification callbacks
    │   │           │   └── RazorpayCheckoutActivity.kt # Transparent Activity handling SDK callbacks
    │   │           ├── analytics/
    │   │           │   ├── AnalyticsScreen.kt     # Spending visualizations and category charts
    │   │           │   └── AnalyticsViewModel.kt  # Monthly spending queries & trends
    │   │           └── profile/
    │   │               ├── ProfileScreen.kt       # User details, avatar upload, logout
    │   │               └── ProfileViewModel.kt    # Profile updates & session termination
    │   │
    │   └── test/                               # Local Unit Tests
    │       ├── ExampleUnitTest.kt              # Sanity verification test
    │       ├── data/repository/
    │       │   └── OfflineSyncTest.kt          # Tests Room offline caching & sync fallbacks
    │       └── domain/
    │           ├── usecase/
    │           │   ├── SettlementEngineTest.kt # Validates debt simplification graph algorithm
    │           │   ├── BalanceCalculatorTest.kt# Tests net balance calculations across splits
    │           │   ├── ExpenseSplitTest.kt     # Tests Equal, Exact, Percentage, Shares logic
    │           │   └── ExpenseReceiptTest.kt   # Validates receipt parsing and validation
    │           └── util/
    │               └── MoneyTest.kt            # Tests currency precision & formatting
```

---

## Component-by-Component Code Contribution Guide

### 1. Root Configuration & Build System

| File | Purpose & Code Contribution |
| :--- | :--- |
| **`build.gradle.kts`** | Configures top-level build logic and registers plugins: Android Application, Kotlin Android, Kotlin Compose, Google Services, Hilt, and KSP (Kotlin Symbol Processing). |
| **`settings.gradle.kts`** | Defines project name (`Splitwsie`) and manages dependency repositories (`google()`, `mavenCentral()`). |
| **`gradle/libs.versions.toml`** | Central Version Catalog for declaring versions and dependencies for Room, Compose, Hilt, Firebase, Retrofit, and Razorpay. |
| **`firebase.json`** | Maps Firebase configuration for deploying Firestore rules, Storage rules, and TypeScript Cloud Functions via Firebase CLI. |
| **`firestore.rules`** | Production security rules: ensures users only read and write groups they belong to, prevents arbitrary modification of financial records, and enforces schema integrity. |
| **`storage.rules`** | Security rules governing Firebase Cloud Storage, validating file size (< 5MB) and MIME type (images only) for expense receipts and user avatars. |

---

### 2. App Entry, Lifecycle & Manifest

| File | Purpose & Code Contribution |
| :--- | :--- |
| **`AndroidManifest.xml`** | Declares permissions (`INTERNET`, `ACCESS_NETWORK_STATE`, `CAMERA`), registers `SettleApplication`, sets `MainActivity` as the launcher, and registers `RazorpayCheckoutActivity`. |
| **`SettleApplication.kt`** | Annotated with `@HiltAndroidApp`. Acts as the global application context, triggers dependency injection graph generation, and configures Firebase and Room singletons. |
| **`MainActivity.kt`** | Annotated with `@AndroidEntryPoint`. Sets up Jetpack Compose content view, wraps the application inside `SettleTheme`, and initializes `NavGraph`. |
| **`AppEntryViewModel.kt`** | Handles cold startup routing: checks `SessionManager` to decide whether to direct the user to `WelcomeScreen`, `AuthScreen`, or `HomeScreen`. |

---

### 3. Dependency Injection Layer (`di/`)

| File | Purpose & Code Contribution |
| :--- | :--- |
| **`DatabaseModule.kt`** | Provides Room database instance `SettleDatabase`, singleton DAOs (`UserDao`, `GroupDao`, `ExpenseDao`, `SettlementDao`, `FriendDao`, `CommentDao`), and `SessionManager`. |
| **`FirebaseModule.kt`** | Provides singleton instances of Firebase services: `FirebaseAuth`, `FirebaseFirestore`, `FirebaseStorage`, and `FirebaseFunctions`. |
| **`PaymentModule.kt`** | Injects payment helpers and providers including Razorpay manager instances and UPI intent launchers. |
| **`RepositoryModule.kt`** | Uses `@Binds` to bind domain repository interfaces (`AuthRepository`, `ExpenseRepository`, etc.) to their concrete data implementations. |

---

### 4. Data Layer (`data/`)

#### Local Room Persistence (`data/local/`)
- **`SettleDatabase.kt`**: Room database definition (`@Database(entities = [...], version = 1)`). Manages SQLite migrations and exposes abstract DAO getters.
- **`Converters.kt`**: Converts Kotlin complex types (such as `Date`, `List<String>`, `Map`, and `BigDecimal`) into SQLite-storable primitives.
- **`SessionManager.kt`**: Secure local storage for saving active user ID, email, and authentication tokens to keep users logged in across launches.
- **`dao/UserDao.kt`**: Queries and caches user profiles.
- **`dao/GroupDao.kt`**: Reads and writes groups and member links with Flow observables.
- **`dao/ExpenseDao.kt`**: Full CRUD operations for expenses, category queries, and group-level totals.
- **`dao/SettlementDao.kt`**: Inserts and retrieves debt settlement records.
- **`dao/FriendDao.kt`**: Manages direct 1-on-1 friend entities and friend balances.
- **`dao/CommentDao.kt`**: Manages discussion comments attached to individual expenses.
- **`entity/`**: Room `@Entity` data classes defining relational tables: `UserEntity`, `GroupEntity`, `MemberEntity`, `ExpenseEntity`, `SplitEntity`, `SettlementEntity`, `FriendEntity`, and `CommentEntity`.

#### Remote Firestore Models (`data/remote/model/`)
- **`FirestoreUser.kt`**: DTO mapping user documents stored at `/users/{userId}`.
- **`FirestoreGroup.kt`**: DTO mapping group documents at `/groups/{groupId}`.
- **`FirestoreGroupMember.kt`**: Embedded DTO representing group member metadata (role, joined timestamp).
- **`FirestoreExpense.kt`**: DTO mapping expense documents at `/groups/{groupId}/expenses/{expenseId}`.
- **`FirestoreSplit.kt`**: DTO serializing member split calculations on Cloud Firestore.
- **`FirestoreSettlement.kt`**: DTO mapping settlement transactions at `/groups/{groupId}/settlements/{settlementId}`.

#### Repository Implementations (`data/repository/`)
- **`FirebaseAuthRepositoryImpl.kt`**: Bridges Firebase Auth SDK: handles user login, registration, password resets, and session emission.
- **`UserRepositoryImpl.kt`**: Syncs user profile updates between local Room cache and remote Firestore documents.
- **`GroupRepositoryImpl.kt`**: Handles group creation, listens to real-time group changes using Firestore snapshot listeners, and joins groups via 6-digit invite codes.
- **`ExpenseRepositoryImpl.kt`**: Offline-first repository for expenses: writes to local Room immediately for instant UI responsiveness, then syncs to Firestore in the background.
- **`SettlementRepositoryImpl.kt`**: Records debt settlement transactions in both local Room and remote Firestore.
- **`FriendRepositoryImpl.kt`**: Handles 1-on-1 friendship requests, approvals, and personal balances.
- **`FileRepositoryImpl.kt`**: Compresses and uploads expense bill receipts and user profile images to Firebase Storage.

---

### 5. Domain Layer (`domain/`)

#### Business Entities (`domain/model/`)
- **`User.kt`**, **`Group.kt`**, **`Expense.kt`**, **`Split.kt`**, **`Settlement.kt`**, **`Debt.kt`**, **`Payment.kt`**: Pure business data classes free of any Android framework or database annotations.
- **`ExpenseCategory.kt`**: Enum categorizing spending: Food & Drinks, Shopping, Housing, Transportation, Entertainment, Utilities, General.
- **`GroupCategory.kt`**: Enum categorizing groups: Trip, Home, Couple, Project, Other.
- **`RepeatInterval.kt`**: Recurring interval definitions: None, Daily, Weekly, Monthly, Yearly.
- **`ActivityItem.kt`**: Audit event model summarizing recent transactions for the activity feed.

#### Use Cases & Core Algorithms (`domain/usecase/`)
- **`SettlementEngine.kt`**:
  > **The Heart of Debt Simplification:** Implements a min-cash-flow greedy algorithm. It computes net balance vectors for every participant and matches the biggest debtor with the biggest creditor iteratively. This reduces an $N$-person tangled web of debts down to a minimum number of transactions ($N-1$ or fewer).
- **`BalanceCalculator.kt`**: Processes a list of expenses and splits to compute exact net balances (`balance > 0` = owed money, `balance < 0` = owes money) for all participants.
- **`RecurrenceEvaluator.kt`**: Evaluates recurring expense schedules and determines when new expense entries must be generated.

#### Payment Integrations (`domain/payment/`)
- **`RazorpayCheckoutManager.kt`**: Interfaces with the Razorpay Checkout SDK: prepares order payloads, opens the payment bottom sheet, and processes payment success/failure callbacks.
- **`UpiPayment.kt`**: Constructs standard NPCI UPI intent URIs (`upi://pay?pa=...&pn=...&am=...&cu=INR`) to launch installed UPI apps (GPay, PhonePe, Paytm).
- **`PaymentProvider.kt`**: Abstract interface allowing the app to swap or support multiple payment gateways.

#### Utilities (`domain/util/`)
- **`Money.kt`**: Monetary math utility preventing floating-point inaccuracies, handles rounding, and formats currency strings (INR `₹`, USD `$`).
- **`Validators.kt`**: Input sanitization rules for email formats, password strength, and expense split sum validation.
- **`InviteCodes.kt`**: Generates and parses secure, non-ambiguous 6-character alphanumeric group invite codes.

---

### 6. Presentation Layer (`presentation/`)

#### Design System & Theme (`presentation/theme/`)
- **`Color.kt`**: Color definitions: Emerald green (settled), Coral red (debt), Deep slate surfaces, and Accent amber.
- **`Theme.kt`**: Material 3 `SettleTheme` with dark/light mode switching and dynamic system bar colors.
- **`Type.kt`**: Material typography scale using modern sans-serif typography.

#### Shared Components (`presentation/components/`)
- **`SettleComponents.kt`**: Reusable Compose elements: `PrimaryButton`, `BalanceCard`, `SettleTopBar`, `CurrencyTextField`, `AvatarBubble`, and `LoadingIndicator`.
- **`ActivityFeedItem.kt`**: Feed item card showing who paid for what with relative timestamps ("2 hours ago").
- **`Charts.kt`**: Custom Canvas-based donut chart displaying percentage breakdowns of spending by category.
- **`FilterBottomSheet.kt`**: Modal bottom sheet allowing users to filter transactions by date, category, and members.
- **`ImageUtils.kt`**: Compresses camera/gallery photos before upload to conserve bandwidth.

#### Navigation (`presentation/navigation/`)
- **`Screen.kt`**: Sealed class defining all route destinations (`Home`, `Auth`, `AddExpense`, `GroupDetails`, `Payment`, etc.) with arguments.
- **`NavGraph.kt`**: Jetpack Compose `NavHost` connecting screens and managing transitions and backstack.

#### Feature Screens & ViewModels
| Feature Screen | ViewModel | Code Contribution |
| :--- | :--- | :--- |
| **`WelcomeScreen.kt`** | `AppEntryViewModel` | Modern onboarding carousel introducing key features with "Get Started" call-to-action. |
| **`AuthScreen.kt`** | `AuthViewModel.kt` | Animated tab interface for Login and Registration with email/password validation and error messaging. |
| **`HomeScreen.kt`** | `HomeViewModel.kt` | Main dashboard displaying net balance card ("You owe ₹X", "You are owed ₹Y"), quick group cards, and recent activity feed. |
| **`GroupCreationScreen.kt`** | `GroupViewModel.kt` | Screen to create a new group or join an existing group using an invite code. |
| **`GroupDetailsScreen.kt`** | `GroupViewModel.kt` | Displays group expense list, member balances, debt simplification settlement recommendations, and invite code sharing. |
| **`GroupSettingsScreen.kt`** | `GroupViewModel.kt` | Manage group members, toggle permissions, edit group metadata, or leave the group. |
| **`AddExpenseScreen.kt`** | `ExpenseViewModel.kt` | Dynamic bill splitting screen supporting **Equal**, **Exact Amount**, **Percentage**, and **Shares** split modes, along with camera/gallery receipt attachment. |
| **`ExpenseDetailsScreen.kt`**| `ExpenseDetailsViewModel.kt`| Detailed breakdown of a specific bill showing exact amounts per member, receipt preview, and real-time comment thread. |
| **`FriendsScreen.kt`** | `FriendsViewModel.kt` | 1-on-1 friend management showing individual balances and direct settle-up buttons. |
| **`SettleUpScreen.kt`** | `SettleUpViewModel.kt` | Settlement flow: select payer, recipient, amount, and proceed to payment gateway. |
| **`PaymentHistoryScreen.kt`**| `SettleUpViewModel.kt` | Chronological ledger of all settled debts with payment method badges (UPI / Razorpay / Cash). |
| **`PaymentScreen.kt`** | `PaymentViewModel.kt` | Payment choice screen: launch Indian UPI apps directly or pay via Razorpay card/netbanking. |
| **`RazorpayCheckoutActivity.kt`** | `PaymentViewModel.kt` | Headless Activity that intercepts Razorpay SDK result callbacks (`PaymentResultWithDataListener`). |
| **`AnalyticsScreen.kt`** | `AnalyticsViewModel.kt` | Personal finance analytics showing category spending breakdowns, monthly spending graphs, and trends. |
| **`ProfileScreen.kt`** | `ProfileViewModel.kt` | User profile management, avatar picture upload, currency preference, and secure sign-out. |

---

### 7. XML Resources (`res/`)

| File | Purpose & Code Contribution |
| :--- | :--- |
| **`values/strings.xml`** | Declares app name (`SETTLE`) and translatable text resources. |
| **`values/colors.xml`** | System color definitions used by Android window decor and splash screen. |
| **`values/themes.xml`** | Defines `Theme.Settle` system theme, splash screen background, and window title bar settings. |
| **`drawable/ic_launcher_background.xml`** | XML vector representing the gradient background for the adaptive app icon. |
| **`drawable/ic_launcher_foreground.xml`** | XML vector representing the currency/settlement glyph on the app icon. |
| **`mipmap-anydpi-v26/ic_launcher.xml`** | Adaptive square launcher icon configuration for Android 8.0+ devices. |
| **`mipmap-anydpi-v26/ic_launcher_round.xml`**| Adaptive round launcher icon configuration. |
| **`xml/backup_rules.xml`** | Directs Android Auto-Backup to exclude encrypted auth tokens and local cache files. |
| **`xml/data_extraction_rules.xml`** | Android 12+ cloud and device-to-device migration rules preserving privacy. |

---

### 8. Test Suites (`app/src/test/`)

| Test File | Verification & Testing Coverage |
| :--- | :--- |
| **`SettlementEngineTest.kt`** | Validates the debt simplification algorithm across complex cycles (e.g., A owes B, B owes C, C owes A) and verifies it produces the minimum possible transactions. |
| **`BalanceCalculatorTest.kt`** | Tests net balance calculations across multiple currencies, uneven splits, and zero-sum balances. |
| **`ExpenseSplitTest.kt`** | Verifies accuracy of Equal, Exact, Percentage, and Share split mathematics. |
| **`OfflineSyncTest.kt`** | Verifies that local Room database operations succeed and maintain state when network connectivity is lost. |
| **`ExpenseReceiptTest.kt`** | Tests receipt image validation, mime-type verification, and size boundary checks. |
| **`MoneyTest.kt`** | Validates precision math (preventing rounding errors like `0.1 + 0.2 != 0.3`) and currency formatting. |

---

### 9. Cloud Backend & Security Rules

| Component | Purpose & Code Contribution |
| :--- | :--- |
| **`functions/src/index.ts`** | Firebase Cloud Functions: atomic group joining via 6-digit invite code, automated member notifications, and data integrity checks. |
| **`firestore.rules`** | Server-side security: ensures users cannot read or write data from groups they have not joined; protects user profiles from unauthorized writes. |
| **`storage.rules`** | Ensures uploaded receipts and avatars are under 5MB and verified image types. |

---

## Getting Started & Local Setup

### 1. Prerequisites
- Android Studio (Ladybug / Hedgehog or newer)
- JDK 17 or JDK 21
- Node.js (v18+) for Cloud Functions

### 2. Firebase Configuration
1. Create a project in the [Firebase Console](https://console.firebase.google.com/).
2. Add an Android app with package name `com.example.splitwsie`.
3. Download `google-services.json` and place it in the `app/` directory.
4. Enable **Email/Password** under Authentication.
5. Create a **Cloud Firestore** database.
6. Deploy rules and functions using the Firebase CLI:
   ```bash
   firebase login
   firebase deploy --only firestore:rules,storage,functions
   ```

### 3. Build & Run
Open the project in Android Studio, sync Gradle, and click **Run** (or `Shift + F10`).
