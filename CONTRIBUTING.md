# Contributing to Audioly

Open an issue for larger changes before implementation. Use a focused branch from `main`, describe the user-visible change, and include relevant validation in your pull request.

Use JDK 17 and the Android SDK configured in `app/build.gradle.kts`. Run `./gradlew :app:testDevDebugUnitTest :app:assembleDevDebug`. For backend work, run `go test ./...` from `backend/` using the Go version in `go.mod`.

Do not commit credentials, account cookies, private recordings, local configuration, signing material or generated APKs. Keep source attribution notices and the license. See `NOTICE.md` for provenance.
