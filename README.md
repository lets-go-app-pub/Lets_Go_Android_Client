# Lets_Go_Android_Client — Kotlin app for activity-based matching

Android client for the Lets Go platform. Users pick activities and timeframes, swipe through match cards, join event cards, and coordinate in an in-app chat—without sharing personal info. The app talks to the C++ gRPC server and stores lightweight local state for fast UI.

> **Stack:** Kotlin · Jetpack Fragments/Navigation · WorkManager · gRPC/Protobuf client · Local databases (Room/SQLite) · Glide  
> **Features:** swipeable match cards · activity/timeframe selection · events as cards · private chat rooms · phone/Google login

---

## Product snapshots

<p align="center">
  <img src="images/home_screen.png" alt="Home/Login" width="260">
  <img src="images/swiping.png" alt="Swipe Cards" width="260">
  <img src="images/tutorial.png" alt="Timeframe Tutorial" width="260"><br/>
  <img src="images/user_card.png" alt="User Card" width="260">
  <img src="images/activities.png" alt="Select Activities" width="260">
  <img src="images/chat_room.png" alt="Chat Room" width="260">
</p>

---

## Highlights (skim me)
- **Polished flows:** phone/Google login → profile setup → activities & timeframes → swipe matches → chat.
- **Responsive UI:** swipeable cards with clear affordances, per-activity chips, and time-overlap visuals.
- **Typed networking:** generated **gRPC** stubs from shared `.proto` files (see `Lets_Go_Protobuf`).
- **Smooth background work:** **WorkManager** and dedicated workers for chat streaming, cleanup, and retries.
- **Local persistence:** small, focused **databases** for accounts, messages, icons, other users, and chat rooms.
- **Privacy by design:** in-app chat rooms so coordination doesn’t require exchanging personal details.

---

## How it works (client view)

**Architecture at a glance**
- **UI layer:** Activities + **Fragments** (`matchScreenFragment`, `chatRoomFragment`, etc.) with shared ViewModel utilities.
- **Data layer:** gRPC clients, repositories, and local databases (Room/SQLite) for offline reads and quick UI.
- **Workers:** background tasks for streaming, uploads, cleanup, and error handling.

**Key flows**
- **Login & onboarding:** fragments collect minimal info (name, birthday, pictures, categories), then confirm via SMS.
- **Matching:** client sends preferences (categories, distance, time windows); server returns a batch → rendered as cards.
- **Chat:** a background **chat stream** receives new messages (fan-out from server’s change stream) and writes to the local DB; UI observes DB for instant updates.

---

## Code tour (where to look)

**UI & Navigation**
- `app/src/main/java/site/letsgoapp/letsgo/activities/` — top-level activities (entry points)
- `app/src/main/java/site/letsgoapp/letsgo/applicationActivityFragments/` — main app screens  
  - `matchScreenFragment/`, `matchMadeScreenFragment/`, `chatRoomsListFragment/`, `chatRoomFragment/`, `profileScreenFragment/`, `selectLocationScreenFragment/`, `timeFrameTutorialFragment/`
- `app/src/main/java/.../loginActivityFragments/` — onboarding: phone/Google login, rules, verification
- `app/src/main/res/navigation/` — NavGraph(s), transitions and destinations

**Data & Networking**
- `app/src/main/java/.../gRPC/clients/` — gRPC client stubs/wrappers (generated from `.proto`)
- `app/src/main/java/.../repositories/` — thin repos (e.g., `chatRoomCommandsRPCs/`) to isolate transport from UI
- `app/src/main/java/.../standAloneObjects/` — `chatStreamObject/`, `findMatchesObject/`, `loginFunctions/`

**Local databases (Room/SQLite)**
- `app/src/main/java/.../databases/`
  - `accountInfoDatabase/` → `accountInfo/`, `accountPicture/`
  - `iconsDatabase/` → `icons/`
  - `messagesDatabase/` → `messages/`, `messageMimeTypes/`, `unsentSimpleServerCommands/`
  - `otherUsersDatabase/` → `otherUsers/`, `chatRooms/`, `matches/`

**Workers & Utilities**
- `app/src/main/java/.../workers/` — `chatStreamWorker/`, `chatStreamObjectWorkers/`, `cleanDatabaseWorker/`, `loginFunctionWorkers/`, `error_handling/`
- `app/src/main/java/.../utilities/` — date pickers, input filters, Glide config, shared ViewModel helpers
- `app/src/main/res/` — layouts, drawables, animations, fonts, menus, colors, etc.

**Tests**
- `app/src/androidTest/java/.../` — UI/instrumentation (fragments, fakes, stream object tests)
- `app/src/test/java/.../` — unit tests + utilities

---

## Design choices (frontend)
- **Fragments + NavGraph** for structured flows and deep-link resilience.
- **Local-first rendering:** UI observes DB tables so lists/chat feel instant while network catches up.
- **Background streaming via WorkManager** keeps chat/live updates running with OS-friendly scheduling.
- **Typed contracts (gRPC)** reduce client/server drift and simplify data models.
- **Defensive UX:** clear error surfaces (retry, “trouble logging in”), block/report affordances, and guardrails during onboarding.

---

## Related

- **Server (C++)** — stateless hub, gRPC/Protobuf, MongoDB  
  👉 [`Lets_Go_Server`](https://github.com/lets-go-app-pub/Lets_Go_Server)

- **Desktop Admin (Qt)** — admin/ops console for moderation, events, stats, and controls  
  👉 [`Lets_Go_Interface`](https://github.com/lets-go-app-pub/Lets_Go_Interface)

- **Protobuf Files** — protobuf files used to communicate between server and clients  
  👉 [`Lets_Go_Protobuf`](https://github.com/lets-go-app-pub/Lets_Go_Protobuf)

## Status & compatibility
Portfolio reference of a completed app. SDK/Gradle versions are legacy; modern toolchains may require updates.

## License
MIT
