package examples;

import net.imagej.ImageJ;
import org.embl.mobie.MoBIE;
import org.embl.mobie.MoBIESettings;

import java.io.IOException;

/**
 * Opens the platybrowser-project-2025 at the {@code test_numeric_annotation}
 * branch to exercise the new {@code numericAnnotation} data source.
 *
 * Usage (program arguments):
 *   [view]    -> view to open (default "default")
 */
public class OpenNumericAnnotationTest
{
	public static final String PROJECT = "https://github.com/cyrilcros/platybrowser-project-2025";
	public static final String BRANCH = "test_numeric_annotation";
	public static final String VIEW = "nuclei-anchor_z";

	public static void main( String[] args ) throws IOException
	{
		final String view = args.length > 0 ? args[ 0 ] : VIEW;

		final ImageJ imageJ = new ImageJ();
		imageJ.ui().showUI();

		final MoBIESettings settings = new MoBIESettings()
				.gitProjectBranch( BRANCH )
				.view( view );

		System.out.println( "Opening " + PROJECT + " @ branch: " + BRANCH + ", view: " + view );

		final MoBIE moBIE = new MoBIE( PROJECT, settings );
	}
}
