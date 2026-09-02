# Pre-render and render PlatyBrowser nuclei subset via the mesh cache

- **Date**: 2026-09-02
- **Status**: Approved (design)
- **Branch**: `feature/mesh-cache-subset` (based on `feature/mesh-cache`)

## Context

`feature/mesh-cache` adds a disk-backed mesh cache for segment meshes shown in the
Fiji 3D Viewer (`~/.mobie/mesh-cache/*.mel`). Computing + smoothing a segment mesh
(marching cubes over the label volume) is the expensive step; caching it once makes
subsequent 3D opens fast. The existing UI ("M" button) pre-renders *all* segments
of a segmentation display.

The user wants to run this on **PlatyBrowser 2025** locally, restricted to the first
~1000 nuclei of the `nuclei` segmentation (label ID < 1000; "neurons" was loose
language — no cell-type filtering is wanted):

1. one run that **generates the mesh cache** for those nuclei;
2. one run that **renders** exactly those nuclei in the interactive Fiji 3D viewer
   (user screenshots manually).

Dataset facts: project `https://github.com/cyrilcros/platybrowser-project-2025`
@ branch `main`, dataset `platybrowser_6dpf` (project default), view `nuclei`
(plain whole-dataset view with a single `SegmentationDisplay` named `nuclei`,
sources `["nuclei"]`, no `resolution3d`). Data (label volume + table) is read from
the project's S3 backend, so runs need internet.

## Goals

- Provide two IntelliJ run configurations (cache generation, render) that work in
  the user's local Fiji/IntelliJ environment on Windows.
- Cache meshes only for nuclei with label ID < 1000, at a fixed 3D voxel spacing
  (default 0.5 µm), so the render run reuses the cache.
- Render shows only that subset in the 3D universe, interactive.

## Non-goals

- No change to the mesh-cache feature code itself unless a bug surfaces.
- No cell-type/annotation-based filtering.
- No programmatic image/movie export (user screenshots from the 3D window).
- No changes to the PlatyBrowser project repository.

## Key mechanism facts (verified against the code)

- `new MoBIE(uri, MoBIESettings.settings().gitProjectBranch(b).dataset(d))` opens
  repo@branch + dataset with the default view; a named view is then activated via
  `moBIE.getViewManager().show("nuclei")`.
- A `SegmentationDisplay` is only present after its view has been shown; it is
  reachable via `viewManager.getCurrentSegmentationDisplays()` (match source/name
  `nuclei`).
- Showing a view always creates `display.segmentVolumeViewer`
  (`ViewManager.initSegmentVolumeViewer`), but `configureMeshCache(...)` **no-ops
  unless the viewer has a fixed `voxelSpacing`** (`SegmentVolumeViewer.java:167`),
  and the `nuclei` view does not declare `resolution3d`. The harness must therefore
  set the spacing + configure the cache itself through the public API:
  `segmentVolumeViewer.setVoxelSpacing(spacing)` then
  `segmentVolumeViewer.configureMeshCache("nuclei", MoBIEHelper.getMeshCacheDir())`.
- The segment collection consumed by pre-rendering is
  `display.getAnnData().getTable().annotations()` (the same idiom as the "M"
  button). Annotated segments expose `label()`.
- Which segments appear in 3D is driven by the display's `SelectionModel`
  (`SegmentVolumeViewer.updateSelectedSegments` adds content for
  `selectionModel.getSelected()`). Restricting the view to a subset =
  `selectionModel.clearSelection()` + `selectionModel.setSelected(subset, true)`
  and ensuring 3D is on via `segmentVolumeViewer.showSegments(true, true)`.
- The cache file name encodes segmentation name + smoothing iterations + spacing,
  so a different spacing argument yields a separate cache file automatically.

## Approach (approved: single main, two modes)

One Java main `PlatybrowserNucleiMeshCache` in `src/test/java/examples/`
(tracked, committed to the branch). `main(String[] args)`:

- `args[0]` mode: `cache` | `render`.
- Optional further args (with defaults):
  `branch` = `main`, `dataset` = `platybrowser_6dpf`, `view` = `nuclei`,
  `spacing` = `0.5` (µm, first element used for all axes),
  `maxLabel` = `1000` (exclusive upper bound).

### Shared steps (both modes)

1. Start `net.imagej.ImageJ` and show the UI.
2. Open the project:
   `new MoBIE(PROJECT_URI, MoBIESettings.settings().gitProjectBranch(branch).dataset(dataset))`.
3. `moBIE.getViewManager().show(view)`.
4. Find the `SegmentationDisplay` whose sources/name match `nuclei`; fail with a
   clear message if absent.
5. If `display.segmentVolumeViewer.getVoxelSpacing() == null`, call
   `setVoxelSpacing(spacing)`; then call
   `configureMeshCache("nuclei", MoBIEHelper.getMeshCacheDir())`.
6. `List<Segment> all = display.getAnnData().getTable().annotations();`
   `subset = all.stream().filter(s -> s.label() < maxLabel)` (plus `label() >= 1`
   to skip a background row if present). Log the subset size.
7. If `subset` is empty, abort with a message.

### Mode `cache`

8. `display.segmentVolumeViewer.preRenderSegments(subset)` (parallel; progress in
   the status bar; flushes the cache file on completion).
9. Log the number of cached meshes (`segmentVolumeViewer.getMeshCache().size()`)
   and the cache file path; then `System.exit(0)` so the JVM terminates cleanly.

### Mode `render`

8. `display.selectionModel.clearSelection()`;
   `display.selectionModel.setSelected(subset, true)`.
9. `display.segmentVolumeViewer.showSegments(true, true)` — opens the 3D universe
   with exactly the selected nuclei (meshes load from the cache when present).
10. Log an instruction ("3D view open; rotate/zoom and screenshot; close to exit");
    keep the JVM alive for interaction.

## IntelliJ run configurations (user-created, two entries)

JUnit-style *Application* configurations pointing at the same main class,
module classpath of `mobie-viewer-fiji` (test scope), main class
`examples.PlatybrowserNucleiMeshCache`:

- **PlatybrowserNucleiMeshCache (cache)**: program argument `cache`
- **PlatybrowserNucleiMeshCache (render)**: program argument `render`

Optional program args after the mode, e.g. `cache main platybrowser_6dpf nuclei 0.5 1000`.

Run order: run `cache` once (slow — computes ~999 meshes from S3), then `render`
(fast, reads the cache). Re-run `render` any time; re-run `cache` after changing
spacing/smoothing or deleting `~/.mobie/mesh-cache`.

## Validation

- Compiles in the repo (`mvn -DskipTests=false -Dtest=NoSuchTest test` is not
  meaningful here; compile via `mvn -DskipTests test-compile`).
- Runs in the user's IntelliJ/Fiji on Windows:
  - `cache`: logs the number of cached meshes and creates
    `~/.mobie/mesh-cache/nuclei-sm<k>-0_5um.mel`; rerunning logs "0 pending"
    (all cached).
  - `render`: opens the 3D viewer showing only nuclei with label < 1000; adding
    nothing outside the subset; meshes appear without a long recompute.
- Linux box has no JDK: compile/run validation happens on the Windows side.

## Risks / caveats

- **Compile bug in `feature/mesh-cache` (prerequisite fix).** `ViewManager.java:777`
  calls `new File( MoBIEHelper.getMeshCacheDir() )`, but `getMeshCacheDir()` already
  returns a `File` — `new File(File)` does not compile. This branch has never been
  built. Fix: `configureMeshCache( display.getName(), MoBIEHelper.getMeshCacheDir() )`.
- `preRenderSegments` silently does nothing if the cache is not configured — the
  harness guards this by configuring the cache explicitly and logging the cache
  file/size.
- First `cache` run can take a long time (network + marching cubes for ~999
  nuclei); progress is shown in the Fiji status bar.
- The 3D render needs a GL-capable display (fine on the Windows desktop).
- S3/network access is required to read the label volumes.
- Spacing choice trades mesh detail vs. compute time (0.5 µm default approved).
