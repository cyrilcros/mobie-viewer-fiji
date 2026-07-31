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

import ij3d.ImageWindow3D;
import ij3d.Image3DUniverse;
import org.jogamp.java3d.Transform3D;
import org.jogamp.vecmath.Vector3d;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;

/**
 * MoBIE-specific extension of {@link Image3DUniverse} that adds:
 * <ul>
 *   <li>A "MoBIE Views" menu in the 3D viewer window for quick camera
 *       orientation presets with human-readable labels.</li>
 *   <li>A composable {@link #rotateView(int, double, boolean)} API for
 *       programmatic rotation around world axes with optional reset.</li>
 * </ul>
 */
public class MoBIEUniverse extends Image3DUniverse
{
	@Override
	public void init( final ImageWindow3D window )
	{
		super.init( window );

		final JMenuBar mb = window.getJMenuBar();
		if ( mb != null )
			mb.add( createMoBIEViewsMenu() );
	}

	private JMenu createMoBIEViewsMenu()
	{
		final JMenu menu = new JMenu( "MoBIE Views" );

		menu.add( orientItem( "XY (top)",    this::rotateToNegativeXY ) );
		menu.add( orientItem( "XY (bottom)", this::rotateToPositiveXY ) );
		menu.add( orientItem( "XZ (front)",  this::rotateToNegativeXZ ) );
		menu.add( orientItem( "XZ (back)",   this::rotateToPositiveXZ ) );
		menu.add( orientItem( "YZ (left)",   this::rotateToNegativeYZ ) );
		menu.add( orientItem( "YZ (right)",  this::rotateToPositiveYZ ) );

		return menu;
	}

	private static JMenuItem orientItem( final String label, final Runnable action )
	{
		final JMenuItem item = new JMenuItem( label );
		item.addActionListener( e -> action.run() );
		return item;
	}

	// -- Composable rotation API --------------------------------------------

	/** X-axis constant (matches {@code ij3d.AxisConstants.X_AXIS}). */
	public static final int X_AXIS = 0;
	/** Y-axis constant (matches {@code ij3d.AxisConstants.Y_AXIS}). */
	public static final int Y_AXIS = 1;
	/** Z-axis constant (matches {@code ij3d.AxisConstants.Z_AXIS}). */
	public static final int Z_AXIS = 2;

	/**
	 * Rotate the view around a world axis by a given angle.
	 * <p>
	 * When {@code reset} is {@code true}, the rotation is reset to identity
	 * before applying the new rotation — this snaps to a canonical
	 * axis-aligned orientation.  When {@code false}, the rotation is
	 * composed on top of the current orientation, allowing incremental
	 * adjustments.
	 *
	 * @param axis    {@link #X_AXIS}, {@link #Y_AXIS}, or {@link #Z_AXIS}.
	 * @param degrees rotation angle in degrees (positive = CCW).
	 * @param reset   if {@code true}, reset rotation to identity first.
	 */
	public void rotateView( final int axis, final double degrees, final boolean reset )
	{
		final Transform3D rot = new Transform3D();
		if ( reset )
		{
			rot.setIdentity();
		}
		else
		{
			getRotationTG().getTransform( rot );
		}

		final Transform3D delta = new Transform3D();
		delta.rotZ( 0 ); // ensure delta is a rotation matrix
		final Vector3d vec = axisVector( axis );
		final double rad = Math.toRadians( degrees );

		// Build a pure rotation around the chosen axis and compose.
		composeAxisRotation( delta, vec, rad );
		rot.mul( delta );

		getRotationTG().setTransform( rot );
	}

	private static Vector3d axisVector( final int axis )
	{
		switch ( axis )
		{
			case X_AXIS: return new Vector3d( 1, 0, 0 );
			case Y_AXIS: return new Vector3d( 0, 1, 0 );
			case Z_AXIS: return new Vector3d( 0, 0, 1 );
			default:
				throw new IllegalArgumentException( "Invalid axis: " + axis + ". Use X_AXIS (0), Y_AXIS (1), or Z_AXIS (2)." );
		}
	}

	/**
	 * Set {@code dest} to a rotation of {@code angle} radians around
	 * {@code axis}, overwriting any existing transform in {@code dest}.
	 */
	private static void composeAxisRotation( final Transform3D dest, final Vector3d axis, final double angle )
	{
		final double c = Math.cos( angle );
		final double s = Math.sin( angle );
		final double t = 1.0 - c;

		final double[] m = new double[ 16 ];
		m[ 0 ] = t * axis.x * axis.x + c;
		m[ 1 ] = t * axis.x * axis.y + s * axis.z;
		m[ 2 ] = t * axis.x * axis.z - s * axis.y;
		m[ 3 ] = 0;

		m[ 4 ] = t * axis.x * axis.y - s * axis.z;
		m[ 5 ] = t * axis.y * axis.y + c;
		m[ 6 ] = t * axis.y * axis.z + s * axis.x;
		m[ 7 ] = 0;

		m[ 8 ] = t * axis.x * axis.z + s * axis.y;
		m[ 9 ] = t * axis.y * axis.z - s * axis.x;
		m[ 10 ] = t * axis.z * axis.z + c;
		m[ 11 ] = 0;

		m[ 12 ] = 0;
		m[ 13 ] = 0;
		m[ 14 ] = 0;
		m[ 15 ] = 1;

		dest.set( m );
	}
}
