# Third-Party Notices

## Create (https://github.com/Creators-of-Create/Create)

The pulse length adjustment overlay's interaction logic (the row/value coordinate
math and the hold-to-open, hover-to-scan, release-to-confirm input handling), and
the bound-reader highlight's rendering approach (drawing the outline as solid lit
cuboids rather than thin lines, the opaque-edges/translucent-face split, the color
pair and timing constants, the alpha fade curve and its cutoff, and the two-step
inset that keeps the outline flush with the block's own shape) were adapted from
Create's source code. Create's code (everything outside its own
`src/main/resources/assets/` directory) is distributed under the MIT License
reproduced below; no assets, textures, or other files from `assets/` were used.

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
