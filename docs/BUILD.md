# Build APK

The GitHub Actions workflow builds the debug APK on every push to `main`.

After the run completes, open Actions → Android debug APK → the latest run → Artifacts and download `retroboy-arcade-debug-apk`.

This debug APK is for installation and testing. It is not a signed Play Store release. Supabase authentication requires the repository secrets `SUPABASE_URL` and `SUPABASE_ANON_KEY`; without them, guest mode remains available.
