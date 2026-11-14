# Android TV API 30 backdrop

API 30 TV uses a software blur of the visible Compose content. This path is enabled only for TV and the `Drawn` platform tier, outside fullscreen playback. API 31+ keeps its existing accelerated path, and phone/tablet entry points do not create this producer.

## Recording and isolation

`TvBackdrop` records the navigation content subtree into a `GraphicsLayer`. The navigation rail is a sibling of that subtree. Focus and standing glass nodes inside the subtree record their foreground separately; their glass, halo and rim are replayed after the base recording, followed by the foreground. Parent surfaces replay before descendant surfaces. Focus overlays use their actual root coordinates, scale and clipped visible bounds. Cached child graphics layers therefore contain foreground/base content without the glass material. This does not capture the composed window or recursively sample the preceding glass frame.

The source is rasterized at a maximum of 256 pixels on its longer side. `GraphicsLayer.toImageBitmap()` supplies the bitmap; pixel readback, three separable box-blur passes with radius 4, and bitmap upload run on `Dispatchers.Default`. Display-list recording and platform graphics readback remain on the Compose coroutine context. Readback time can include the platform's asynchronous GPU copy; it is measured separately from worker processing.

## Invalidation and cost

There is one producer job and a conflated request channel. Readbacks start at least 250 ms apart. Source drawing and isolated focus-content drawing register scoped snapshot read dependencies. Changes to those dependencies and actual placement changes request an update. Bitmap publication and unrelated playback progress are outside those subscriptions. Requests wait for pending child-layer drawing before readback. Ambient artwork completion requests a frame and one final frame after its independent crossfade.

Route/size changes clear the published frame and invalidate its generation. The generation is checked again after pending child frames complete and before readback. A result is published only when its generation and source origin still match. New content arriving during worker processing keeps one conflated follow-up request; the current bounded sample can still publish, so animation cannot starve the material. Static content reuses the published bitmap; no display-frame capture loop runs.

Materials sample in source/root coordinates. Areas outside the source, including the collapsed rail gutter, extend the nearest source edge. A rail partly overlapping content preserves the interior alignment instead of stretching the cropped picture. API 30 live material uses a 12% neutral veil, thin sheen and rim. During initial capture it uses a 3.5% white surface. It has no fixed black rail strip or dense 62% rail scrim on this path.

Debug timing uses the `TvBackdrop` log tag: `capture`, `readbackMs`, `blurMs`, `pixels` and `generation`, emitted on the first capture and every twentieth capture. Failures log a count and exception type only. No titles, routes, account data, artwork URLs or pixel data enter these messages.

## Geometry

Settings rows use a 1 dp inset within the 20 dp rounded group: first and last rows have the corresponding 19 dp inner corners; middle edges are square. TV hearts retain their existing action target while drawing a centered 24 dp glyph for both checked states. Home artwork focus anchors attach to the actual image bounds and use the image's card/circle shape; the layout gutter stays separate from the visual rim.

## Content transitions

Home, Search, Settings and the playlist content containers switch immediately on the software backdrop path. Their visibility transitions containing extracted focus surfaces also use no enter/exit transition. The sibling glass pass cannot inherit an arbitrary ancestor's alpha; immediate content replacement keeps the base and material consistent during loading, filter, settings and header changes. This tradeoff is scoped by the active software producer. Mobile and API 31+ retain their existing content transitions. Focus springs, unrelated loading indicators, search placeholder animation and ambient artwork crossfades retain their motion.

## Evidence and limits

JVM tests use the production recording, worker blur, source sampler and real Compose renderer with coloured bands, text, focus, source offsets and cached focus layers. An independent nonfocused cached child changes its draw-state colour and must publish the new green pixels. Additional tests cover a once-composited translucent card, standing-panel ancestor clipping, actual first/last Settings corners at density 3, and software-only immediate content transitions. Device performance and appearance remain separate acceptance requirements. Physical Home, Search, Settings and playlist captures, focus/scroll/Back/IME regression checks, and device timing must be recorded before claiming visual completion.

This is a sampled backdrop, capped at four updates per second rather than continuous video blur. A 256-pixel image loses fine detail intentionally. Compose display lists cannot include a separate video `SurfaceView`; fullscreen playback disables this producer. Arbitrary independent graphics-layer property animations need an explicit content invalidation if they do not read observed source state or change source placement; the ambient crossfade has that explicit completion request.

The recording APIs follow the official [Compose graphics modifiers guide](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers) and [GraphicsLayer reference](https://developer.android.com/reference/kotlin/androidx/compose/ui/graphics/layer/GraphicsLayer). A headless Skia render verifies the composition boundary; it does not establish API 30 OEM GPU readback behavior or physical visual acceptance.
