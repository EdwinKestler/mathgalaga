# MathGalaga

![MathGalaga Logo](path/to/logo.png) <!-- Replace with actual logo if available; otherwise, omit or use a placeholder -->

MathGalaga is an open-source Android game that blends the classic arcade shooter *Galaga* with educational math challenges. Designed as a two-player competitive experience, it encourages players to practice multiplication tables while engaging in fast-paced action. The game is optimized for arcade-style setups with physical joysticks (e.g., DragonRise or similar USB controllers), making it ideal for educational kiosks, family game nights, or classroom activities. It runs in full-screen immersive mode on Android devices, supporting landscape orientation for a seamless gaming experience.

The core intention of MathGalaga is to make learning mathematics fun and interactive. By tying gameplay success to solving multiplication problems, it helps reinforce arithmetic skills in an engaging way. Difficulty adapts dynamically based on player performance, ensuring the game remains challenging yet accessible for different age groups and skill levels. The project uses Kotlin for Android development, employing an Entity-Component-System (ECS) architecture for efficient game logic management.

This game is intended for deployment on a Raspberry Pi 4, mounted in an arcade chassis with a 17-inch DELL monitor and a sound amplifier. It supports two USB joysticks for primary input, though gamepads can also be used as an alternative.

As of July 15, 2025, the project is in active development, with recent refactors focusing on modularity, performance optimizations, and joystick calibration for better hardware compatibility.

## Features

- **Two-Player Mode**: Competitive play where each player controls a ship (red for Player 1, blue for Player 2) using joysticks. Shoot aliens by solving math problems correctly.
- **Adaptive Difficulty**: Uses a sliding window of recent answers to adjust problem complexity (e.g., from 1×1 to 50×50). Tracks streaks for bonus points.
- **Math Integration**: Multiplication problems appear in the HUD. Shooting the alien with the correct answer clears it; incorrect shots bounce off.
- **Game Mechanics**:
  - Aliens in formations (top row with numbers, lower rows as distractors).
  - Bullets, explosions, combo texts with floating animations.
  - Lives system with respawning.
  - Levels up to 5, with varying alien patterns and speeds.
- **Input Support**: Joystick calibration for precise control (move left/right for detection, press fire buttons). Touch input is disabled for arcade authenticity.
- **Audio & Visuals**: Sound effects for shooting, hits, and explosions. Pre-rendered starry background, sprite-based rendering for players and aliens.
- **Immersive Kiosk Mode**: Full-screen, hidden system bars, edge-to-edge drawing. Keeps screen on during play.
- **ECS Architecture**: Modular components (Position, Velocity, etc.), systems (Movement, Collision, Rendering), and states (Playing, Calibration, Game Over).
- **Performance**: VSync-aware rendering with Choreographer, delta-time scaling for smooth 60 FPS gameplay.
- **Calibration Screen**: Guides players through joystick setup at startup.

## Recommended Hardware Setup

MathGalaga is specifically designed for a Raspberry Pi 4 running Android (via LineageOS or similar builds), housed in an arcade chassis. The recommended configuration includes:
- **Raspberry Pi 4**: As the core computing unit for running the Android app.
- **17-inch DELL Monitor**: For display, connected via HDMI for crisp visuals in landscape mode.
- **Sound Amplifier**: To enhance audio output from the Raspberry Pi's headphone jack or HDMI audio, ensuring immersive sound effects.
- **Two USB Joysticks**: Primary input devices (e.g., DragonRise models). The game includes calibration for left/right movement and fire buttons.
- **Alternative Input**: Gamepads (e.g., Xbox or PlayStation controllers) can be used if joysticks are unavailable, with similar calibration support.

This setup creates a dedicated arcade machine feel, perfect for educational or entertainment purposes. Ensure proper cooling for the Pi in the chassis to avoid thermal throttling during extended play.

## Installation

### Prerequisites
- Android Studio (latest version recommended, e.g., Jellyfish or later).
- Android SDK with API level 34 (Android 14) or higher for compilation.
- Physical joysticks (optional but recommended; tested with device IDs 9 and 5 for red/blue players).
- Drawable resources (sprites like `player_blue.png`, `alien_square_green.png`) in `res/drawable`.
- Raw audio files (`shoot.wav`, `hit.wav`, `explosion.wav`) in `res/raw`.

### Steps
1. Clone the repository:
   ```
   git clone https://github.com/yourusername/mathgalaga.git
   ```
2. Open the project in Android Studio.
3. Sync Gradle and build the project (ensure `androidx.core:core-ktx` is in dependencies).
4. Add missing resources:
   - Place sprite images in `app/src/main/res/drawable`.
   - Place sound files in `app/src/main/res/raw`.
5. Run on an emulator or physical device (landscape mode enforced). For Raspberry Pi 4, build an APK and install via ADB or sideload.

## Usage

1. **Launch the App**: The game starts in calibration mode. Follow on-screen prompts to set up joysticks (move left/right, press fire).
2. **Gameplay**:
   - Each player sees a multiplication problem (e.g., "3×4") in their HUD.
   - Shoot aliens: Top-row aliens have numbers; hit the one matching your answer to clear it and advance.
   - Lower aliens are distractors—shoot them for points but no math progress.
   - Avoid enemy bullets; lose lives on hits.
   - Clear all top aliens to advance levels (up to 5).
   - Game over on zero lives; win on completing all levels.
   - Press fire button in end states (win/loss) to restart.
3. **Controls**:
   - Joystick: Move ship (with dead-zone debouncing).
   - Fire button: Shoot (with cooldown to prevent spam).
4. **Pause/Resume**: Handled via activity lifecycle; no in-game pause (arcade style).
5. **Debugging**: Enable logs (e.g., `adb logcat | grep MathGalaga`) for performance metrics, input detection.

For educational use, customize math ranges in `Config.kt` or extend `DifficultyManager` for other operations (e.g., addition).

## File Structure

The project follows a modular structure with separated concerns for better maintainability. Latest files as of the refactor:

- **Components.kt**: Defines data classes for ECS components (e.g., `Position`, `Player`, `Bullet`).
- **config.kt**: Central configuration object with settings for screens, fonts, colors, player/alien/bullet properties, difficulty levels, and sprite loading (using direct resource IDs for efficiency).
- **difficulty.kt**: `DifficultyManager` class for adaptive problem generation and `generateAdaptiveProblem` function for multiplication questions.
- **GameView.kt**: Core SurfaceView handling game loop (Choreographer-based), input (joysticks, keys), sounds, and calibration.
- **MainActivity.kt**: Entry point Activity; sets up immersive mode, hosts `GameView`, and manages lifecycle/event dispatching.
- **LevelUtils.kt**: Utility functions for generating alien formation positions based on level.
- **States.kt**: Game state classes (e.g., `PlayingState`, `CalibrationState`, `GameOverState`) managing update/draw logic.
- **Utils.kt**: General utilities (e.g., rectangle collision detection).
- **controller.kt**: Main `GameController` class orchestrating ECS world, systems, states, entity creation, and game logic.
- **Systems.kt**: ECS system classes (e.g., `MovementSystem`, `CollisionSystem`, `RenderingSystem`) for updating game entities.

Other potential files (not listed but implied): `build.gradle` for dependencies, `AndroidManifest.xml` for permissions/activity config.

## Contributing

Contributions are welcome! Fork the repo, create a branch, and submit a pull request. Focus areas:
- Add more math operations (division, fractions).
- Support touch controls as fallback.
- Enhance AI for single-player mode.
- Optimize for lower-end devices.

Please follow Kotlin conventions and add unit tests for new features.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.

---

Built with ❤️ by [Your Name/Team]. For questions, open an issue on GitHub.