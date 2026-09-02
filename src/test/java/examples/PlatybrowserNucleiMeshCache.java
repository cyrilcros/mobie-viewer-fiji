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
