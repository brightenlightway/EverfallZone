# EverZones

Paper 1.21.11 plugin for PvPZone and SafeZone.

## Build without installing Gradle

1. Create a new GitHub repository.
2. Upload every file/folder in this project.
3. Open the **Actions** tab.
4. Select **Build EverZones**.
5. Click **Run workflow**.
6. Wait for the green check.
7. Open the completed workflow.
8. Under **Artifacts**, download **EverZones**.
9. Extract it and upload `EverZones-1.0.0.jar` to your server's `plugins/` folder.
10. Fully restart the server.

Java 21 is used automatically by GitHub Actions.

After installation:
- `/pvpzone wand`
- `/pvpzone list`
- `/pvpzone delete <number>`
- `/safezone wand`
- `/safezone list`
- `/safezone delete <number>`

Zone data is stored in `plugins/EverZones/zones.yml`.
