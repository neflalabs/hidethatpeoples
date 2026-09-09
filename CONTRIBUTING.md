# Contributing to HideThatPeoples

We welcome contributions, bug fixes, and feature improvements!

## 🛠️ Development Setup
1. **Prerequisites**:
   - Android Studio Ladybug or later / IntelliJ IDEA
   - JDK 17
   - Android SDK 35 / 36

2. **Clone & Build**:
   ```bash
   git clone https://github.com/neflalabs/hidethatpeoples.git
   cd hidethatpeoples
   ./gradlew assembleDebug
   ```

3. **Run Unit Tests**:
   ```bash
   ./gradlew test
   ```

## 📐 Code Style & Guidelines
- Follow official Kotlin coding conventions.
- Keep UI strictly in Jetpack Compose Material 3.
- Maintain architecture boundaries (`privilege/`, `data/`, `ui/`, `worker/`, `tile/`).
- Verify that `./gradlew test assembleDebug` passes before submitting a pull request.
