# BodyAR Try-On

An Android app that detects a person's body in the live camera feed and overlays
virtual **shirts**, **pants**, and **sunglasses** on them in real time (AR
try-on), with a sidebar to browse the wardrobe and a capture button to save the
result. Built with **CameraX** + **ML Kit Pose Detection** + Kotlin, no Android
Studio required to build (GitHub Actions workflow included, matching how you've
been building your other projects).

## How the AR works

There are two broad approaches to "put clothes on a detected body":

1. **Full 3D body mesh + garment simulation** (what apps like Zeekit/Snap AR
   fashion filters do) — requires a body-shape 3D reconstruction model and
   physics-based cloth draping. Very heavy, not realistic to hand-roll from
   scratch in one project.
2. **2D pose-anchored overlay** — detect skeletal landmarks (shoulders, hips,
   knees, ankles, eyes, ears) with `ML Kit Pose Detection`, then scale/rotate/
   position a 2D garment cutout so it tracks those landmarks frame-to-frame.
   This is the same technique used by most consumer "outfit try-on" camera
   filters, and it's what this project implements.

`PoseGarmentRenderer.kt` is the core of it — for each garment it:
- picks the relevant landmark pair (shoulders for shirts, hips+ankles for
  pants, eyes+ears for sunglasses)
- computes the on-screen distance between them → garment width/scale
- computes the angle between them → garment rotation, so it tilts naturally
  when you turn or lean
- maps pose coordinates (which are in the camera analysis frame's coordinate
  space) into on-screen coordinates via `CoordinateMapper`, which replicates
  CameraX's `FILL_CENTER` scale-and-crop behavior so the overlay lines up with
  what's on screen, including front-camera mirroring.

The exact same renderer is reused for the **live preview overlay** and for
**compositing the saved photo**, so what you see is what gets captured.

## Project structure

```
app/src/main/java/com/hftx/bodyar/
  MainActivity.kt          Activity: camera setup, sidebar wiring, capture button
  CameraPoseAnalyzer.kt    CameraX ImageAnalysis.Analyzer running ML Kit pose detection
  PoseOverlayView.kt       Custom View drawing garments over the live preview
  PoseGarmentRenderer.kt   Core math: pose landmarks -> garment position/scale/rotation
  CaptureHelper.kt         Takes a full-res photo, composites garments, saves to gallery
  ClothingItem.kt          Data model
  ClothingRepository.kt    The 10 shirts / 10 pants / 8 sunglasses catalog
  ClothingAdapter.kt       RecyclerView adapter for the sidebar thumbnail grid

app/src/main/res/
  drawable/shirt_1..10.xml   Placeholder shirt artwork
  drawable/pant_1..10.xml    Placeholder pant artwork
  drawable/glass_1..8.xml    Placeholder sunglasses artwork
  layout/activity_main.xml   DrawerLayout: camera + overlay + sidebar
  layout/item_clothing.xml   Sidebar thumbnail cell

.github/workflows/android-build.yml   CI build -> downloadable debug APK artifact
```

## About the included clothing art

To hand you a project that **runs end-to-end today**, all 28 wardrobe items
(10 shirts, 10 pants, 8 sunglasses) are generated as simple flat-color vector
silhouettes — not photoreal garment photos, since I can't generate/license
real clothing photography inside this build. They're wired up completely (sidebar
thumbnails, category filtering, live AR placement, capture) so you can see and
test the whole pipeline immediately.

**To get photoreal results**, swap the art for real assets:
1. Get transparent-background PNG/WebP cutouts of shirts/pants/sunglasses
   (front-facing, arms/legs roughly T-pose works best for the scaling math).
2. Drop them into `res/drawable/` using the same filenames (`shirt_1.png`,
   `pant_3.png`, `glass_5.png`, ...) — no code changes needed, since
   `ClothingRepository` resolves them by name.
3. You'll likely want to tweak the scale multipliers in
   `PoseGarmentRenderer.kt` (e.g. `shoulderWidth * 2.25f`) to match your new
   art's exact proportions — this is normal tuning for any pose-anchored AR
   overlay and depends on how much empty margin your PNGs have.

Adding more items (beyond 10/10/8) is just adding entries to the `shirtNames`
/ `pantNames` / `glassNames` lists in `ClothingRepository.kt` plus a matching
drawable.

## Building

**Option A — GitHub Actions (matches your existing workflow):**
Push this repo to GitHub; `.github/workflows/android-build.yml` builds a debug
APK on every push to `main` and uploads it as a workflow artifact you can
download. It provisions Gradle directly via `gradle/actions/setup-gradle`
rather than relying on a committed wrapper jar, so no local Android Studio or
Gradle install is needed.

**Option B — Android Studio:** Open the project folder directly; Studio will
generate the Gradle wrapper and sync automatically.

## Permissions

Only `CAMERA` is required at runtime (requested on first launch). Saved
photos go to `Pictures/BodyAR` via `MediaStore`, so no storage permission is
needed on Android 10+.

## Known limitations / good next steps

- Garments are 2D and don't deform around body contours (no cloth simulation) —
  this is the standard trade-off for real-time mobile AR try-on without a
  server-side 3D pipeline.
- Occlusion isn't handled (e.g. an arm crossing in front of the torso will be
  drawn *under* the shirt overlay, not over it) — solving this well needs body
  segmentation masks (e.g. ML Kit Selfie Segmentation) composited with the
  garment layer, which would be a solid v2 addition.
- Tuning constants in `PoseGarmentRenderer.kt` were chosen for a person
  standing roughly upright, facing the camera — extreme poses/angles will
  need more sophisticated landmark logic (e.g. detecting side-on stance and
  switching to a profile-fit garment).
