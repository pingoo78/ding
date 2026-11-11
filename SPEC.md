# Ding! — Spécification (contrat commun à tous les agents)

Application Android **100 % locale** (aucun compte, aucun réseau, aucune permission INTERNET) pour créer
des **programmes de minuteurs** : une suite d'étapes chaînées qui sonnent à des instants donnés depuis le
démarrage (ex. 5, 12, 18, 23 min puis fin à 30 min), avec option « attendre ma validation » qui gèle le
chrono tant que l'utilisateur n'a pas validé l'étape atteinte.

Langue de l'UI : **français** (ressources dans `values/`). Code et identifiants en anglais.

---

## 1. Stack & build (imposé — versions déjà en cache sur la machine)

| Élément | Valeur |
|---|---|
| Gradle wrapper | 8.13 (`gradlew`, `gradlew.bat`, `gradle/wrapper/*` versionnés dans le dépôt) |
| AGP | 8.13.0 |
| Kotlin | 2.2.20 (+ plugins `org.jetbrains.kotlin.plugin.compose` et `org.jetbrains.kotlin.plugin.serialization` 2.2.20) |
| JDK | Android Studio JBR (JDK 21) → définir `JAVA_HOME` avant chaque build ; bytecode JVM 17 |
| SDK | SDK Android (chemin dans `local.properties`, non versionné) ; compileSdk 35, targetSdk 35, minSdk 26 |
| UI | Jetpack Compose (BOM récent compatible), Material 3, `material-icons-extended` (style **Rounded** partout) |
| Navigation | `androidx.navigation:navigation-compose` |
| Persistance | **DataStore** + **kotlinx.serialization JSON** (pas de Room/KSP) |
| Réordonnancement | `sh.calvin.reorderable:reorderable` (glisser-déposer des étapes) |
| DI | Manuelle : `AppContainer` créé dans `DingApp` (pas de Hilt) |
| Tests | JUnit4 + kotlinx-coroutines-test (tests JVM purs du moteur) |
| Version catalog | `gradle/libs.versions.toml` |

Package / applicationId : `fr.pingooweb.ding`. Nom affiché : **Ding!**.
Commandes de validation : `./gradlew assembleDebug testDebugUnitTest lintDebug`.

---

## 2. Modèle de données (`fr.pingooweb.ding.domain.model`)

Tous `@Serializable`, immuables.

```kotlin
data class Program(
    val id: String,                 // UUID
    val name: String,
    val colorIndex: Int = 0,        // index dans ProgramPalette (0..7)
    val steps: List<Step>,          // ≥ 1 ; la DERNIÈRE étape = fin du programme
    val defaultWaitForAck: Boolean = false,
    val defaultSound: SoundChoice = SoundChoice.SystemAlarm,
    val defaultVibrate: Boolean = true,
    val announceLabels: Boolean = false,   // synthèse vocale (TTS hors-ligne) du libellé à chaque étape
    val ringSeconds: Int = 8,              // durée de sonnerie si l'étape n'attend PAS de validation
    val repeatCount: Int = 1,              // nb de cycles ; 0 = infini
    val createdAt: Long, val updatedAt: Long, val lastRunAt: Long? = null,
)

data class Step(
    val id: String,
    val label: String,              // ex. "Retourner la viande" ; vide → "Étape n"
    val durationMs: Long,           // durée DEPUIS l'étape précédente (ou le départ) ; > 0
    val waitForAck: Boolean? = null,  // null = hérite de Program.defaultWaitForAck
    val sound: SoundChoice? = null,   // null = hérite
    val vibrate: Boolean? = null,     // null = hérite
)

sealed interface SoundChoice {      // sérialisation polymorphe
    data object SystemAlarm : SoundChoice          // sonnerie d'alarme par défaut du système
    data object Silent : SoundChoice               // vibration / visuel seulement
    data class Custom(val uri: String, val title: String) : SoundChoice  // via RingtoneManager picker
}
```

**Stockage interne en durées**, mais l'éditeur permet de saisir en **temps cumulé** (« sonne à 12:00 »)
ou en **durée d'étape** (« +7:00 ») ; conversion `cumulative[i] = Σ durationMs[0..i]`. En mode cumulé, les
instants doivent être strictement croissants (erreur inline sinon).

Helpers (dans `domain`) : `Program.totalDurationMs`, `Program.cumulativeOffsets()`,
`Step.effectiveWaitForAck(p)`, `effectiveSound(p)`, `effectiveVibrate(p)`.

`ProgramPalette` : 8 couleurs (cf. §6) — couleur d'accent du programme (anneau, liseré de carte).

---

## 3. Moteur d'exécution (pur Kotlin, `fr.pingooweb.ding.domain.engine`) — cœur testable

Aucune dépendance Android. Le temps est injecté (`now` = `SystemClock.elapsedRealtime()` côté Android).

```kotlin
enum class RunStatus { RUNNING, PAUSED, WAITING_ACK, FINISHED }

data class RunState(
    val program: Program,           // INSTANTANÉ du programme au démarrage (les éditions ne l'affectent pas)
    val cycle: Int,                 // 1-based
    val currentIndex: Int,          // étape vers laquelle on décompte (ou atteinte si WAITING_ACK)
    val status: RunStatus,
    val stepStartedAt: Long,        // instant (elapsed) où le décompte de l'étape courante a démarré, décalé des pauses
    val pausedAt: Long?,            // non-null si PAUSED ou WAITING_ACK (chrono gelé)
    val runStartedAt: Long,
    val totalFrozenMs: Long,        // cumul des pauses + attentes de validation (pour le temps effectif)
    val completedSteps: Int,        // nb d'étapes atteintes dans le cycle courant
)

sealed interface RunEvent {
    data class StepReached(val index: Int, val cycle: Int, val waitsForAck: Boolean, val isLast: Boolean) : RunEvent
    data class CycleCompleted(val cycle: Int) : RunEvent
    data object ProgramFinished : RunEvent
}

object RunEngine {
    fun start(program: Program, now: Long): RunState
    fun tick(state: RunState, now: Long): Pair<RunState, List<RunEvent>>
    fun pause(state: RunState, now: Long): RunState          // RUNNING → PAUSED
    fun resume(state: RunState, now: Long): RunState         // PAUSED → RUNNING (stepStartedAt += durée gelée)
    fun acknowledge(state: RunState, now: Long): Pair<RunState, List<RunEvent>> // WAITING_ACK → étape suivante démarre à `now`
    fun skip(state: RunState, now: Long): Pair<RunState, List<RunEvent>>        // atteint immédiatement l'étape courante (sans sonner : pas d'événement StepReached)
    fun remainingInStepMs(state: RunState, now: Long): Long
    fun elapsedEffectiveMs(state: RunState, now: Long): Long // temps écoulé hors gels
    fun nextDeadline(state: RunState): Long?                 // instant elapsed de la prochaine échéance si RUNNING, sinon null
}
```

Sémantique (à couvrir par tests unitaires) :
1. Étape **sans validation** atteinte : événement `StepReached`, l'étape suivante démarre **à l'échéance** (pas à `now`, pour ne pas dériver).
2. Étape **avec validation** atteinte : `status = WAITING_ACK`, chrono gelé (`pausedAt = échéance`). `acknowledge` → étape suivante démarre à `now` ; le gel compte dans `totalFrozenMs`.
3. `tick` après une longue absence (process endormi) : avance à travers **toutes** les étapes sans validation échues, s'arrête à la première étape avec validation ; renvoie un événement par étape franchie.
4. Dernière étape atteinte : si cycles restants → `CycleCompleted` puis cycle suivant (index 0) ; sinon `ProgramFinished` et `status = FINISHED`. Si la dernière étape attend une validation, `WAITING_ACK` d'abord, puis l'ack mène à la fin / au cycle suivant.
5. `repeatCount = 0` → infini.
6. Pause pendant RUNNING puis reprise : les échéances sont décalées de la durée de pause.
7. Exemple de référence (le test « canonique ») : étapes 5/12/18/23/30 min en cumulé, sans validation → événements à 5,12,18,23 min puis fin à 30.

`TimeFormat` : `format(ms)` → `"m:ss"` / `"h:mm:ss"` (arrondi au supérieur pour un décompte : 4,2 s restantes → `0:05`), parsing des chiffres du pavé numérique (« 1230 » → 12:30).

---

## 4. Contrôleur & runtime Android

Interface consommée par l'UI (`fr.pingooweb.ding.domain.RunController`) :

```kotlin
interface RunController {
    val state: StateFlow<RunState?>   // null = aucun programme en cours
    val alerting: StateFlow<Alert?>   // alerte sonore/visuelle en cours (étape atteinte)
    fun start(programId: String)
    fun pause(); fun resume()
    fun acknowledge()                 // valide l'étape atteinte (et coupe la sonnerie)
    fun silence()                     // coupe la sonnerie sans valider
    fun skip()
    fun stop()
}
data class Alert(val stepIndex: Int, val stepLabel: String, val waitsForAck: Boolean, val isFinal: Boolean)
```

Un seul programme actif à la fois (v1). Démarrer un autre programme → l'UI demande confirmation et arrête le précédent.

Implémentation `fr.pingooweb.ding.runtime` :
- **`DefaultRunController`** (singleton dans `AppContainer`) : applique `RunEngine`, persiste l'état actif
  (DataStore JSON, avec `Settings.Global.BOOT_COUNT` pour invalider après redémarrage), déclenche service,
  alarmes et alertes à partir des `RunEvent`.
- **`TimerService`** : Foreground Service `foregroundServiceType="specialUse"` (propriété
  `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` = "Minuteur programmable multi-étapes"). Démarré au `start`, arrêté au
  `stop`/fin. Boucle coroutine qui `tick` à chaque échéance (précision) ; notification persistante.
- **`AlarmScheduler`** : `AlarmManager.setAlarmClock()` sur la prochaine échéance (fiable en Doze) →
  `AlarmReceiver` → `controller.onAlarm()` (tick). Replanifié à chaque changement d'état ; annulé si gelé/arrêté.
- **Notification en cours** (canal « Programme en cours », importance LOW) : nom du programme, étape
  courante, décompte natif via `setUsesChronometer(true)` + `setChronometerCountDown(true)` (pas de mise à
  jour chaque seconde), actions Pause/Reprendre · Passer · Arrêter. Tap → ouvre l'écran Exécution.
- **Notification d'alerte** (canal « Étapes atteintes », importance HIGH, catégorie ALARM) : libellé de
  l'étape, actions **Valider** (si attente) / **Couper** ; `fullScreenIntent` → `AlertActivity`
  (au-dessus de l'écran de verrouillage : `showWhenLocked`, `turnScreenOn`).
- **`AlertPlayer`** : son via `MediaPlayer`/`Ringtone` avec `AudioAttributes.USAGE_ALARM` ; vibration
  (motif) ; TTS hors-ligne optionnel (annonce du libellé). Si l'étape attend une validation → sonne en boucle
  jusqu'à `acknowledge`/`silence` (sécurité : coupe le son après 3 min, la notification reste). Sinon → sonne
  `ringSeconds` puis s'arrête seul, le chrono continue.
- **Permissions** (manifest) : `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`,
  `USE_EXACT_ALARM`, `SCHEDULE_EXACT_ALARM` (maxSdkVersion 32), `USE_FULL_SCREEN_INTENT`, `VIBRATE`,
  `WAKE_LOCK`. **Jamais `INTERNET`.**
- **`PermissionStatus`** : helper exposant l'état (notifications, alarmes exactes, plein écran
  `canUseFullScreenIntent()` sur 34+, optimisation batterie) + intents vers les écrans système.

---

## 5. Écrans & UX (`fr.pingooweb.ding.ui`)

Pile de navigation simple (pas de bottom nav) : **Accueil** → Éditeur / Exécution / Réglages.
Deep link : la notification ouvre directement Exécution.

### 5.1 Accueil — liste des programmes
- Top app bar « Ding! » + icône Réglages.
- **Bandeau « En cours »** (si un programme tourne) : couleur du programme, nom, étape, décompte live, tap → Exécution.
- Cartes programme : liseré/pastille couleur, nom, durée totale, nb d'étapes, **mini-frise** (segments
  proportionnels aux durées, marqueur spécial pour les étapes à validation), bouton **Lecture** 48 dp bien
  visible. Tap carte → Éditeur. Menu ⋮ : Dupliquer, Exporter, Supprimer (confirmation + Snackbar « Annuler »).
- FAB étendu « Nouveau programme ».
- **État vide** : illustration simple (icône vectorielle), texte d'accueil et **modèles** en un tap :
  « Exemple 30 min » (5/12/18/23/30), « Pomodoro » (25/5 ×4 avec validation), « Entraînement fractionné » (30 s/15 s ×8).
- Démarrer : vérifie `POST_NOTIFICATIONS` (13+) → demande si besoin (avec explication), puis navigue vers Exécution.

### 5.2 Éditeur de programme
- Champ nom (label visible), sélecteur de couleur (8 pastilles 48 dp, état sélectionné non basé que sur la couleur : coche).
- **Frise d'aperçu** en haut (segments proportionnels, durée totale).
- **Bascule de saisie** segmentée : « Heure de sonnerie » (cumulé, défaut) / « Durée d'étape ».
- Liste des étapes réordonnables (poignée de glisser + accessibilité : actions « Monter/Descendre ») ;
  chaque ligne : n°, libellé, temps (« à 12:00 » ou « +7:00 » selon le mode, l'autre en secondaire),
  icônes d'état (main = validation requise, cloche barrée = silencieux), tap → **bottom sheet d'étape**.
  La dernière étape est étiquetée « Fin du programme ».
- Bouton « Ajouter une étape » (pré-remplit +5 min après la dernière).
- **Bottom sheet d'étape** : libellé ; temps via **pavé numérique type horloge** (les chiffres remplissent
  h:mm:ss depuis la droite) + raccourcis (+30 s, +1 min, +5 min) ; interrupteur « Attendre ma validation »
  (Hériter / Oui / Non) ; son (Hériter / Alarme système / Choisir… via `RingtoneManager.ACTION_RINGTONE_PICKER` / Silencieux) ; vibration ; Supprimer.
- Section « Options du programme » (repliable) : validation par défaut, son par défaut, vibration, annonce
  vocale, durée de sonnerie (5/8/15/30 s), répétitions (1–20, ∞).
- Validation inline (erreur sous le champ), bouton Enregistrer dans la top bar ; quitter avec des
  modifications → dialogue « Abandonner les modifications ? ».

### 5.3 Exécution
- Fond neutre ; **grand anneau de progression** (couleur du programme) pour l'étape courante.
- Centre : **temps restant de l'étape** (chiffres très grands, tabulaires), libellé de l'étape, « Étape 3/5 ».
- Sous l'anneau : temps écoulé effectif · temps total restant · cycle « 2/4 » si répétitions.
- **Frise verticale des étapes** : faites (coche, atténuées), courante (mise en avant), à venir (heure prévue estimée).
- Contrôles bas, zone du pouce : **Pause/Reprendre** (bouton principal 72 dp), **Passer**, **Arrêter**
  (confirmation). Écran maintenu allumé (réglage).
- **État « Étape atteinte »** : la surface passe à la couleur d'attention (orange), animation de pulsation
  douce (désactivée si animations réduites), libellé en très grand, bouton géant **Valider** (si attente)
  ou **Couper** ; annonce accessibilité (`liveRegion` polite **uniquement** au changement d'étape, jamais chaque seconde).
- **Fin** : écran récapitulatif (durée effective, nb d'étapes) + « Recommencer » / « Terminer ».

### 5.4 Alerte plein écran (`AlertActivity`, scope runtime)
Même rendu que l'état « Étape atteinte » ; fonctionne écran verrouillé ; Valider / Couper / Arrêter le programme.

### 5.5 Réglages
- Apparence : thème Système/Clair/Sombre, « Couleurs dynamiques (Material You) » (off par défaut).
- Comportement : garder l'écran allumé pendant l'exécution, mode de saisie par défaut de l'éditeur, retour haptique.
- **Centre d'autorisations** : une ligne par permission avec état (OK / À activer) et bouton vers l'écran système.
- Données : Exporter tous les programmes (JSON via `ACTION_CREATE_DOCUMENT`), Importer (`ACTION_OPEN_DOCUMENT`, fusion, ids régénérés en cas de collision).
- À propos : « Fonctionne entièrement hors ligne. Aucune donnée ne quitte l'appareil. »

---

## 6. Design system « Bento » (app Ding!)

Direction : **Bento** — écran = grille de **tuiles** aux grands rayons (28 dp), sans ombre ni bordure, sur fond
gris-bleu clair ; typographie ronde et très grasse ; boutons **pilule**. Aucune couleur M3 par défaut (violet)
ne doit apparaître. Maquettes de référence : `design/project/Bento-*.dc.html` ; icône : `design/ding-icon-final.svg`.

### Couleurs (`ui/theme/Color.kt`, `ExtendedColors.kt`)

| Jeton | Clair | Sombre |
|---|---|---|
| background | `#EEF1F6` | `#0F1117` |
| tile (tuile) | `#FFFFFF` | `#1A1D27` |
| tileRaised (tuile surélevée) | `#FFFFFF` | `#232734` |
| ink (encre) = primary | `#14161F` (texte `#FFFFFF`) | `#F1F3F8` (texte `#14161F`) |
| inkSecondary (onSurfaceVariant) | `#4B5263` | `#A3ABBD` |
| track (filet / piste) | `#D5DAE3` | `#2E3342` |
| outline (contour discret) | `#9AA3B5` | `#5A6275` |
| attention (fond « étape atteinte ») | `#FFE3D1` | `#3A2116` |
| attentionAction / onAttentionAction | `#C2410C` / `#FFFFFF` | `#FF8A4C` / `#1C0A00` |
| success | `#127A4B` | `#6EE7A8` |
| error / onError | `#B91C1C` / `#FFFFFF` | `#F87171` / `#2A0A0A` |
| brand (corail Ding : logo, icône, splash uniquement) | `#FF5A3C` | `#FF5A3C` |
| brandText (corail accessible pour du texte) | `#D63B1F` | `#FF8A70` |

`primary` = encre : boutons pilule noirs en clair, inversés en sombre. Le `ColorScheme` M3 est entièrement mappé
(primary/secondary/tertiary + containers, surface*, outline*, inverse*, error*, surfaceTint transparent).

**`ProgramPalette`** : 8 paires (conteneur pastel, couleur forte), index 0..7 conservé.
Sur un conteneur : texte = encre du thème. Sur la couleur forte : blanc (clair) / `#14161F` (sombre).

| # | Nom | Clair (conteneur / forte) | Sombre (conteneur / forte) |
|---|---|---|---|
| 0 | Lavande | `#E6E0FF` / `#5B45E0` | `#2A2450` / `#B7A9FF` |
| 1 | Menthe | `#D7F2E3` / `#127A4B` | `#173A2A` / `#6EE7A8` |
| 2 | Pêche | `#FFE3D1` / `#C2410C` | `#3D2417` / `#FFB48A` |
| 3 | Ciel | `#DCEBFF` / `#1D5FD1` | `#172B47` / `#8CB8FF` |
| 4 | Rose | `#FFDDE8` / `#BE185D` | `#3F1A2A` / `#F9A8C9` |
| 5 | Citron | `#F4EFC2` / `#716000` | `#353112` / `#E8D96A` |
| 6 | Corail | `#FFE0DA` / `#C2341A` | `#401C16` / `#FF9A85` |
| 7 | Ardoise | `#E3E7EE` / `#3F4A5E` | `#232833` / `#B4BFD3` |

Accès (jamais de hex en dur dans les écrans) : `MaterialTheme.extended.{ink, inkSecondary, tile, tileRaised, track,
attention, onAttention, attentionAction, onAttentionAction, success, brand, brandText}` et
`MaterialTheme.extended.program(index)` → `ProgramColors(container, strong, onStrong)`
(`programAccent(index)` = ancienne couleur forte seule, alias transitoire).

### Typographie (`ui/theme/Type.kt`)
**Plus Jakarta Sans** (SIL OFL, TTF statiques Regular/Medium/SemiBold/ExtraBold dans `res/font/`, licence dans
`assets/plus_jakarta_sans_OFL.txt`). Échelle : titre d'écran 28 sp/800 (`headlineLarge`), titres de tuile 18–22/800
(`titleMedium`, `headlineSmall`), corps 16/500 (`bodyLarge`), libellés 13/600 (`labelMedium`).
**Chiffres du décompte** (`fontFeatureSettings = "tnum"`, poids 800, letterSpacing négatif) : `TimerHero` (96 sp),
`TimerLarge` (44 sp), `TimerMedium` (28 sp, valeur de StatTile), `TimerSmall` (20 sp). Alias : `TimerDigits` = Hero,
`TimerDigitsMedium` (22 sp), `TimerDigitsSmall` = Small. Tous les temps affichés utilisent `tnum`. Respecter la taille
de police système (pas de débordement).

### Dimensions (`Dimens`)
Rayon tuile `TileRadius` 28 dp ; rayon pastille/puce `PillRadius` 20 dp ; `Gap` 12 dp ; `Margin` 16 dp
(24 dp ≥ 600 dp de large) ; padding de tuile `TilePadding` 20 dp ; cible tactile `TargetSize` 48 dp ;
boutons `ButtonM` 48 / `ButtonL` 64 / `ButtonXL` 80 dp (`PrimaryControl` 72 dp).

### Kit de composants (`ui/components/bento/`)
`BentoTile` (fond tile ou conteneur pastel, onClick optionnel accessible), `PillButton` (variantes Primary / Strong /
Attention / Tonal ; tailles M / L / XL ; icône + texte), `IconBadge` (pastille 40 dp), `RoundIconButton` (48 dp,
`contentDescription` obligatoire), `SegmentBar` (frise segmentée proportionnelle : fait / en cours avec fraction /
à venir, marqueur rond évidé pour les étapes à validation), `StatTile` (libellé + valeur tabulaire). Avec `@Preview`
clair/sombre.

### Étape atteinte (`StepReachedContent`)
Plein écran sur fond `attention` : pastille cloche « Étape atteinte », grande tuile (libellé 40 sp/800, « Étape n/N ·
programme », puce « En attente depuis m:ss » incrémentée), deux `StatTile` (« Écoulé », « Ensuite : étape · dans m:ss »),
`PillButton` Attention XL « Valider » (ou « Couper » sans validation), puis « Couper le son » / « Arrêter ».
Libellé vide → titre « Étape n/N ». Pulsation 1,0→1,03 en 1,2 s (désactivée si animations réduites). `liveRegion`
polite uniquement au changement d'étape.

### Identité
Nom affiché **Ding!** (package/applicationId `fr.pingooweb.ding`). Icône adaptative : fond corail `#FF5A3C`,
cloche blanche + coche évidée (foreground, monochrome et splash partagent `ic_launcher_foreground`) ; icône de
notification = cloche monochrome, couleur de notification corail. Splash Android 12+ : fond corail + cloche.

### Espacements, mouvement, accessibilité
Grille 4/8 dp ; cibles ≥ 48 dp. Mouvement 150–250 ms, easing standard M3 ; l'anneau progresse linéairement ; tout est
désactivé si `ANIMATOR_DURATION_SCALE == 0`. `contentDescription` sur toutes les icônes interactives, icônes
décoratives masquées ; l'état ne repose jamais sur la couleur seule ; contraste ≥ 4,5:1 ; ordre de focus = ordre
visuel ; edge-to-edge avec insets respectés.

---

## 7. Arborescence et propriétaires (scopes des agents)

```
app/src/main/java/fr/pingooweb/ding/
  DingApp.kt, MainActivity.kt, di/AppContainer.kt         ← Phase 1 (fondations)
  domain/model/*, domain/engine/*, domain/RunController.kt  ← Phase 1
  data/*  (ProgramRepository, SettingsRepository, ActiveRunStore, ImportExport, Templates) ← Phase 1
  ui/theme/*, ui/navigation/*                                ← Phase 1
  runtime/*, alert/*                                         ← Phase 2 (runtime)
  ui/home/*, ui/editor/*, ui/run/*, ui/settings/*, ui/components/*  ← Phase 3 (UI)
res/values/strings.xml (Phase 1, commun) · strings_runtime.xml (Phase 2) · strings_ui.xml (Phase 3)
```
Un agent ne modifie pas les fichiers hors de son scope, sauf correction minime documentée dans son rapport.
