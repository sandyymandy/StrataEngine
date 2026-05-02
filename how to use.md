# StrataEngine UI

This MD includes a small retained-mode UI system (a tree of nodes) plus a demo overlay

## Where it lives

- UI nodes: `src/main/java/engine/strata/client/frontend/ui/`
- Text rendering: `src/main/java/engine/strata/client/frontend/render/renderer/font/TextRenderer.java`
- Tutorial overlay + input handling: `src/main/java/engine/strata/client/frontend/ui/UiManager.java`
- Hooked into the render loop: `src/main/java/engine/strata/client/frontend/ClientFrontEnd.java`

## Concepts

Everything is a `UiNode` in a parent/child hierarchy:

- `FrameNode`: a rectangular container (optional background).
- `TextNode`: draws text at its anchored point (uses STB easy font).
- `ButtonNode`: a clickable `FrameNode` that changes color on hover/press and calls a callback.
- `ImageNode`: draws a texture (`Identifier`) stretched to its rect.

## Transparency

UI rendering enables alpha blending. Use alpha in:

- frame/button background (`background(r,g,b,a)`)
- text color (`color(r,g,b,a)`)
- image tint (`ImageNode.color(r,g,b,a)`) and/or PNG alpha channel

### Layout (anchor + pivot)

Each node computes its pixel rectangle from its parent:

- `anchor` (0..1): where the node attaches within the parent rect
- `pivot` (0..1): which point inside the node is placed at the anchor
- `offsetPx`: pixel offset applied at the anchor
- `size`: size relative to parent (0..1)
- `sizePx`: additional absolute pixel size

Position formula (top-left output):

```
absSize = parentSize * size + sizePx
anchorPos = parentPos + parentSize * anchor + offsetPx
absPos = anchorPos - pivot * absSize
```

Example: center a button inside the screen:

```java
ButtonNode button = new ButtonNode();
button.anchor().set(0.5f, 0.5f);
button.pivot().set(0.5f, 0.5f);
button.sizePx().set(220, 40);
```

## Demo overlay

By default, the UI manager creates:

- A top-left button that toggles the label mode
- A label that shows either FPS or memory
- A toggled image that changes once the button is pressed

You can change it in `UiManager.buildDemoOverlay()`.

## Adding your own UI

In `UiManager`:

```java
FrameNode root = ui.root();

FrameNode panel = new FrameNode()
        .background(0, 0, 0, 0.4f);
panel.anchor().set(0, 0);
panel.pivot().set(0, 0);
panel.offsetPx().set(10, 100);
panel.sizePx().set(260, 120);

TextNode title = new TextNode()
        .text("Hello UI")
        .scale(1.0f);
title.anchor().set(0.5f, 0.0f);
title.pivot().set(0.5f, 0.0f);
title.offsetPx().set(0, 8);

panel.addChild(title);
root.addChild(panel);
```

## Images

```java
ImageNode icon = new ImageNode()
        .texture(Identifier.ofEngine("ui/my_icon"))
        .color(1, 1, 1, 1);
icon.anchor().set(1.0f, 0.0f);
icon.pivot().set(1.0f, 0.0f);
icon.offsetPx().set(-10, 10);
icon.sizePx().set(32, 32);
root.addChild(icon);
```

The engine looks up textures under `assets/<namespace>/textures/<path>.png`.
MUST BE PNG.