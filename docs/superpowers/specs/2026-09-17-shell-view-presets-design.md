# Shell-axis view presets for the MoBIE 3D viewer — Design

Date: 2026-09-17
Status: Approved (design)
Branch: `feature/shell-views` (off `8a6d314f`, the camera-work tip)
Related: `platybrowser-project-2025/2025_scripts/shell_halves/docs/shell-ovoid-orientation.md`

## Goal

Add camera-orientation presets to the Fiji 3D viewer window that align the view
exactly with the Platynereis shell's measured principal axes (AP/LR/DV), so one
click gives a true full frontal view, side profile, dorsal/ventral view, etc.

## Context

- The 3D viewer is `ij3d.Image3DUniverse`. MoBIE wraps it in `MoBIEUniverse`
  (already on this branch), which adds a **"MoBIE Views"** menu to the viewer
  window and a `rotateView(axis, degrees, reset)` API.
- Existing menu items snap to world X/Y/Z, plus Z±45° in-plane spins. Those
  cannot produce the shell views: the ovoid is not axis-aligned — AP is ~32° off
  Z, and LR/DV are tilted out of the XY plane.
- The shell's true axes, in voxel `(x, y, z)` coordinates, from the orientation
  doc:

  | axis | direction |
  |---|---|
  | AP (main) | `(-0.359, -0.388, 0.849)` |
  | LR (left–right) | `(0.633, -0.770, -0.084)` |
  | DV (dorso-ventral) | `(0.686, 0.507, 0.522)` |

  These are orthonormal (max off-diagonal dot product `2e-4`) and right-handed
  (`det = +1`).
- The shell sources are pure-scale BDV N5 (`diag(0.32, 0.32, 0.4)`), with no
  rotation, so the documented voxel directions are usable as view directions.

## Design

### Rotation construction

A camera view is defined by the world direction it looks along, `f`, and its
screen-up direction, `u`. With the viewer convention that an identity rotation
looks along `-Z` with `+X` screen-right and `+Y` screen-up, the view rotation is

```
R = [ f×u , u , -f ]        (columns)
```

applied via `univ.getRotationTG().setTransform(R)`. This is the composition of
the three rotations that map the world frame onto the shell frame, expressed as
one orthonormal basis change. `viewTransformer.rotate()` composes onto the same
`rotationTG`, so this is consistent with the base-class presets.

### Frame choice

Use the documented **voxel-space** triad directly; it is already orthonormal and
right-handed, so no re-orthonormalization is needed. An alternative is to map
through the calibration `diag(0.32, 0.32, 0.4)` and Gram–Schmidt; that shifts DV
by a few degrees and is not needed for now.

### Presets

A new **"Shell views"** submenu under "MoBIE Views":

| Item | look along `f` | up `u` |
|---|---|---|
| Frontal | `+AP` | `+DV` |
| Rear | `-AP` | `+DV` |
| Left profile | `+LR` | `+DV` |
| Right profile | `-LR` | `+DV` |
| Dorsal | `-DV` | `+AP` |
| Ventral | `+DV` | `+AP` |

Naming caveat: the doc fixes eigenvector signs deterministically but notes that
left/right and front/back are viewer conventions only. The labels may need to be
swapped after a first visual check.

### Components

- **`ShellFrame`** (new, `org.embl.mobie.lib.volume`): the three axis constants
  and a factory for the six view rotations. Pure math, no viewer dependency, so
  it is unit-testable headlessly.
- **`MoBIEUniverse`**: add `lookAlong(Vector3d forward, Vector3d up)` and build
  the "Shell views" submenu from `ShellFrame`. The existing world-axis items are
  unchanged.

### Error handling

`lookAlong` rejects zero-length or parallel `f`/`u` with
`IllegalArgumentException`. Inputs are internal constants, so this is defensive
only.

## Verification

1. `ShellFrameTest` (JUnit, headless): the triad is orthonormal with `det = +1`;
   for each preset `R` is orthonormal, `det = +1`, `R·(0,0,-1) = f`, and
   `R·(0,1,0) = u`.
2. `mvn -DskipTests package` compiles.
3. Visual smoke test in Fiji: open the platybrowser project, show a shell source
   in the 3D viewer, click each preset; confirm frontal/side/dorsal and swap
   labels if needed.

## Out of scope

- No changes to the platybrowser dataset (the frame is not declared in
  `dataset.json`).
- No changes to the transparent-background test or segment-mesh parallelization
  (neither is on this branch).
- No calibrated-space re-orthonormalization.
- No fitting or centering — orientation only.
