/*-
 * #%L
 * Fiji viewer for MoBIE projects
 * %%
 * Copyright (C) 2018 - 2024 EMBL
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 *  IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package org.embl.mobie.lib.volume;

import ij3d.DefaultUniverse;
import ij3d.Image3DUniverse;
import ij3d.ImageCanvas3D;
import org.jogamp.java3d.Background;
import org.jogamp.java3d.BranchGroup;
import org.jogamp.java3d.Canvas3D;
import org.jogamp.java3d.GraphicsConfigTemplate3D;
import org.jogamp.java3d.ImageComponent;
import org.jogamp.java3d.ImageComponent2D;
import org.jogamp.java3d.Screen3D;
import org.jogamp.vecmath.Color3f;
import org.jogamp.vecmath.Point3f;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.GraphicsConfigTemplate;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.util.Collections;

import javax.swing.SwingUtilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Checks whether a snapshot of the Fiji 3D Viewer universe (ij3d
 * {@link Image3DUniverse}) can be rendered with a <em>transparent</em>
 * background.
 * <p>
 * The user-configurable RGB background of the 3D viewer is an opaque Java3D
 * {@link Background} node in the scene graph. The idea under test
 * ("approach A"): temporarily detach that node and render into an offscreen
 * {@link Canvas3D} whose clear color is fully transparent. If Java3D clears
 * the offscreen buffer accordingly, background pixels come back with alpha 0
 * while rendered content keeps alpha 255.
 * <p>
 * These tests open a 3D viewer window and need an OpenGL-capable display, so
 * they only run when explicitly enabled:
 *
 * <pre>
 * mvn -DskipTests=false -Dmobie.test3d=true \
 *     -Dtest=Image3DUniverseTransparentBackgroundTest test
 * </pre>
 * (or via {@code xvfb-run} on a headless machine).
 */
class Image3DUniverseTransparentBackgroundTest
{
	private static final int WIDTH = 256;

	private static final int HEIGHT = 256;

	/** Sample corner pixels this many pixels inside the image borders. */
	private static final int INSET = 4;

	@Test
	void snapshotRenderedWithBackgroundNodeIsOpaque() throws Exception
	{
		require3DTestEnvironment();

		final Image3DUniverse universe = createUniverseOrAbort();
		try
		{
			addCentralMesh( universe );
			final BufferedImage snapshot = renderOffscreen( universe );
			assertOpaqueCorners( snapshot );
			assertTrue( alphaAt( snapshot, WIDTH / 2, HEIGHT / 2 ) > 0, "expected content in the image centre" );
		}
		finally
		{
			closeUniverse( universe );
		}
	}

	@Test
	void snapshotRenderedWithoutBackgroundNodeIsTransparent() throws Exception
	{
		require3DTestEnvironment();

		final Image3DUniverse universe = createUniverseOrAbort();
		final Background background = ( ( ImageCanvas3D ) universe.getCanvas() ).getBG();
		final BranchGroup scene = universe.getScene();
		boolean detached = false;
		try
		{
			addCentralMesh( universe );

			// Detach the opaque Background node, so the render is cleared with
			// the (fully transparent) offscreen canvas background instead.
			scene.removeChild( background );
			detached = true;

			final BufferedImage snapshot = renderOffscreen( universe );
			assertTransparentCorners( snapshot );
			assertTrue( alphaAt( snapshot, WIDTH / 2, HEIGHT / 2 ) > 0, "expected content in the image centre" );
		}
		finally
		{
			if ( detached )
			{
				try
				{
					scene.addChild( background );
				}
				catch ( final RuntimeException e )
				{
					e.printStackTrace();
				}
			}
			closeUniverse( universe );
		}
	}

	// -- helpers ------------------------------------------------------------

	private static void require3DTestEnvironment()
	{
		assumeTrue( Boolean.getBoolean( "mobie.test3d" ),
			"3D rendering test disabled; run with -Dmobie.test3d=true" );
		assumeFalse( GraphicsEnvironment.isHeadless(), "3D rendering test requires a display (e.g. xvfb-run)" );
	}

	private static Image3DUniverse createUniverseOrAbort()
	{
		try
		{
			final Image3DUniverse universe = new Image3DUniverse( WIDTH, HEIGHT );
			universe.setAutoAdjustView( false ); // keep the default centred view
			universe.showAttribute( DefaultUniverse.ATTRIBUTE_SCALEBAR, false );
			universe.showAttribute( DefaultUniverse.ATTRIBUTE_COORD_SYSTEM, false );
			SwingUtilities.invokeAndWait( universe::show );
			waitForDisplayableCanvas( universe.getCanvas() );
			return universe;
		}
		catch ( final Throwable t )
		{
			// e.g. Java3D cannot create a graphics configuration because there
			// is no OpenGL/display available on this machine
			assumeTrue( false, "Could not create a 3D universe on this machine: " + t );
			return null; // never reached
		}
	}

	private static void addCentralMesh( final Image3DUniverse universe )
	{
		// one opaque icosphere in the centre of the view; small enough that the
		// image corners are guaranteed to show only the background
		universe.addIcospheres( Collections.singletonList( new Point3f( 0, 0, 0 ) ),
			new Color3f( 0.9f, 0.1f, 0.1f ), 2, 0.5f, "sphere" );
	}

	private static void waitForDisplayableCanvas( final Canvas3D canvas ) throws InterruptedException
	{
		for ( int i = 0; i < 100 && !canvas.isDisplayable(); i++ )
			Thread.sleep( 50 );
	}

	/**
	 * Renders the current scene into an offscreen ARGB {@link BufferedImage},
	 * mirroring {@link ij3d.DefaultUniverse#takeSnapshot(int, int)} but keeping
	 * the alpha channel and clearing the buffer to fully transparent instead of
	 * the scene's Background color.
	 */
	private static BufferedImage renderOffscreen( final Image3DUniverse universe )
	{
		final GraphicsConfigTemplate3D template = new GraphicsConfigTemplate3D();
		template.setDoubleBuffer( GraphicsConfigTemplate.UNNECESSARY );
		final GraphicsConfiguration gc =
			GraphicsEnvironment.getLocalGraphicsEnvironment()
				.getDefaultScreenDevice().getBestConfiguration( template );

		final Canvas3D offScreenCanvas = new Canvas3D( gc, true );
		// If there is no Background node in the scene, Java3D clears the buffer
		// with the canvas background colour - ask for a transparent one.
		offScreenCanvas.setBackground( new Color( 0, 0, 0, 0 ) );

		final Screen3D screen3D = universe.getCanvas().getScreen3D();
		final Screen3D offScreen3D = offScreenCanvas.getScreen3D();
		offScreen3D.setSize( screen3D.getSize() );
		offScreen3D.setPhysicalScreenWidth( screen3D.getPhysicalScreenWidth() );
		offScreen3D.setPhysicalScreenHeight( screen3D.getPhysicalScreenHeight() );

		universe.getViewer().getView().addCanvas3D( offScreenCanvas );
		try
		{
			final BufferedImage image = new BufferedImage( WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB );
			final ImageComponent2D buffer = new ImageComponent2D( ImageComponent.FORMAT_RGBA, image );
			offScreenCanvas.setOffScreenBuffer( buffer );
			offScreenCanvas.renderOffScreenBuffer();
			offScreenCanvas.waitForOffScreenRendering();
			return offScreenCanvas.getOffScreenBuffer().getImage();
		}
		finally
		{
			universe.getViewer().getView().removeCanvas3D( offScreenCanvas );
		}
	}

	private static void closeUniverse( final Image3DUniverse universe )
	{
		try
		{
			if ( universe.getWindow() != null )
				universe.close();
		}
		catch ( final Throwable t )
		{
			t.printStackTrace();
		}
		try
		{
			universe.cleanup();
		}
		catch ( final Throwable t )
		{
			t.printStackTrace();
		}
	}

	private static int alphaAt( final BufferedImage image, final int x, final int y )
	{
		return ( image.getRGB( x, y ) >>> 24 ) & 0xFF;
	}

	private static void assertOpaqueCorners( final BufferedImage image )
	{
		assertEquals( 255, alphaAt( image, INSET, INSET ), "top-left corner should be opaque" );
		assertEquals( 255, alphaAt( image, image.getWidth() - 1 - INSET, INSET ), "top-right corner should be opaque" );
		assertEquals( 255, alphaAt( image, INSET, image.getHeight() - 1 - INSET ), "bottom-left corner should be opaque" );
		assertEquals( 255, alphaAt( image, image.getWidth() - 1 - INSET, image.getHeight() - 1 - INSET ), "bottom-right corner should be opaque" );
	}

	private static void assertTransparentCorners( final BufferedImage image )
	{
		assertEquals( 0, alphaAt( image, INSET, INSET ), "top-left corner should be transparent" );
		assertEquals( 0, alphaAt( image, image.getWidth() - 1 - INSET, INSET ), "top-right corner should be transparent" );
		assertEquals( 0, alphaAt( image, INSET, image.getHeight() - 1 - INSET ), "bottom-left corner should be transparent" );
		assertEquals( 0, alphaAt( image, image.getWidth() - 1 - INSET, image.getHeight() - 1 - INSET ), "bottom-right corner should be transparent" );
	}
}
