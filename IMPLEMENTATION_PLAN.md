# MathGalaga Implementation Plan: Speed + Accuracy for Elementary Math

## Goal
Make gameplay exciting but achievable for elementary learners by balancing:
- **Accuracy** (correct target selection)
- **Speed** (time-to-solve)
- **Adaptive challenge** (difficulty rises/falls based on performance)

---

## 1) Grade-band design with concrete parameters

### Grade Band A (Grades 1–2)
- Focus: basic multiplication familiarity and confidence.
- Operations: multiplication only.
- Problem bounds:
  - `a,b` in `1..5` early, then `1..10` after stable success.
- Enemy pressure:
  - alien base speed: `1`
  - alien shoot chance: `0.0025`
  - alien shoot interval: `2600ms`
  - max bullets on screen: `12`
- Level pacing:
  - top targets: `4`
  - lower enemies: `6..8`
- Scoring:
  - correct target: `+10`
  - speed bonus: up to `+5`
  - wrong top-target shot: `-3`
  - wrong lower-enemy/noise shot: `0`
- Hints:
  - show hint after `2` wrong attempts or `8s` without correct hit.

### Grade Band B (Grades 3–4)
- Focus: multiplication fluency and mental strategies.
- Operations: multiplication only.
- Problem bounds:
  - `a,b` in `1..12` then `1..20`.
- Enemy pressure:
  - alien base speed: `2`
  - alien shoot chance: `0.004`
  - alien shoot interval: `2200ms`
  - max bullets: `16`
- Level pacing:
  - top targets: `5`
  - lower enemies: `10..14`
- Scoring:
  - correct target: `+12`
  - speed bonus: up to `+8`
  - wrong top-target shot: `-4`
- Hints:
  - show hint after `3` wrong attempts or `10s`.

### Grade Band C (Grades 5+)
- Focus: speed + precision under pressure.
- Operations: multiplication now, extensible for division later.
- Problem bounds:
  - `a,b` in `1..20`, advanced `5..50` only for top performers.
- Enemy pressure:
  - alien base speed: `3`
  - alien shoot chance: `0.006`
  - alien shoot interval: `1800ms`
  - max bullets: `20`
- Level pacing:
  - top targets: `5..6`
  - lower enemies: `14..20`
- Scoring:
  - correct target: `+15`
  - speed bonus: up to `+10`
  - wrong top-target shot: `-5`
- Hints:
  - optional (teacher/parent toggle), default off.

---

## 2) Exact code touchpoints and implementation tasks

## A. Add learner profile + grade band config
**Files:**
- `app/src/main/java/com/robocrops/mathgalaga/config.kt`
- `app/src/main/java/com/robocrops/mathgalaga/Components.kt`

**Changes:**
1. In `Config`, add:
   - `enum class GradeBand { G1_2, G3_4, G5_PLUS }`
   - `data class GradeBandSettings(...)` with fields for bounds, enemy speed/chance/interval, bullet cap, scoring values, hint thresholds.
   - `val GRADE_BAND_PRESETS: Map<GradeBand, GradeBandSettings>` using parameter values above.
2. Add current selection:
   - `var currentGradeBand: GradeBand = GradeBand.G3_4` (default middle band).
3. In `Player` component, add telemetry fields:
   - `currentProblemStartMs: Long`
   - `wrongAttemptsOnCurrentProblem: Int`
   - `correctCount: Int`, `wrongCount: Int`

---

## B. Fix adaptive difficulty to include failures
**Files:**
- `app/src/main/java/com/robocrops/mathgalaga/difficulty.kt`
- `app/src/main/java/com/robocrops/mathgalaga/Systems.kt`

**Changes:**
1. In `DifficultyManager.record(correct: Boolean)`, keep sliding window, but tune thresholds by grade band:
   - G1_2: level up at `>=0.75`, down at `<0.50`
   - G3_4: up at `>=0.80`, down at `<0.50`
   - G5_PLUS: up at `>=0.85`, down at `<0.55`
2. In collision logic:
   - when a player hits wrong numbered top alien, call `p.dm.record(false)`.
   - increment `p.wrongAttemptsOnCurrentProblem++`.
3. On correct answer hit:
   - call `p.dm.record(true)` (already present).
   - reset problem wrong-attempt counter and start time.

---

## C. Add speed-aware scoring model
**Files:**
- `app/src/main/java/com/robocrops/mathgalaga/Systems.kt`
- `app/src/main/java/com/robocrops/mathgalaga/States.kt`

**Changes:**
1. Compute solve time:
   - `solveMs = now - p.currentProblemStartMs`.
2. Speed bonus formula:
   - `speedBonus = clamp(maxBonus * (targetMs - solveMs) / targetMs, 0, maxBonus)`.
   - targetMs by band:
     - G1_2: `9000ms`
     - G3_4: `7000ms`
     - G5_PLUS: `5500ms`
3. Score update on correct target:
   - `p.score += baseCorrect + speedBonus + streakBonus`.
4. Wrong top-target shot penalty:
   - subtract `wrongPenalty` by grade band.
5. HUD additions:
   - show accuracy `%` and last solve time (e.g., `Acc: 83%  Time: 4.2s`).

---

## D. Replace random distractors with pedagogical distractors
**File:**
- `app/src/main/java/com/robocrops/mathgalaga/controller.kt`

**Changes:**
1. In `setupLevel()`, replace `List(3) { Random.nextInt(1, 100) }` distractors.
2. Add helper function (same file or `Utils.kt`):
   - `generateDistractors(correctAnswer: Int, band: GradeBand): List<Int>`
3. Distractor strategy:
   - near misses (`±1`, `±2`, `±10` where valid), table confusions (e.g., swap factors), and one far distractor.
4. Ensure uniqueness and avoid equal-to-correct.

---

## E. Grade-based pacing for formations and enemy pressure
**Files:**
- `app/src/main/java/com/robocrops/mathgalaga/LevelUtils.kt`
- `app/src/main/java/com/robocrops/mathgalaga/controller.kt`
- `app/src/main/java/com/robocrops/mathgalaga/config.kt`

**Changes:**
1. Add grade-band-aware top/lower formation counts.
2. Keep current formation shapes but reduce density for G1_2.
3. Apply per-band alien speed/chance/interval when constructing `AlienMovement` and `Shooter`.
4. Apply per-band bullet cap in `createBullet`.

---

## F. Hint system for recovery (avoid frustration)
**Files:**
- `app/src/main/java/com/robocrops/mathgalaga/Components.kt`
- `app/src/main/java/com/robocrops/mathgalaga/Systems.kt`
- `app/src/main/java/com/robocrops/mathgalaga/States.kt`

**Changes:**
1. Add `Hint` component/entity fields:
   - message, start time, duration.
2. In update loop, trigger hint when wrong attempts or timeout threshold reached.
3. Render hint near problem text (simple one-line strategy prompt).
4. Example messages:
   - "Use doubles: 6×7 = 6×6 + 6"
   - "Break apart: 8×7 = 8×5 + 8×2"

---

## G. Multiplayer fairness modes
**Files:**
- `app/src/main/java/com/robocrops/mathgalaga/States.kt`
- `app/src/main/java/com/robocrops/mathgalaga/controller.kt`
- `app/src/main/java/com/robocrops/mathgalaga/config.kt`

**Changes:**
1. Add mode enum:
   - `SINGLE_PLAYER`, `COOP_SHARED`, `COOP_INDEPENDENT`
2. Level progression rules:
   - shared: current behavior (both clear to advance).
   - independent: advance when active learner clears own target set or contributes quota.
3. Game-over rules:
   - independent mode should not end session because one learner is out.

---

## 3) Rollout sequence (recommended)

### Milestone 1 (1–2 days)
- Grade-band presets + apply to enemy pressure.
- Add failure recording (`record(false)`) and wrong-shot penalties.
- Keep UI minimal.

### Milestone 2 (1–2 days)
- Speed-based scoring + HUD metrics.
- Distractor generator swap-in.

### Milestone 3 (2 days)
- Hint system.
- Multiplayer fairness modes.

### Milestone 4 (1 day)
- QA tuning pass on parameter values from playtest logs.

---

## 4) Validation checklist

### Functional checks
- Correct hit increases score and updates DM with `true`.
- Wrong top-target hit updates DM with `false` and applies penalty.
- Difficulty level can move both up and down over play.
- Grade band changes enemy pressure and problem ranges.
- Hint appears only after configured thresholds.

### Learning outcome checks
- Accuracy target ranges by band:
  - G1_2: `70–85%`
  - G3_4: `75–90%`
  - G5_PLUS: `80–92%`
- Median solve-time trend should improve over a session.

### Performance checks
- No frame-drop regression from hint/HUD updates.
- Bullet cap and entity cleanup still stable.

---

## 5) Success metrics to track (lightweight)
- `session_accuracy`
- `median_solve_ms`
- `difficulty_level_distribution`
- `wrong_attempts_before_correct`
- `hints_shown_per_session`

Use these to tune thresholds after classroom playtests.
