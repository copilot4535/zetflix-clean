# Migrate :music module from Hilt to Koin

The current Hilt implementation is incompatible with AGP 9.1.1, causing build failures ("Android BaseExtension not found"). This plan details the migration of the `:music` module to Koin for dependency injection.

## User Review Required

> [!IMPORTANT]
> This change replaces Hilt with Koin in the `:music` module. Hilt annotations like `@AndroidEntryPoint` and `@Inject` will be removed.

## Proposed Changes

### Build Configuration

#### [MODIFY] [libs.versions.toml](file:///home/user/zetflix-clean/gradle/libs.versions.toml)
- Ensure Koin versions and libraries are correctly defined.

#### [MODIFY] [build.gradle.kts (music)](file:///home/user/zetflix-clean/music/build.gradle.kts)
- Remove `dagger.hilt.android` plugin.
- Replace Hilt dependencies with `koin-android` and `koin-compose`.

---

### Dependency Injection Refactoring

#### [MODIFY] [AppModule.kt](file:///home/user/zetflix-clean/music/src/main/java/com/zetflix/music/di/AppModule.kt)
- Convert the Hilt module to a Koin module.
- Use `single` for singletons and `viewModel` for ViewModels.
- Use `named()` for qualifiers (e.g., PlayerCache, DownloadCache).

#### [MODIFY] [MusicInitializer.kt](file:///home/user/zetflix-clean/music/src/main/java/com/zetflix/music/MusicInitializer.kt)
- Initialize Koin using `startKoin` or `loadKoinModules`.

---

### Component Refactoring

#### [MODIFY] All Activities and Services in `:music`
- Remove `@AndroidEntryPoint`.
- Use `by inject()` or `get()` for dependency retrieval.

#### [MODIFY] All ViewModels in `:music`
- Remove `@Inject` from constructors.
- Register them in the Koin module.

#### [MODIFY] All Composables in `:music`
- Replace `hiltViewModel()` with `koinViewModel()`.

## Verification Plan

### Automated Tests
- Run Gradle sync to verify that build configuration is stable.
- Build the `:music` module.

### Manual Verification
- Launch the Music activity from the main app.
- Verify that dependencies are correctly injected (app doesn't crash on startup).
