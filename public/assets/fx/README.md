# Zesty FX — formato del catalogo effetti

Formato originale di Zesty, condiviso dall'app Android (`com.example.fx`) e dalla web app.
Un effetto è una **ricetta**: una sequenza di *step*, ognuno dei quali chiama un'operazione primitiva.
Per aggiungere un effetto basta aggiungere una voce JSON in `catalog/<categoria>.json`: l'interfaccia
(cursori, colori, scelte) viene generata automaticamente dai parametri.

## Struttura

```json
{ "id": "film_warm", "label": "Pellicola calda",
  "params": [ { "id": "amount", "label": "Intensità", "min": 0, "max": 100, "default": 80 } ],
  "steps": [
    { "op": "curves", "r": [[0, 12], [128, 142], [255, 250]], "opacity": "$amount / 100" },
    { "op": "grain", "amount": 20 }
  ] }
```

- `index.json` elenca le categorie (ordine dei chip) e i file da caricare.
- `id` deve essere unico in tutto il catalogo.

### Parametri

| type | campi | valore |
|---|---|---|
| `range` (predefinito) | `min`, `max`, `default`, `step` | numero |
| `color` | `default: "#rrggbb"` | numero RGB 0..16777215 |
| `choice` | `options: [..]`, `default` (indice) | indice |
| `toggle` | `default: 0/1` | 0 o 1 |

Il parametro speciale `seed` ("Variante") cambia la casualità di grana, polvere, particelle, glitch...

### Valori negli step

- numeri, booleani, colori `#rgb`, `#rrggbb`, `#rrggbbaa`
- riferimenti a parametri: `"$amount"`, anche per i colori: `"$dark"`
- espressioni: `"$amount * 0.4 + 10"`, `"-$amount"`, `"1 - $bars / 50"`, `min()`, `max()`, `abs()`, `clamp()`
- colore calcolato: `"rgb(246 - $aged * 0.14, 238, 218)"` (canali 0..255, arrotondati e limitati)
- scelta da parametro: `{ "pick": "$mode", "from": ["left", "right"] }`

### Unità

Le distanze (raggi, dimensioni, spostamenti) sono in **u = 1% del lato corto** dell'immagine:
l'effetto appare identico sulla miniatura e sull'immagine a piena risoluzione.
Le posizioni (`cx`, `cy`, `position`) vanno da 0 a 1.

### Chiavi comuni a ogni step

| chiave | significato |
|---|---|
| `blend` | fusione del risultato sull'ingresso: normal, multiply, screen, overlay, soft-light, hard-light, darken, lighten, color-dodge, color-burn, difference, exclusion, add, subtract, hue, saturation, color, luminosity |
| `opacity` | 0..1 |
| `mask` | `{type:"radial", cx, cy, radius, feather, invert}`, `{type:"linear", position, width, feather, angle, invert}`, `{type:"luma", from, to, softness, invert}` |
| `if` | espressione: lo step si esegue solo se ≠ 0 |

`{"op": "layer", "steps": [...]}` esegue una sotto-ricetta su una copia e la fonde sopra con `blend`/`opacity`/`mask`.

## Operazioni

**Colore e tono** — `adjust` (exposure, brightness, contrast, saturation, vibrance, temperature, tint, hue, shadows, highlights, whites, blacks, gamma, fade), `matrix` (m: 20 valori), `mono` (r,g,b oppure reds…magentas), `curves` (rgb, r, g, b: punti [x,y] 0..255), `levels`, `gradient_map` (stops), `duotone`, `split_tone`, `tint`, `invert`, `solarize`, `posterize`, `threshold`, `color_keep`, `hue_replace`, `channel_swap`, `auto_levels`, `white_balance`, `fill`, `dither`

**Sfocatura e dettaglio** — `blur`, `sharpen`, `clarity`, `smooth` (filtro guidato, preserva i bordi), `motion_blur`, `zoom_blur`, `glow`, `skin_smooth`

**Artistico** — `oil` (Kuwahara), `edges` (mono, glow, overlay, color), `sketch`, `emboss`, `pixelate` (square, circle, diamond), `halftone` (mono, color, lines), `crystallize`, `warhol`, `cell_tint`

**Distorsione** — `swirl`, `bulge`, `ripple` (both, horizontal, vertical, radial), `turbulence`, `polar`, `mirror`, `kaleidoscope`, `shear`, `flip`, `rgb_shift`, `glitch`, `disperse`

**Poster e cornici** — `palette_quantize` (colors, tone, aged: palette ridotta con k-means), `mat` (passe-partout: size, bottom, color, line, lineColor, shadow — la foto viene rimpicciolita intera, nulla viene coperto), `frame` (bordo interno), `fibers` (fibre della carta)

**Luce, texture, particelle, pattern** — `vignette`, `grain`, `gradient` (linear, radial), `texture` (paper, canvas, clouds, noise, marble, concrete), `dust`, `particles` (snow, rain, bokeh, stars), `scanlines`, `pattern` (dots, lines, grid, checker, stripes, waves, zigzag, cross, weave, hex, diamonds, hearts, stars)
