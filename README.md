# Smart Cafe
Taofeeq Gbadamosi (P2897777), De Montfort University

Android app for the CTEC3703 Mobile App Development and Big Data programming coursework. Individual submission, Customer role, with the café/admin side simulated.

Language: Kotlin
UI: XML layouts + ViewBinding, Material 3 components
Architecture: MVVM-style layering (Activities/Fragments → ViewModels → Repositories → SQLite)
Storage: SQLite via SQLiteOpenHelper (local, on-device)
Advanced integration: context-aware recommendations combining basket analysis over all orders, personal order history, time of day, and live weather from the Open-Meteo REST API
Min SDK 26, target SDK 35
Running
Open the folder in Android Studio and let Gradle sync.
Run the app configuration on an emulator (API 26+) or device.
Register a new account, browse the menu, add items, place an order.
Orders move Pending → Preparing → Ready → Collected automatically (see Account → Café simulator), or manually from Account → Open staff test console. A notification fires on each change.
Tests
gradlew testDebugUnitTest            # JVM unit tests (validation, hashing, status rules, recommendations, weather parsing, analytics)
gradlew connectedDebugAndroidTest    # SQLite repository tests on an emulator/device
Project documents
File	Purpose
docs/ARCHITECTURE.md	Layers, database schema, key design decisions
docs/BACKLOG.md	Product backlog mapped to brief requirements
docs/CHANGELOG.md	Change requests (CR-xx) traced to commits, development log
docs/CLIENT_MEETINGS.md	Lab tutor (client) meeting records
docs/TEST_PLAN.md	Test plan, test cases and results
docs/GENAI_LOG.md	Running log for the GenAI Usage Declaration
The report lives in OneDrive > CTEC3703 > Programming Spec.
