# PlatyBrowser Nuclei Mesh-Cache Harness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the mesh-cache compile bug and add a two-mode Java harness (`cache`/`render`) that pre-renders and interactively renders PlatyBrowser nuclei with label < 1000 via the mesh cache.

**Architecture:** One tracked Java main in `src/test/java/examples/` opens the PlatyBrowser project (branch `main`, dataset `platybrowser_6dpf`, view `nuclei`) using the existing public MoBIE/ViewManager/SegmentVolumeViewer APIs, forces a fixed isotropic 3D voxel spacing (default 0.5 µm) so the mesh cache activates, filters annotated segments to `0 < label < 1000`, then either pre-renders them to `~/.mobie/mesh-cache/` (`cache`) or selects and shows exactly them in the interactive Fiji 3D viewer (`render`). A one-line fix removes the `new File(File)` compile error in `ViewManager`.

**Tech Stack:** Java 8 (repo baseline), Maven, Fiji/ImageJ (`net.imagej.ImageJ`), MoBIE public APIs, jogamp Java3D via the 3D_Viewer.

## Global Constraints

- Java language level 8 (`release 8`); no newer APIs.
- Only touch: `src/main/java/org/embl/mobie/lib/view/ViewManager.java` (one-line compile fix) and add one new file under `src/test/java/examples/`.
- No changes to mesh-cache feature behavior, no refactoring.
- Cache root: `~/.mobie/mesh-cache/` (from `MoBIEHelper.getMeshCacheDir()`), cache file name auto-derived as `<displayName>-sm<k>-<spacing>um.mel`.
- Spacing argument is isotropic `(s, s, s)`, default `0.5` µm.
- Filter: `label() > 0 && label() < maxLabel`, `maxLabel` default `1000`.
- Repo commit style: imperative, concise (`git log --oneline` for examples).
- NOTE: the repo currently cannot be compiled on this Linux machine (JRE only, no `javac`/Maven). Compile/run validation happens on the user's Windows/IntelliJ setup (or any machine with a JDK). All tasks therefore end with a commit, and the plan's validation section lists the Windows commands.

---
### Task 1: Fix the `new File(File)` compile error in `ViewManager`

The mesh-cache branch was never built. `ViewManager.initSegmentVolumeViewer` calls
`new File( MoBIEHelper.getMeshCacheDir() )`, but `MoBIEHelper.getMeshCacheDir()`
already returns a `File` — `new File(File)` is not a valid constructor and breaks
compilation.

**Files:**
- Modify: `src/main/java/org/embl/mobie/lib/view/ViewManager.java:777`

- [ ] **Step 1: Make the edit**

Replace line 777:

```java
display.segmentVolumeViewer.configureMeshCache( display.getName(), new File( MoBIEHelper.getMeshCacheDir() ) );
```

with:

```java
display.segmentVolumeViewer.configureMeshCache( display.getName(), MoBIEHelper.getMeshCacheDir() );
```

(The `configureMeshCache(String, File)` signature already accepts the returned
`File` directly. Do not touch anything else.)

- [ ] **Step 2: Sanity-check the edit**

Run: `grep -n "getMeshCacheDir" src/main/java/org/embl/mobie/lib/view/ViewManager.java`
Expected: the single remaining reference reads
`configureMeshCache( display.getName(), MoBIEHelper.getMeshCacheDir() );`
and there is no other `new File( MoBIEHelper...` pattern left in the file.

Also grep for any other accidental `new File( ... getMeshCacheDir()` occurrences repo-wide:
Run: `grep -rn "new File( MoBIEHelper.getMeshCacheDir" src/main/java`
Expected: no matches.

- [ ] **Step 3: Note remaining compile risk**

This branch (`feature/mesh-cache`, base of `feature/mesh-cache-subset`) has never
been compiled anywhere. If compiling on Windows (command in Validation) reports
further errors in the mesh-cache feature files (`MeshCache.java`,
`SegmentVolumeViewer.java`, `MeshCreator.java`, `UserInterfaceHelper.java`,
`MoBIEHelper.java`, `ViewManager.java`), fix those minimal compile errors as part
of this task before proceeding, then re-run the compile command.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/org/embl/mobie/lib/view/ViewManager.java
git commit -m "Fix new File(File) compile error when configuring mesh cache"
```

---
### Task 2: Add the two-mode `PlatybrowserNucleiMeshCache` harness

**Files:**
- Create: `src/test/java/examples/PlatybrowserNucleiMeshCache.java`

**Interfaces:**
- Consumes (existing public APIs, verified in the codebase):
  - `net.imagej.ImageJ` — `new ImageJ(); imageJ.ui().showUI();`
  - `org.embl.mobie.MoBIESettings` — `new MoBIESettings().gitProjectBranch(String).dataset(String).view(String)` (each returns `MoBIESettings`)
  - `org.embl.mobie.MoBIE` — `public MoBIE( String projectUri, MoBIESettings settings ) throws IOException`
  - `MoBIE.getViewManager()` → `org.embl.mobie.lib.view.ViewManager`; `viewManager.getCurrentSegmentationDisplays()` → `List<SegmentationDisplay>` (raw)
  - `SegmentationDisplay` (raw): `getName()`, `getSources()`, `getAnnData().getTable()` (raw table), public fields `segmentVolumeViewer` (raw `SegmentVolumeViewer`) and `selectionModel` (raw `SelectionModel`)
  - `SegmentVolumeViewer` (raw): `getVoxelSpacing()`, `setVoxelSpacing(double[])`, `configureMeshCache(String, File)`, `preRenderSegments(Collection)`, `showSegments(boolean, boolean)`, `getMeshCache().size()`
  - `org.embl.mobie.lib.util.MoBIEHelper.getMeshCacheDir()` → `File`
  - Annotations are instances of `org.embl.mobie.lib.annotation.AnnotatedSegment` (has `long label()`)
- Produces: runnable main `examples.PlatybrowserNucleiMeshCache`, program arg `cache` | `render`, optional args `branch dataset view spacing maxLabel`.

- [ ] **Step 1: Write the file**

Create `src/test/java/examples/PlatybrowserNucleiMeshCache.java` with exactly this content
(tabs for indentation, matching `ColorNucleiByZCoordinate.java` conventions):

```java
package examples;

import net.imagej.ImageJ;
import org.embl.mobie.MoBIE;
import org.embl.mobie.MoBIESettings;
import org.embl.mobie.lib.annotation.AnnotatedSegment;
import org.embl.mobie.lib.serialize.display.SegmentationDisplay;
import org.embl.mobie.lib.util.MoBIEHelper;
import org.embl.mobie.lib.view.ViewManager;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Pre-renders or renders the first {@code maxLabel} nuclei of the PlatyBrowser
 * 2025 dataset through the segment mesh cache.
 *
 * <p>Two modes (first program argument):
 * <ul>
 *   <li>{@code cache}  - compute + smooth the meshes of all nuclei with
 *                        0 &lt; label &lt; maxLabel and write them to the disk
 *                        cache (~/.mobie/mesh-cache), then exit.</li>
 *   <li>{@code render} - open the interactive Fiji 3D viewer showing exactly
 *                        those nuclei (loading from the cache when present),
 *                        for manual screenshots; keep running until closed.</li>
 * </ul>
 *
 * <p>Optional further program arguments (all with defaults):
 * {@code [branch] [dataset] [view] [spacing-um] [maxLabel]}
 * e.g. {@code render main platybrowser_6dpf nuclei 0.5 1000}
 *
 * <p>The mesh cache only activates when the segment volume viewer has a fixed
 * 3D voxel spacing. The plain "nuclei" view does not declare one, so this
 * harness sets it explicitly (isotropic {@code spacing}) before configuring
 * the cache. Run {@code cache} once (slow, network + marching cubes), then
 * {@code render} any time (fast, loads from the cache). Changing the spacing
 * argument yields a different cache file.
 */
public class PlatybrowserNucleiMeshCache
{
	public static final String PROJECT = "https://github.com/cyrilcros/platybrowser-project-2025";

	public static void main( String[] args ) throws Exception
	{
		final String mode = args.length > 0 ? args[ 0 ] : "cache";
		final String branch = args.length > 1 ? args[ 1 ] : "main";
		final String dataset = args.length > 2 ? args[ 2 ] : "platybrowser_6dpf";
		final String view = args.length > 3 ? args[ 3 ] : "nuclei";
		final double spacing = args.length > 4 ? Double.parseDouble( args[ 4 ] ) : 0.5;
		final long maxLabel = args.length > 5 ? Long.parseLong( args[ 5 ] ) : 1000;

		if ( ! mode.equals( "cache" ) && ! mode.equals( "render" ) )
			throw new IllegalArgumentException( "First argument must be 'cache' or 'render'; got: " + mode );

		final ImageJ imageJ = new ImageJ();
		imageJ.ui().showUI();

		final MoBIESettings settings = new MoBIESettings()
				.gitProjectBranch( branch )
				.dataset( dataset )
				.view( view );

		System.out.println( "Opening " + PROJECT + " @ branch " + branch + ", dataset " + dataset + ", view " + view );

		final MoBIE moBIE = new MoBIE( PROJECT, settings );

		run( moBIE, mode, spacing, maxLabel );
	}

	@SuppressWarnings( { "unchecked", "rawtypes" } )
	private static void run( MoBIE moBIE, String mode, double spacing, long maxLabel ) throws Exception
	{
		final ViewManager viewManager = moBIE.getViewManager();
		final List< SegmentationDisplay > segmentationDisplays = viewManager.getCurrentSegmentationDisplays();

		if ( segmentationDisplays.isEmpty() )
			throw new RuntimeException(
					"No segmentation displays found in the current view. "
							+ "Open a view that contains a segmentation (e.g. view \"nuclei\")." );

		// Find the nuclei segmentation display, falling back to the first one.
		SegmentationDisplay display = null;
		for ( SegmentationDisplay candidate : segmentationDisplays )
		{
			if ( candidate.getName() != null && candidate.getName().toLowerCase().contains( "nuclei" ) )
			{
				display = candidate;
				break;
			}
		}
		if ( display == null )
			display = segmentationDisplays.get( 0 );
		System.out.println( "Segmentation display: " + display.getName() );

		if ( display.segmentVolumeViewer == null )
			throw new RuntimeException( "Segment volume viewer not initialised for display " + display.getName() );

		// The mesh cache only works with a fixed 3D voxel spacing; the plain
		// "nuclei" view does not declare one, so set it and enable the cache.
		if ( display.segmentVolumeViewer.getVoxelSpacing() == null )
			display.segmentVolumeViewer.setVoxelSpacing( new double[] { spacing, spacing, spacing } );
		display.segmentVolumeViewer.configureMeshCache( "nuclei", MoBIEHelper.getMeshCacheDir() );
		System.out.println( "3D voxel spacing: " + spacing + " um (isotropic); cache dir: " + MoBIEHelper.getMeshCacheDir() );

		final List< AnnotatedSegment > subset = annotations( display ).stream()
				.filter( s -> s.label() > 0 && s.label() < maxLabel )
				.collect( Collectors.toList() );

		if ( subset.isEmpty() )
			throw new RuntimeException( "No segments with 0 < label < " + maxLabel + " in display " + display.getName() );

		System.out.println( "Subset: " + subset.size() + " segments (labels 1.." + ( maxLabel - 1 ) + ")" );

		if ( mode.equals( "cache" ) )
			preRender( display, subset );
		else
			render( display, subset );
	}

	@SuppressWarnings( { "unchecked", "rawtypes" } )
	private static List< AnnotatedSegment > annotations( SegmentationDisplay display )
	{
		if ( display.getAnnData() == null || display.getAnnData().getTable() == null )
			throw new RuntimeException( "No annotation table available for display " + display.getName() );

		final List< AnnotatedSegment > annotations = new ArrayList<>();
		for ( Object annotation : display.getAnnData().getTable().annotations() )
			if ( annotation instanceof AnnotatedSegment )
				annotations.add( ( AnnotatedSegment ) annotation );
		return annotations;
	}

	@SuppressWarnings( { "unchecked", "rawtypes" } )
	private static void preRender( SegmentationDisplay display, List< AnnotatedSegment > subset )
	{
		System.out.println( "Pre-rendering " + subset.size() + " meshes... (parallel; progress in the Fiji status bar)" );
		display.segmentVolumeViewer.preRenderSegments( subset );
		final int cached = display.segmentVolumeViewer.getMeshCache() == null
				? 0
				: display.segmentVolumeViewer.getMeshCache().size();
		System.out.println( "Done. Cached meshes: " + cached );
		System.out.println( "Cache files under: " + MoBIEHelper.getMeshCacheDir() );
		System.exit( 0 );
	}

	@SuppressWarnings( { "unchecked", "rawtypes" } )
	private static void render( SegmentationDisplay display, List< AnnotatedSegment > subset )
	{
		display.selectionModel.clearSelection();
		display.selectionModel.setSelected( subset, true );
		display.segmentVolumeViewer.showSegments( true, true );
		System.out.println( "3D view open with " + subset.size() + " nuclei (labels below maxLabel). Rotate/zoom; screenshot; close to exit." );
	}
}
```

- [ ] **Step 2: Compile-check (on a machine with a JDK, e.g. the Windows/IntelliJ side)**

Run (repo root, from IntelliJ's bundled Maven or a JDK shell):

```bash
mvn -DskipTests=false -Dmaven.test.skip=false test-compile
```

or, in IntelliJ: `Build ▸ Build Project` after letting the Maven import finish.

Expected: `BUILD SUCCESS`. If the compiler reports errors, fix them and re-run.
If Task 1's compile risk note applies (additional pre-existing mesh-cache errors),
resolve those first.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/examples/PlatybrowserNucleiMeshCache.java
git commit -m "Add PlatyBrowser nuclei mesh-cache harness (cache/render modes)"
```

---
### Task 3: Run configurations and user validation (Windows side, no repo change)

**Files:** none (IntelliJ configurations are user-local; `.idea` is gitignored).

- [ ] **Step 1: Pull the branch in IntelliJ**

In the Windows checkout: `git pull` (or checkout) `feature/mesh-cache-subset`, then
let the Maven import + `Build ▸ Build Project` finish so the new classes compile.

- [ ] **Step 2: Create the two Application run configurations**

`Run ▸ Edit Configurations… ▸ + ▸ Application` twice:

1. Name: `PlatybrowserNucleiMeshCache (cache)`
   - Main class: `examples.PlatybrowserNucleiMeshCache`
   - Program arguments: `cache`
   - Use classpath of module: `mobie-viewer-fiji` (test classpath; IntelliJ picks
     this automatically when the main is in `src/test`)
   - VM options: leave empty (do NOT set `-Dmobie.test3d` — not needed here)
2. Name: `PlatybrowserNucleiMeshCache (render)`
   - Same main class; Program arguments: `render`

(Equivalent: right-click `PlatybrowserNucleiMeshCache` in the project tree →
`Run 'PlatybrowserNucleiMeshCache.main()'` with `cache`/`render` passed by editing
the created configuration's *Program arguments*.)

- [ ] **Step 3: Run `cache`**

Run the `cache` configuration. Expected, over several minutes:
- Fiji UI opens; console logs opening branch/dataset/view and the display.
- "Subset: N segments (labels 1..999)" with N ≈ 999.
- Mesh pre-rendering progresses in the Fiji status bar.
- On completion: "Done. Cached meshes: …" and a `.mel` file appears under
  `~/.mobie/mesh-cache/` (e.g. `nuclei-sm5-0_5um.mel`); the JVM exits.

- [ ] **Step 4: Run `render`**

Run the `render` configuration. Expected:
- Fiji 3D viewer window opens showing only the cached nuclei (fast — loads from
  cache), interactive for rotating/zooming and screenshots.
- If the `cache` step was skipped or spacing differs, meshes are computed on the
  fly (slow) instead — still works, just slower.

- [ ] **Step 5: Report results back**

Report: subset size, cache file name/size, whether render showed exactly the
expected nuclei, and any errors (screenshots/log excerpts) for follow-up.

---
## Validation summary

- Linux side (this session): code written and committed; **no local compile is
  possible** (JRE only, no `javac`/Maven) — compilation is verified on the
  Windows/IntelliJ side per Task 2 Step 2 and Task 3.
- Windows side: `mvn -DskipTests=false -Dmaven.test.skip=false test-compile`
  (Task 2), then Task 3 run steps (cache, then render).
- Success criteria: branch compiles; `cache` produces a `.mel` file with ~999
  meshes; `render` interactively shows only nuclei with label < 1000.
