# Sound sources

The protocol's one sound, a tube firing, is cut from a recording taken from freesound.org under
the Creative Commons Zero (CC0 1.0) public-domain dedication, which permits use, modification and
redistribution without attribution. The author is credited here anyway, because they deserve it.
The file in `src/` is the recording as downloaded (Freesound's high-quality Vorbis preview);
`build.py sounds` cuts it into `src/main/resources/assets/submersibles/sounds/` with the shared
`tools/sound/cutlib.py`. Each submarine mod ships its own engine loop.

| File | Title | Author | Freesound page | License |
|---|---|---|---|---|
| `336469-underwater-torpedo-pass-bys.ogg` | Underwater_torpedo pass bys_CsG.wav | csaszi | https://freesound.org/people/csaszi/sounds/336469/ | CC0 1.0 |

| Shipped sound | Built from |
|---|---|
| `torpedo_launch.ogg` | 336469, 13.28 to 14.18 s: the eighth of its nineteen designed pass-bys (made in Alchemy), the one whose last 6 dB before its peak come quickest (20 ms) and most of it under 300 Hz; cut from 0.3 s before the peak so it starts hard and goes away, faded in over 15 ms and out over 120 ms. Played at a pitch drawn from 0.92 to 1.08 |

Chosen over craigsmith's "G45-34-Reverberant Torpedos" (438736), whose launches come tangled with
whistles, explosions and a spring reverb.
