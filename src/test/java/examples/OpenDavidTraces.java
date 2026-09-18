package examples;

import net.imagej.ImageJ;
import ij.IJ;
import org.embl.mobie.MoBIE;
import org.embl.mobie.MoBIESettings;
import org.embl.mobie.lib.annotation.AnnotatedSegment;
import org.embl.mobie.lib.serialize.DataSource;
import org.embl.mobie.lib.serialize.display.SegmentationDisplay;
import org.embl.mobie.lib.view.ViewManager;
import org.embl.mobie.lib.volume.SegmentVolumeViewer;

import java.util.ArrayList;
import java.util.List;

/**
 * Starts a running Platybrowser (from IntelliJ) and loads ALL traces of the
 * newly added David sources into the live 3D volume viewer, so that MoBIE
 * renders them on demand and logs which traces cannot be rendered.
 * <p>
 * No meshes are precomputed: this drives the normal {@code SegmentVolumeViewer}
 * path (selection -> 3D rendering), exactly as clicking the traces in the UI
 * would. The render-failure log cap is lifted so every failing trace is
 * reported, not just the first 10.
 * <p>
 * Arguments (optional):
 * <ol>
 *   <li>git branch of the project (default {@code main})</li>
 *   <li>comma-separated source names (default
 *       {@code david_all_traces,traces_MN_David_Puga})</li>
 * </ol>
 * Failures appear as {@code [MoBIE] Could not render segment <label> in 3D: ...}
 * in the ImageJ Log window ({@code Plugins > Log}) and on the console.
 */
public class OpenDavidTraces
{
	public static final String PROJECT = "https://github.com/cyrilcros/platybrowser-project-2025";

	public static final String DEFAULT_SOURCES = "david_all_traces,traces_MN_David_Puga";

	public static void main( String[] args ) throws Exception
	{
		final String branch = args.length > 0 ? args[ 0 ] : "main";
		final String[] requestedSources = ( args.length > 1 ? args[ 1 ] : DEFAULT_SOURCES ).split( "," );

		log( "Opening " + PROJECT + " @ branch: " + branch );
		final ImageJ imageJ = new ImageJ();
		imageJ.ui().showUI();
		final MoBIE moBIE = new MoBIE( PROJECT, new MoBIESettings().gitProjectBranch( branch ) );
		final ViewManager viewManager = moBIE.getViewManager();

		// Mirror the viewer's per-trace render progress to the console, so a
		// stall or silent stop immediately shows the trace that blocked it.
		SegmentVolumeViewer.setProgressLogger( message ->
		{
			IJ.log( message );
			System.out.println( message );
			System.out.flush();
		} );

		// Resolve and initialise only the requested trace sources.
		final List< DataSource > dataSources = new ArrayList<>();
		for ( final String requested : requestedSources )
		{
			final String name = requested.trim();
			final DataSource ds = moBIE.getDataset().sources().get( name );
			if ( ds == null )
				log( "WARNING: source '" + name + "' not found in the dataset; skipping." );
			else
				dataSources.add( ds );
		}

		if ( dataSources.isEmpty() )
			throw new IllegalStateException( "None of the requested trace sources exist in this project/branch." );

		log( "Initialising " + dataSources.size() + " source(s) ..." );
		moBIE.initDataSources( dataSources );

		final List< String > sourceNames = new ArrayList<>();
		for ( final DataSource ds : dataSources )
			sourceNames.add( ds.getName() );

		// A segmentation display for all requested sources; we select every trace.
		final SegmentationDisplay< AnnotatedSegment > display =
				new SegmentationDisplay<>( "David traces (3D)", sourceNames );
		display.showTable( false );

		log( "Showing segmentation display " + display.getName() + " ..." );
		viewManager.show( display );

		if ( display.getAnnData() == null || display.segmentVolumeViewer == null )
			throw new IllegalStateException( "Display was not initialised; are there any traces in these sources?" );

		final List< AnnotatedSegment > traces = display.getAnnData().getTable().annotations();
		log( "Selecting all " + traces.size() + " trace(s) ..." );
		display.selectionModel.setSelected( traces, true );

		// Report every failing trace, not just the first 10.
		SegmentVolumeViewer.setMaxLoggedRenderFailures( Integer.MAX_VALUE );

		log( "Rendering all selected traces in the 3D volume ..." );
		display.segmentVolumeViewer.showSegments( true, true );

		log( "" );
		log( "Done. MoBIE is now rendering the traces on demand." );
		log( "Watch for '[MoBIE] Could not render segment <label> in 3D: ...' in the" );
		log( "ImageJ Log window (Plugins > Log) and on this console." );
	}

	private static void log( final String message )
	{
		System.out.println( message );
		System.out.flush();
	}
}
