# Third-Party Notices

## Create (https://github.com/Creators-of-Create/Create)

The pulse length adjustment overlay's interaction logic (the row/value coordinate
math and the hold-to-open, hover-to-scan, release-to-confirm input handling), and
the bound-reader highlight's rendering approach (drawing the outline as solid lit
cuboids rather than thin lines, the opaque-edges/translucent-face split, the color
pair and timing constants, the alpha fade curve and its cutoff, and the two-step
inset that keeps the outline flush with the block's own shape) were adapted from
Create's source code. The sensor detection-range highlight's continuous UV tiling
(setting a face's texture coordinates to its width/height in blocks rather than a
normalized 0..1 range, so the pattern repeats via the texture's own tiling instead
of stretching, and follows a resize smoothly instead of snapping once it settles)
was adapted from the same technique in Create's `AABBOutline` renderer. Create's
code (everything outside its own `src/main/resources/assets/` directory) is
distributed under the MIT License reproduced below; no assets, textures, or other
files from `assets/` were used.

```
MIT License

Copyright (c) The Create Team / The Creators of Create

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Create: Simulated (mod id `simulated`, by the Simulated Team)

The sensor detection-range highlight's interaction pattern - arming it by
right-clicking with an item, Ctrl+Scroll growing or shrinking the selection one
cell at a time toward whichever face is being looked at, confirming/cancelling by
right/left-clicking the highlight itself rather than the original block, and
rendering it at a fixed thin width when not being looked at versus a thicker width
with a face texture when it is (two fixed states, not a fade between them) - was
adapted from the "Honey Glue" item's client-side handler in Create: Simulated
(bundled with Create: Aeronautics). Its code (outside its own asset directories,
which were not used here) is distributed under the MIT License reproduced below.

```
MIT License

Copyright (c) The Simulated Team / The Creators of Aeronautics

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
