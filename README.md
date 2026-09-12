# UnboundDex — offline Android Pokédex + Borrius map

A native Kotlin/Jetpack Compose Android app for Pokémon Unbound. The finished APK is offline: the app does **not** need internet access at runtime.

## What is included

- Source-backed Pokémon data from the current public Pokémon Unbound Field Guide dataset.
- Pokémon names, guide numbers, types, abilities, hidden abilities, base stats, evolutions and known locations.
- Wild encounter methods and encounter rates when supplied by the source dataset (Land/Day/Night, Surfing, Old Rod, Good Rod, Super Rod, Rock Smash).
- Borrius route/area map with pinch-to-zoom and pan.
- Route 1–18 plus major caves/areas and Victory Road as selectable map nodes.
- Location exits/points of interest when supplied by the source dataset.
- Type filters and Pokémon search.
- Build-time source hash so each APK records exactly which data package it used.

## Important map note

The public Field Guide's own source inventory says exact map artwork is not currently supplied with its data package. This project therefore uses an original, offline Borrius route schematic rather than redistributing copyrighted game/wiki map artwork. The app architecture leaves room for replacing the schematic with locally supplied map images/tiles later.

## Easiest way to get an APK — GitHub Actions

1. Create a new **private or public GitHub repository**.
2. Upload the entire `UnboundDex` folder from this ZIP.
3. Open the repository's **Actions** tab.
4. Select **Build UnboundDex APK**.
5. Click **Run workflow**.
6. When it finishes, open the workflow run and download the artifact named **UnboundDex-debug-apk**.
7. Unzip that artifact and install `app-debug.apk` on your Android phone.

The workflow downloads the source-backed data during the build, packages it into the APK, and then the resulting app works offline.

## Android Studio

Open the `UnboundDex` folder in Android Studio. Use **Build → Make Project** or **Build → Build Bundle(s) / APK(s) → Build APK(s)**. Android Studio will resolve the Gradle/Android dependencies.

## Data provenance

The public field guide documents Pokémon Unbound 2.1.1.1 as its current versioned dataset. It identifies Dynamic Pokémon Expansion/CFRU as primary source data and the Unbound-Pokedex encounter tables plus the version-matched Location Guide workbook as supplementary encounter sources.

This project does not include a Pokémon ROM or ROM-derived executable code. It packages structured guide data and an original UI/map schematic.
