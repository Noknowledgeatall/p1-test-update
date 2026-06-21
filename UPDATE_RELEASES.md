# Updates publiceren

De app controleert updates eerst via:

`https://raw.githubusercontent.com/Noknowledgeatall/p1-test-update/main/updates/update.json`

Daarna gebruikt hij GitHub Releases als fallback:

`https://github.com/Noknowledgeatall/p1-test-update`

Werkwijze:

1. Bouw de APK:

   `gradle assembleDebug`

2. Maak in GitHub een nieuwe Release aan met een tag die hoger is dan de app-versie, bijvoorbeeld:

   `v0.1.2`

3. Upload deze APK als release asset:

   `app/build/outputs/apk/debug/app-debug.apk`

4. In de app: tik op `Controleer update`.

De makkelijkste route is `updates/update.json` plus `updates/energy-optimizer-latest.apk`.
Bij GitHub Releases zoekt de app in de nieuwste release naar de eerste asset waarvan de naam eindigt op `.apk`.
De updateknop downloadt die APK automatisch via Android DownloadManager en opent daarna de Android-installer.

Met een GitHub token met `contents:write` kan dit ook vanaf de laptop:

`$env:GITHUB_TOKEN='...'; .\tools\publish-github-release.ps1 v0.1.2`
