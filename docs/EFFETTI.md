# Zesty — nuovo ordinamento degli effetti

Documento di lavoro (12/11/2026), costruito sul documento di Marco `effetti.txt`. **Ogni voce è riportata, nessuna è riassunta o tolta.**
Tutto è ricreato a modo nostro: i nomi dei registi e delle opere indicano un'*ispirazione* di colore e luce; nell'app i filtri hanno nomi propri (es. "Amélie" diventa "Parigi sognante"), senza loghi, immagini o materiali dei film.

## Come si legge

**Realizzazione:**
- **R** = ricetta del motore Zesty FX con le operazioni che esistono già.
- **R+** = ricetta più una nuova operazione da scrivere (indicata tra parentesi).
- **IA-G** = la parte che *aggiunge o reinventa oggetti* (rampicanti, ingranaggi, geroglifici, forme organiche) richiede un modello generativo. Si fa in due livelli:
  1. un **Look**, subito: colore, luce, contrasto, grana, tratto, che rendono l'atmosfera senza inventare nulla;
  2. una **Reinterpretazione IA**, facoltativa: pacchetto generativo pesante, previsto solo per la webapp su computer con GPU; da valutare (vedi §11).

**Extra:**
- **bande** = bande cinema facoltative (2.39:1, 1.85:1, 4:3 con bordo pellicola);
- **bordo** = cornice tipica dello stile;
- **3L** = i tre livelli dei Poster (§4.5);
- **pallini** = varianti di colore rapide sotto il filtro.

**Font:** font suggerito (tutti a licenza libera OFL, scaricati solo se li scegli; vedi §12).

---

## 0. Ordine delle categorie (chip nella schermata Effetti)

1. Correzione · 2. Bianco e nero · 3. **Cinema** · 4. **Analogico e pellicola** · 5. **Fumetti** · 6. **Poster e stampa** · 7. **Arte e stili** · 8. **Specchi e illusioni** · 9. Glitch e ottica · 10. Colore · 11. Luce · 12. Sfocatura · 13. Texture e grana · 14. Atmosfera · 15. Pattern · 16. Ritratto · 17. **Social e piattaforme**

In grassetto le categorie nuove o rifatte. Pop Art si distribuisce tra Fumetti (punti, retini) e Poster (griglia Warhol, serigrafia); Vintage confluisce in Analogico; Distorsione confluisce in Specchi e illusioni.

### Effetti già presenti: dove vanno

| Categoria attuale | Effetti | Nuova collocazione |
|---|---|---|
| Correzione (15) | tutti | Correzione (invariata) |
| B&N (21) | Classico … Grafite; *Cianotipia* | B&N; **Cianotipia** → Analogico §3.2 (diventa famiglia con varianti); *Noir* resta, e il "Noir classico" del cinema §1.2 è la sua versione con bande e grana anni '40 |
| Pellicola (18) | tutti | Analogico §3.1 |
| Vintage (14) | tutti | Analogico §3.3 |
| Cinema (15) | Teal & Orange, Blockbuster, Codice verde, Neon noir… | Cinema §1, accanto ai nuovi; *Codice verde* = "Codice verde" (Matrix) §1.3; *Deserto* si fonde con "Deserto infuocato" §1.3 come variante |
| Pop Art (18) | Pop 4/9 riquadri, Quattro tinte, Serigrafia, Poster audace | → Poster §4 |
| Pop Art | Punti fumetto, Retino, Retino a colori/linee, Fumetto anni '60, Neon pop | → Fumetti §2 |
| Pop Art | Mosaico, Punti colorati, Rombi, Cristalli, Vetrata, Griglia di colore | → Arte e stili §5.6 (mosaici e vetrate) |
| Disegno (30) | Matita … Diffusione | Arte e stili §5.5 (tecniche di disegno); *Fumetto, Cartoon, Inchiostro, Contorni* → Fumetti §2 |
| Distorsione (15) | Vortice, Fisheye, Pizzica, Caricatura, Onde, Acqua, Liquido, Piccolo pianeta, Tunnel, Specchio, Caleidoscopio, Inclina, Capovolgi, Dispersione, Vetro smerigliato | Specchi e illusioni §6 (ognuno nella sua sezione) |
| Glitch (14) | tutti | Glitch e ottica §7 |
| Colore, Luce, Sfocatura, Texture, Atmosfera, Pattern, Ritratto | tutti | invariati |
| Poster (13) | Poster vintage … Cartolina | Poster §4; le **cornici** (bianca, nera, colorata, istantanea, passe-partout, cartolina) diventano il livello Cornice §4.5, utilizzabile con qualsiasi effetto |

---

## 1. Cinema

Ogni filtro ha: **Intensità** · **Grana** · **bande** facoltative con il formato tipico dello stile (2.39:1, 1.85:1, 1.37:1). Come al cinema, le bande coprono i margini sopra e sotto; si possono spegnere.

### 1.1 I grandi autori (stili iconici)
| Nome in app | Ispirazione | Colore e luce | Real. | Extra | Font |
|---|---|---|---|---|---|
| Acciaio | Nolan, freddo e realistico | toni desaturati, ombre profonde, dominanti blu acciaio e grigio ardesia, contrasto molto netto | R | bande 2.39 (o 1.43 IMAX) | Bebas Neue |
| Pastello simmetrico | Wes Anderson | gialli senape, rosa cipria, verde menta, azzurro cielo, basso contrasto, luci morbide, effetto fiabesco e retrò | R | bande 1.85, guida di simmetria facoltativa | Futura-like: Jost |
| Sabbia epica | Villeneuve | palette mono o bicromatica (ocra, sabbia, arancio bruciato contro blu profondo), alte luci diffuse, atmosfera polverosa | R (polvere) | bande 2.39 | Montserrat Light |
| Neon malinconico | Wong Kar-wai | verdi smeraldo intensi, rossi profondi, luci al neon sfocate, ombre calde | R (glow + mosso leggero) | bande 1.66 | Noto Serif |
| Thriller clinico | Fincher | dominanti verde-giallastre o ciano scuro, ombre molto chiuse, atmosfera tesa | R | bande 2.39 | Oswald |
| Pulp | Tarantino | colori saturi, rossi accesi, gialli caldi, incarnati dorati, grana visibile, pellicola 35 mm anni '70-'90 | R | bande 2.39 + **bordo pellicola** | Anton |
| Gotico | Burton | quasi monocromo: blu notte, grigi freddi, bianchi pallidi, rari accenti accesi | R+ (*color_keep* su un colore scelto) | vignetta | Creepster |
| Parigi sognante | Jeunet (Amélie) | forte dominante verde smeraldo e giallo caldo, ombre morbide, colori ricchi | R | bande 2.39 | Playfair Display |
| Fantasy barocco | del Toro | blu-verde acqua nelle ombre, oro ambrato nelle luci, ombre profonde | R | vignetta | Cinzel |
| Simmetria clinica | Kubrick | colori primari puliti (rossi, blu, bianchi puri), contrasto bilanciato, luci fredde o tungsteno nette | R | bande 1.85, guida di simmetria | Futura-like: Jost |
| Epico fumetto | Snyder | fortemente desaturato, ombre pesanti, alto contrasto, dominanti grigio-azzurre o bronzo | R (clarity forte) | bande 2.39 | Bebas Neue |

### 1.2 Generi ed epoche (per chi non conosce i registi)
| Nome in app | Descrizione | Real. | Extra | Font |
|---|---|---|---|---|
| Blockbuster | teal nelle ombre, arancio sugli incarnati (già presente: rifinito) | R | bande 2.39 | Bebas Neue |
| Noir classico | B&N altissimo contrasto, neri puri, luci dure, grana anni '40 | R | bande 1.37 (Academy) | Special Elite |
| Anni '70 Kodak | caldi e leggermente sbiaditi, neri sollevati verso marrone, rossi e gialli forti | R | bordo pellicola | Cooper-like: Bitter Black |
| Cyberpunk | alte luci magenta, viola, ciano elettrico, ombre molto scure | R | bande 2.39 | Orbitron |
| Indie | toni naturali e caldi, contrasto morbido, luce dorata da golden hour | R | bande 1.85 | Lora |

### 1.3 Estetiche di culto e movimenti
| Nome in app | Descrizione | Real. | Extra | Font |
|---|---|---|---|---|
| Codice verde | dominante verde acido su tutto, neri profondi, contrasto alto (ex "Codice verde" rifinito) | R | bande 2.39 | Share Tech Mono |
| Euforia | alte luci violacee, blu elettrico, magenta, incarnati caldi, bagliori diffusi (halation, bloom) | R (glow + halation) | — | Syne |
| Deserto infuocato | cieli ciano elettrico contro terre arancio e ocra ipersature: teal & orange estremo | R | bande 2.39 | Anton |
| Nouvelle Vague | B&N morbido, neri leggermente grigi, grana fine, luce naturale | R | bande 1.37 | Libre Baskerville |
| Horror anni '80 | neon rosso sangue e blu intenso, ombre scure, bagliore da VHS | R + glitch leggero | bordo VHS | Creepster |

### 1.4 Arena brutalista (ispirazione: l'arena in bianco e nero di Dune: Parte Due)
- **Look (R+, nuova op `infrared`)**:
  - simulazione dell'infrarosso vicino: le zone verdi e la pelle diventano chiare, i cieli pallidi e luminosi;
  - neri schiacciati e mezzitoni desaturati, praticamente niente colore;
  - controluce intenso e alte luci bruciate;
  - foschia atmosferica e pulviscolo fine;
  - grana leggera e una lieve morbidezza ottica.
- **Parametri:** Intensità infrarosso, Neri, Bagliore, Foschia, Polvere, Grana.
- **Extra:** bande 2.39 ultra-larghe. **Font:** Cinzel.
- **Reinterpretazione IA (IA-G):** il "mondo brutalista monumentale", con scala e architetture reinterpretate, secondo i due prompt del documento, conservati in `docs/prompt/` per la fase generativa.

### 1.5 Neo-noir illustrato (serie a un solo colore di luce)
Base comune:
- palette scura e desaturata, con **una sola luce viva**;
- luce di contorno e controluce drammatici;
- foschia di profondità;
- linee nette tipo inchiostro (*edges* + *posterize* morbido), grana.

Parametri comuni: **Colore della luce** (pallini), Silhouette, Foschia, Inchiostro, Grana.

| Nome in app | Luce | Look | Elementi aggiunti (solo IA-G) | Font |
|---|---|---|---|---|
| Neo-noir rosso | rosso/arancio vivo su teal scuro | R+ (`rim_light`) | — | Anton |
| Neo-noir azzurro | ciano/blu elettrico su antracite | R+ | — | Oswald |
| Neo-noir ottanio | verde petrolio/smeraldo | R+ | rampicanti e radici sulle strutture, muschio bioluminescente, cemento crepato con vegetazione | Oswald |
| Viola tossico | viola/ultravioletto + nebbia chimica | R+ | — | Syne |
| Viola tossico steampunk | come sopra | R+ | ingranaggi in ottone, valvole, lamiere rivettate, sfiati di vapore, tubi di rame | Cinzel Decorative |
| Giallo cyberpunk | neon giallo/ambra + pioggerella | R+ | frammenti olografici riflessi, cavi e circuiti, glitch ai bordi della luce, droni, insegne nella nebbia | Orbitron |

### 1.6 Varianti di stile della serie (stessa composizione e posa)
| Nome in app | Descrizione completa | Real. | Font |
|---|---|---|---|
| Spazio liminale | fluorescente piatto senza ombre, giallo-beige malato, desaturato e "itterico", grana analogica/VHS, sovraesposto e velato come da usa e getta, nessuna fonte di luce visibile, quiete inquietante | R (look completo, nessun elemento da aggiungere) | VT323 |
| Sin City | B&N puro senza mezzitoni, chiaroscuro d'inchiostro, ombre a pozze, alte luci bruciate, **un solo elemento a colore** (rosso sangue / blu elettrico / giallo acido), silhouette nette, pioggia | R+ (`color_keep` + `threshold` morbido); il colore isolato si sceglie toccando la foto | Bangers |
| Brutalista | B&N architettonico (brutalismo francese, razionalismo italiano), cemento a vista, volumi monumentali, sole duro con ombre grafiche, nitido, da still anni '70 | R (+ texture cemento) | Archivo Black |
| Ukiyo-e | xilografia giapponese notturna: indaco dominante, campiture piatte senza modellato, contorni neri netti, nebbia in fasce orizzontali sfumate (bokashi), sbavature di stampa, carta calda, accento oro pallido o bianco per luna e lanterne | R+ (`flat_regions` + `edges` + bande di nebbia) | Sawarabi Mincho |
| Liberty | linee organiche a colpo di frusta, motivi floreali, colori a gemma (verde pavone, oro, viola tenue), cornici ornamentali | Look R + cornice ornamentale; motivi floreali sugli edifici = IA-G | Cinzel Decorative |
| Neoliberty italiano | torri di ispirazione medievale, piani superiori a sbalzo, pietra scura e mattone, silhouette da fortezza | Look R (toni); forme architettoniche = IA-G | Cinzel |
| Vittoriano | nebbia densa alla luce ambrata dei lampioni a gas, ferro battuto, mattoni anneriti, archi gotici, seppia scuro, Londra dickensiana | Look R (luce, nebbia, seppia); ferro e gargoyle = IA-G | IM Fell English |
| Barocco | tenebrismo caravaggesco: una sola luce che scolpisce il soggetto nel buio, accenti oro e cremisi, pennellata a olio | R+ (`rim_light`, `oil`) | Cormorant Garamond |
| Futurista | linee di movimento, sagome moltiplicate e fratturate, piani geometrici, toni metallici | R+ (`echo` = copie sfalsate + `shatter`) | Michroma |
| Art déco | simmetria, raggiere e chevron, nero/oro/cromo, superfici lucide | R + cornice art déco | Poiret One |
| Espressionismo tedesco | ombre dipinte geometriche, architettura storta, contrasto B&N teatrale | R+ (`skew_warp` + ombre a forme) | UnifrakturCook |
| Vaporwave | cielo sfumato magenta-ciano, griglia neon, scanline VHS, aberrazione cromatica | R+ (`sky_gradient` con maschera del cielo, griglia prospettica) | Monoton |
| Costruttivismo / Bauhaus | solo rosso, nero e bianco, diagonali aggressive, forme geometriche in sovrapposizione | R + forme in sovrapposizione | Oswald / Rubik Mono One |
| Solarpunk | giorno caldo, verde rigoglioso integrato, ora dorata, palette fiduciosa | Look R; vegetazione e pannelli = IA-G | Quicksand |
| Metafisica | ispirata a de Chirico: architetture vuote, luce piatta del pomeriggio, ombre lunghissime impossibili, ocra, terracotta, rosa polvere contro blu-viola, prospettiva onirica | Look R (palette, ombre allungate con op `long_shadow`); architetture = IA-G | Cormorant Garamond |
| Dieselpunk | atmosfera industriale-militare anni '20-'40, ferro rivettato, fumo, verde oliva, ruggine, ottone, fasci di fari nello smog | Look R (palette, fasci di luce, smog); macchinari e dirigibili = IA-G | Stardos Stencil |
| Surreale decadente | ispirato a Beksiński: atmosfera apocalittica in ruggine, osso e seppia, nebbia, luce diffusa senza fonte, materia pittorica | Look R (palette, nebbia, texture olio); forme organiche e biomeccaniche = IA-G | IM Fell English |

---

## 2. Fumetti

Ogni filtro ha **Intensità**, più i due cursori suggeriti nel documento:
- **Spessore tratto:** quanto pesano i contorni;
- **Retino:** dimensione dei puntini o dei tratteggi.

Con questi si sceglie quanto disegno e quanta foto.

### 2.1 Grandi tradizioni
| Nome in app | Descrizione | Real. | Font |
|---|---|---|---|
| Manga | contrasto altissimo: grigi trasformati in bianco, nero pieno o retino a puntini (screentone), contorni sottili e puliti | R+ (`screentone`) | Zen Maru Gothic |
| Comics anni '50-'60 (Golden/Silver Age) | primari accesi, contorni neri spessi, puntini Ben-Day, carta ingiallita | R | Bangers |
| Linea chiara | colori piatti e luminosi, ombre minime, contorni neri sottili e uniformi (stile europeo) | R+ (`flat_regions`) | Comic Neue |
| Chiaroscuro all'italiana (stile dei fumetti Bonelli) | B&N drammatico, tratteggi incrociati nelle ombre, neri d'inchiostro | R+ (`hatching`) | Special Elite |

### 2.2 Stili visivi e autoriali
| Nome in app | Descrizione | Real. | Font |
|---|---|---|---|
| Noir estremo | B&N brutale senza mezzitoni, un solo colore isolato (rosso, giallo o blu); è lo stesso motore di "Sin City" in Cinema, qui senza bande | R+ | Bangers |
| Multiverso pop | colori vibranti, aberrazione cromatica rosso/ciano, puntini grandi nelle ombre, linee d'azione | R+ (`speed_lines`) | Bangers |
| Graphic novel ad acquerello | dettagli ammorbiditi in macchie morbide e trasparenti, contorni accennati a matita o china, carta ruvida | R (oil + acquerello + edges leggeri) | Patrick Hand |
| Cyber-manga | rossi neon, blu elettrici, tungsteno forte, ombre nette, bagliore da animazione sci-fi anni '80-'90 | R | Orbitron |
| Ombre totali (alla Mignola) | ombre vastissime e completamente nere, pochi dettagli illuminati in colori terrosi, rossi cupi, blu freddi | R+ (`threshold` sulle ombre + palette) | Germania One |

### 2.3 Carta e stampa (tocco finale)
| Nome in app | Descrizione | Real. | Font |
|---|---|---|---|
| Carta giornale / Pulp | colori leggermente desaturati, neri grigi, trama porosa della carta economica, sbavature fuori registro | R+ (`misregister`) | Special Elite |
| Webtoon | colori luminosi e puliti, sfumature digitali morbide, luce diffusa, pelle levigata | R (smooth + skin_smooth) | Nanum Gothic |

### 2.4 Coppia colore / china (dal confronto nel documento)
| Nome in app | Descrizione | Real. | Font |
|---|---|---|---|
| Fumetto a colori | toni naturali semplificati in campiture piatte posterizzate, contorni scuri sottili su volti, pieghe, dettagli | R+ (`flat_regions`) | Comic Neue |
| China | niente colore, pochi mezzitoni, bianchi puri e neri profondi, ombre a tratti netti, tratteggi incrociati, macchie d'inchiostro | R+ (`hatching`) | Special Elite |

Dalla vecchia Pop Art e dal Disegno: Punti fumetto, Retino, Retino a colori, Retino a linee, Fumetto anni '60, Neon pop, Fumetto, Cartoon, Inchiostro, Contorni.

---

## 3. Analogico e pellicola

### 3.1 Pellicole
Tutte quelle attuali (Pellicola calda … Alone rosso). Con **bordo pellicola** facoltativo: tacche e numerazione disegnate da Zesty, nessun marchio reale.

### 3.2 Cianotipia (una famiglia con 5 varianti a pallini)
| Variante | Descrizione | Real. |
|---|---|---|
| Classica (blu di Prussia) | ombre blu intenso, alte luci bianco carta, carta fibrosa, bordi a pennello | R + bordo `brush_edge` (nuova op) |
| Virata al tè | carta beige calda, blu più scuro e violaceo, effetto antico e botanico | R |
| Al caffè | toni marrone scuro, bruciato e ambra, con il contrasto chimico tipico | R |
| Solare (sovraesposta) | neri metallici, quasi argento o bronzo, mezzitoni blu polvere | R |
| Doppia (bicromia) | blu ciano nelle ombre, ocra o rosso mattone nei mezzitoni | R |

La cianotipia compare anche in **Poster e stampa** come scorciatoia alla stessa famiglia: non è un doppione, è lo stesso filtro.

### 3.3 Vintage
Tutti quelli attuali (Vintage … Menta retrò), più gli altri antichi processi già presenti in B&N (Dagherrotipo, Platino, Selenio).

---

## 4. Poster e stampa

Ogni poster ha:
- **pallini di palette**, per passare per esempio da rosso/blu a giallo/viola nello stesso stile;
- **Bordo bianco on/off**, da locandina o passe-partout;
- i **3 livelli** (§4.5).

### 4.1 Stili iconici e storici
| Nome in app | Descrizione | Real. | Font |
|---|---|---|---|
| Speranza (pop politico) | 4 colori piatti: blu scuro nelle ombre, rosso acceso nei mezzitoni, azzurro e beige crema nelle luci; ideale per ritratti frontali | R (`palette_quantize` a palette fissa) | Oswald |
| Serigrafia pop | ombre nere marcate e campiture fluo o acide (fucsia, giallo limone, ciano) leggermente sfalsate | R+ (`misregister`) | Anton |
| Propaganda costruttivista | rosso mattone, nero carbone, bianco avorio, ombre geometriche, grana da stampa d'epoca | R | Rubik Mono One |
| Art Nouveau (alla Mucha) | pastello caldi e dorati (ocra, verde salvia, rosa antico), contorni morbidi, atmosfera decorativa, **cornice ornamentale** | R + cornice | Cinzel Decorative |

### 4.2 Musica, concerti e cultura pop
| Nome in app | Descrizione | Real. | Font |
|---|---|---|---|
| Zine fotocopiata (punk) | B&N sporco a soglia, grana pesante, graffi, volantino underground anni '70-'80 | R | Rubik Glitch / Special Elite |
| Psichedelico anni '60 | complementari vibranti (viola, arancio fluo, verde lime), sfumature liquide | R+ (`liquify_noise`) | Monoton |
| Locandina anni '80 (aerografo) | colori pittorici, bagliori caldi ad aerografo, blu notte, arancio dorato | R (glow + oil) | Righteous |
| Rave brutalista | colori acidi su nero o grigio cemento, stampa termica, nitidezza estrema | R | Space Mono |

### 4.3 Tecniche di stampa e texture
| Nome in app | Descrizione | Real. | Font |
|---|---|---|---|
| Risograph | 2-3 inchiostri sovrapposti (es. rosa fluo e blu teal), grana puntinata, piccoli errori di registro | R+ (`misregister`); esiste "Risografia", da rifinire | Space Grotesk |
| Bicromia (duotone) | ombre su un colore scuro, luci su un colore brillante, look musicale | R (`duotone`), con pallini | Montserrat |
| Manifesto affisso | sbiadito dal sole, pieghe, bolle d'aria, piccoli strappi, carta incollata al muro | R+ (`paper_folds`) | Oswald |
| Minimal svizzero | sfondo neutro o monocromo, soggetto nitido (usa Scontorna/IS-Net), griglia pulita, bordi bianchi asimmetrici | R + maschera soggetto | Inter |

### 4.4 Tecniche avanzate, composizione e storia
| Nome in app | Descrizione | Real. | Font |
|---|---|---|---|
| Xilografia / linoleografia | niente sfumature: intagli direzionali che seguono i volumi, campiture piene nero o colore su carta avorio | R+ (`woodcut` con linee lungo il gradiente) | IM Fell English |
| Mezzitoni tipografici | linee parallele che si ispessiscono nelle ombre (stampa bancaria) o grandi cerchi concentrici | R+ (`halftone` linee/cerchi concentrici) | Libre Baskerville |
| Cianotipia (scorciatoia) | vedi §3.2 | — | — |
| Letterpress | 2 colori netti, effetto rilievo dell'inchiostro pressato nel cartoncino | R (emboss sulla maschera dei colori) | Abril Fatface |
| Scontrino (stampa termica) | monocromo granuloso a micro-punti neri o blu, bande di scorrimento, bordo seghettato | R+ (bordo `zigzag_edge`) | Space Mono |
| Acid graphics | posterizzazione estrema, mappa termica, inversione parziale, nitidezza metallica, mirini, codici a barre, griglie | R + elementi grafici nostri | Space Mono |
| Collage dadaista | soggetto isolato (Scontorna) con bordo bianco strappato su carta da pacchi, ritagli o tinta unita, con ombra | R+ (`torn_edge` + maschera soggetto) | Special Elite |
| Svizzero / Bauhaus | immagine riorganizzata in forme rigide (cerchi, bande diagonali, griglie) con blocchi rosso, giallo, blu | R+ (`shape_crop` multipli) | Jost |
| Scanline CRT | manifesto fotografato da monitor a tubo: righe, canali sfalsati, bande luminose | R | VT323 |
| Griglia Warhol | 2×2 o 3×3 della stessa foto in combinazioni pop diverse; è il "Pop 4/9 riquadri" esistente, con i pallini | R | Anton |
| Manifesto turistico anni '30-'50 | campiture pittoriche semplificate (gouache), caldi e solari, ombre blu oltremare, cieli morbidi | R (oil + palette) | Limelight |
| Scuola polacca | alto contrasto, nero, rosso sangue, verde acido, ocra, texture gessosa, contorni nervosi | R | Rubik Dirt |
| Locandina giapponese Showa | offset anni '70 sbiadito, vermiglio, indaco e nero, carta washi fibrosa, margini asimmetrici | R (fibers) | Sawarabi Mincho |
| Retrowave | tramonto a bande neon (magenta, arancio, viola) su cielo stellato o griglia anni '80, riflessi ciano e rosa | R+ (sfondo generato + maschera soggetto) | Monoton |
| Ferrovia art déco anni '40 | manifesto di viaggio dipinto a mano: forme geometriche monumentali, prospettiva dal basso, luce forte, nuvole e vapore, blu acciaio, crema, ocra, antracite, pennellata leggera, texture litografica | R (palette + oil + fibers + nuvole) | Poiret One |
| Retrofuturismo anni '50 | illustrazione editoriale ottimista: geometrie pulite, ombre lunghe, gouache e china, stampa offset, pastello (salvia, azzurro, crema, grigio caldo, rosso tenue), luce piena | Look R; architetture "Googie" = IA-G | Righteous |
| Poster editoriale vintage | resta **fotografico**: colori smorzati oliva/beige/blu sbiadito, luci ingiallite, neri sollevati, grana fine, mezzitono appena percettibile, nitidezza trattenuta, **bordo avorio sottile e imperfetto**, nessun testo | R + bordo | Libre Baskerville |
| Locandina serigrafica minimale | stessa foto, pochi toni piatti che **conservano i colori originali** (prato verde, cielo azzurro), carta opaca, serigrafia finissima, composizione identica | R (`palette_quantize` che preserva le tinte) | Bebas Neue |

**Serie coerente.** Nel documento è richiesto che più foto sembrino della stessa collezione. Nel nostro motore succede da sé: una ricetta con gli stessi valori dà sempre lo stesso trattamento. In più si potrà salvare il proprio "look" e applicarlo a più foto.

### 4.5 I tre livelli dei poster (combinabili con ogni effetto della categoria)
1. **Colore e stampa:** la palette del filtro, per esempio Risograph, Serigrafia, Cianotipia.
2. **Supporto:** carta piegata in 4, manifesto strappato sul muro, plastica lucida con riflessi, cartone riciclato. Nuove texture **generate** dal nostro motore.
3. **Cornice e impaginazione:** margine bianco da galleria, bordo nero con segni di taglio tipografici, fascia inferiore da festival. Le cornici esistenti confluiscono qui.

---

## 5. Arte e stili

Per ogni stile sono indicati i parametri scelti: colori, deformazione delle forme, materia.

### 5.1 Arte antica
| Nome in app | Descrizione | Parametri | Real. | Font |
|---|---|---|---|---|
| Pittura murale egizia | campiture piatte, contorni scuri netti, palette ocra, sabbia, terracotta, rosso, blu egizio, turchese, nero, oro; intonaco calcareo con crepe e scheggiature; cornici geometriche | Palette, Contorno, Invecchiamento, Crepe | R+ (`flat_regions` + texture `plaster` + bordo geometrico); pose frontale/profilo, geroglifici, loti = IA-G | Cinzel |
| Affresco (alla Giotto) | colori a tempera chiari e polverosi, blu oltremare dei cieli, ocra e rosa, contorni morbidi, intonaco con cadute di colore | Polvere, Cadute, Cielo blu | R+ (texture `plaster`) | IM Fell English |
| Bassorilievo | la foto diventa rilievo in pietra o bronzo, luce radente regolabile | Materiale (marmo, pietra, bronzo), Profondità, Angolo luce | R+ (`relief` con normali; meglio con la profondità IA) | Cinzel |
| Scultura classica | soggetto in marmo bianco venato | Venature, Luce | R (texture marble + mono + emboss) | Cinzel |
| Mosaico bizantino | tessere irregolari con fughe, oro di fondo | Dimensione tessere, Fughe, Oro | R+ (`mosaic` a tessere Voronoi) | Cinzel Decorative |
| Miniatura medievale | colori pieni, oro, contorni scuri, pergamena | Oro, Pergamena | R | UnifrakturCook |

### 5.2 Rinascimento e barocco
Barocco caravaggesco (§1.6) si richiama anche qui. Si aggiungono, sotto forma di *colore e materia* e non di copie di opere: Sfumato (morbido, velature), Tenebrismo.

### 5.3 Ottocento e Novecento
| Nome in app | Descrizione | Real. |
|---|---|---|
| Impressionismo | pennellate corte a tocchi di colore puro | R+ (`brush_strokes` orientati) |
| Puntinismo | già presente | R |
| Espressionismo (pittura) | colori arbitrari, pennellata spessa, contorni deformati | R (oil + hue + turbulence lieve) |
| Art Nouveau / Liberty / Art déco / Futurismo / Metafisica / Costruttivismo | vedi §1.6 e §4.1 | R |

### 5.4 Pixel e retro-digitale
| Nome in app | Descrizione | Parametri | Real. | Font |
|---|---|---|---|---|
| Bit anni '80 | poster pixel art: solo nero, rosso vivo, blu elettrico, dithering pesante, mezzitoni, pixel netti senza sfumature, motivi geometrici di fondo, imperfezioni serigrafiche, estetica CRT; varianti 1 bit, Amiga, MS-DOS, Spectrum, Atari | Pixel, Palette (pallini), Dithering (ordinato, diffusione, spirale), Motivo | R (`pixelate` + `dither` + palette fissa) | Press Start 2P |

### 5.5 Tecniche di disegno
Tutte quelle di "Disegno" (Matita … Diffusione) tranne quelle passate a Fumetti.

### 5.6 Mosaici, vetrate e pattern d'arte
Da Pop Art: Mosaico, Punti colorati, Rombi, Cristalli, Vetrata, Griglia di colore.
Più **Memphis / pattern "disruptive"** anni '80-'90, ispirato alle maglie da portiere dell'epoca:
- geometrie spezzate e zig-zag;
- palette acida (viola/magenta, verde ottanio, nero, giallo acido);
- V centrale a chevron con pannelli verticali, simmetria "a scudo";
- generatore di pattern nostro (R+ `memphis`), usabile come sfondo o come riempimento di forme e testi.

---

## 6. Specchi e illusioni

Tutti con il **centro trascinabile sulla tela** e i parametri elencati.

### 6.1 Simmetrie e riflessioni
| Effetto | Parametri | Real. |
|---|---|---|
| Specchio singolo (orizzontale/verticale/obliquo) | posizione del fulcro, angolo dell'asse 0-360°, lato da tenere, sfumatura della giunzione | R+ (estende `mirror`) |
| Specchio quadruplo | centro, angolo fra i due assi (anche non 90°), offset per quadrante, scala | R+ |
| Scacchiera di specchi | righe, colonne, rotazione della griglia, sfalsamento a mattoncino, zoom nella cella | R+ |
| Tunnel infinito (specchi paralleli) | distanza fra i piani, decadimento della luce, angolo prospettico, numero di riflessi | R+ |

### 6.2 Caleidoscopi, rosoni, tassellazioni
| Effetto | Parametri | Real. |
|---|---|---|
| Caleidoscopio radiale | spicchi 3-64, rotazione del campione, rotazione esterna, centro, zoom | R (esiste, si estende) |
| Caleidoscopio a specchi curvi | curvatura dei lati, ripetizioni, torsione, sfalsamento cromatico | R+ |
| Tassellazione triangolare/esagonale | modulo, rotazione, offset, prospettiva del piano | R+ |
| Mandala a livelli | anelli, simmetria per anello, rotazione alternata, ombra fra anelli | R+ |

### 6.3 Geometrie non euclidee e frattali
| Effetto | Parametri | Real. |
|---|---|---|
| Droste (ricorsione infinita) | scala fra livelli, rotazione per livello, forma della finestra, centro di fuga | R+ |
| Spirale di Droste (alla Escher) | passo, bracci, rotazione, torsione centrale | R+ (trasformazione logaritmica complessa) |
| Disco di Poincaré | p, q, raggio, traslazione iperbolica, curvatura | R+ |
| Inversione circolare (Möbius) | raggio, centro, poli, rotazione complessa | R+ |
| Deformazione di Julia/Mandelbrot | costante Cx, Cy, iterazioni, zoom, raggio di fuga | R+ |

### 6.4 Proiezioni sferiche e 3D
| Effetto | Parametri | Real. |
|---|---|---|
| Piccolo pianeta / Tana del bianconiglio | avvolgimento, orizzonte, rotazione polare, inversione | R (esiste, si estende) |
| Bolla di cristallo | raggio, indice di rifrazione 1,0-2,5, riflesso, ombra di contatto | R+ |
| Tunnel 3D cilindrico e conico | profondità, curva del percorso, rotazione delle pareti, nebbia | R (esiste "Tunnel", si estende) |
| Fisheye / grandangolo estremo | curvatura ±, raggio, aberrazione ai bordi | R (esiste, si estende) |
| Cilindro anamorfico | raggio del cilindro, angolo di vista, altezza, anteprima dello specchio | R+ |

### 6.5 Onde, vortici, fluidi
| Effetto | Parametri | Real. |
|---|---|---|
| Increspature (gocce) | frequenza, ampiezza, smorzamento, fase, rifrazione sulle creste, più sorgenti | R (esiste "Acqua", si estende) |
| Vortice | angolo, raggio, curva di decadimento, più vortici | R (esiste) |
| Onde interferenti (moiré) | distanza fra sorgenti, frequenze, sfasamento, fusione | R+ |
| Turbolenza | scala, ottave, forza X/Y, evoluzione | R (esiste `turbulence`) |
| Telo di seta (pieghe) | direzione della gravità, pieghe, ombre, lucentezza | R+ |

### 6.6 Frammenti e geometrie
| Effetto | Parametri | Real. |
|---|---|---|
| Vetro in frantumi | frammenti (Voronoi), punto d'impatto, spostamento, rotazione, bordi riflettenti | R+ (esiste "Vetro rotto" semplice) |
| Forme concentriche sfalsate | anelli, forma (cerchio, quadrato, esagono, stella), rotazione progressiva, ombra 3D | R+ |
| Persiane / lamelle | numero, inclinazione 0-180°, spaziatura, ombre | R+ |
| Tagli a scatti | frequenza, spostamento, separazione RGB, rumore | R (`glitch`) |
| Pixel sort | soglie, direzione 0-360°, lunghezza della scia, curvatura | R+ |

### 6.7 Illusioni percettive e op-art
| Effetto | Parametri | Real. |
|---|---|---|
| Serpenti rotanti (movimento periferico) | contrasto, spessore bordi, densità, saturazione | R+ (pattern generato fuso con la foto) |
| Griglia di Hermann | spessore linee, quadrati, colori, arrotondamento | R+ |
| Muro del caffè | contrasto tessere, malta, sfalsamento | R+ |
| Rilievo alla Vasarely | raggio, altezza, suddivisioni, luce | R+ |
| Anaglifo 3D / autostereogramma | parallasse (usa la profondità IA), frequenza della trama | R+ (profondità: Depth Anything) |
| Linee modulate (mezzatinta) | frequenza, spessore min/max, angolo, ondulazione | R (`halftone lines`, si estende) |

### 6.8 Generativi e ottici
| Effetto | Parametri | Real. |
|---|---|---|
| Low-poly (Delaunay) | densità, sensibilità ai contorni, wireframe, sfumatura | R+ |
| Prisma / dispersione spettrale | angolo, bande spettrali, caustiche, sfocatura | R+ |
| Aura elettrica (Kirlian) | soglia, lunghezza scariche, colore, bagliore | R+ |
| Dithering artistico | matrice, colori, algoritmo (ordinato, diffusione, spirale), contrasto | R (`dither`, si estende) |

### 6.9 Ottica: teleobiettivo e sole/luna giganti
Dal "Master prompt" del documento. **Non è un filtro ma una composizione:** soggetto dalla foto A e Sole o Luna dalla foto B.
Va nello **Studio luce** (roadmap §3.3):
- soggetto scontornato e collocato secondo la regola dei terzi;
- disco solare o lunare grande dietro;
- compressione da 600 mm (sfondo avvicinato e sfocato);
- controluce con luce di contorno del colore della sorgente;
- adattamento di esposizione, bilanciamento, foschia e grana.

La regola del documento resta il criterio: la credibilità fotografica viene prima di tutto.

---

## 7. Glitch e ottica

Tutti quelli attuali. L'**aberrazione cromatica** diventa una famiglia con varianti:
- **Spostamento RGB:** esiste già come "Separazione RGB";
- **Anaglifo 3D;**
- **Lente difettosa:** aberrazione solo ai bordi più morbidezza.

Inoltre la si usa come **ingrediente** dentro Multiverso pop, Cyberpunk, Euforia e Rave/CRT. Come cursore continuo da 0 a 100 entra in **Regola → Ottica**, accanto a Vignettatura, Grana e Nitidezza.

---

## 8. Social e piattaforme (illusioni legate ai layout)

| Nome in app | Come funziona | Real. |
|---|---|---|
| Sorpresa all'apertura | esporta 1080×1350 (4:5); il soggetto "innocente" sta nella fascia centrale 1080×608 visibile nell'anteprima del feed, la sorpresa nei margini sopra e sotto; guide sulla tela | Strumento in Esporta: guide + controllo di dove cade il soggetto |
| Camaleonte (chiaro/scuro) | PNG trasparente con un disegno in nero puro (visibile su sfondo chiaro) e uno in bianco puro (visibile in modalità notte), senza semitrasparenze | Esporta → "Camaleonte": due livelli scelti dall'utente, soglia a 1 bit, niente anti-aliasing |
| Moiré da scorrimento | pattern ad altissima frequenza (2-3 pixel per ciclo) che "vibra" scorrendo | R+ (pattern generato) |
| Immagine nascosta in miniatura | da vicino una texture, in miniatura un volto, un logo o una parola: le basse frequenze di un'immagine dentro le alte frequenze di un'altra (immagine ibrida) | R+ (`hybrid`: due immagini, nessuna IA) |
| Testo corrotto (Zalgo) | testo con segni diacritici impilati che "escono" dalla riga | Strumento Testo: stile "Corrotto" |
| Taglio verticale | foto alta divisa in 2, 3 o 4 strisce numerate che il visualizzatore ricompone una sotto l'altra | Esiste in Dividi: si aggiunge il preset "X / verticale" |
| Finto pulsante dell'interfaccia | **non lo includerei**: imitare l'interfaccia di un'altra piattaforma (font, colori, pulsanti) per far cliccare per errore può violarne le regole e i marchi. Alternativa: pulsanti generici di Zesty ("Tocca qui ▶") disegnati da noi | — |

---

## 9. Animazione generativa (da "effetto digitale stile Matrix")

L'animazione a particelle descritta nel documento:
- anelli concentrici disallineati resi solo a puntini;
- due colori;
- rotazione eccentrica che li fa "pulsare";
- su nero puro.

È **generativa e fattibile senza IA**: un generatore nostro che esporta video o GIF in loop. La variante "occhio" usa due forme (iride e pupilla) e, nella pupilla, la foto scelta riflessa e animata, al posto della scena descritta nel documento. È un modulo a sé (**Animazioni**), dopo gli effetti statici.

---

## 10. Nuove operazioni del motore (riepilogo per lo sviluppo)

| Gruppo | Operazioni |
|---|---|
| Colore e luce | `infrared`, `rim_light`, `long_shadow`, `halation` |
| Fumetto e disegno | `screentone`, `flat_regions`, `hatching`, `speed_lines`, `brush_strokes`, `woodcut` |
| Stampa e carta | `misregister`, `paper_folds`, `torn_edge`, `brush_edge`, `zigzag_edge`, `plaster` (texture) |
| Specchi e geometria | `droste`, `log_spiral`, `poincare`, `mobius`, `julia_warp`, `glass_sphere`, `anamorphic`, `shatter`, `concentric`, `blinds`, `pixel_sort`, `delaunay`, `prism`, `kirlian`, `tessellate`, `mandala`, `cloth` |
| Illusioni e pattern | `op_art` (serpenti, Hermann, muro del caffè, Vasarely), `memphis`, `moire`, `hybrid` |
| Sistema | `mosaic` (Voronoi), `relief` (con profondità), `echo`, `skew_warp`, `sky_gradient` (con maschera del cielo), parametri "bande" e "bordo" comuni |

Ogni operazione nuova si scrive in Kotlin (APK) e si collauda con test sul computer, come le attuali. La webapp la riceverà con il codice condiviso (roadmap §2).

---

## 11. La parte generativa (IA-G): onestà e strada

Alcune voci chiedono di **aggiungere o reinventare oggetti**:
- geroglifici e pose egizie, rampicanti, ingranaggi, droni, gargoyle;
- architetture brutaliste o "Googie";
- forme biomeccaniche alla Beksiński;
- vegetazione solarpunk.

Nessun filtro lo può fare: serve un modello generativo da immagine a immagine, guidato dalla foto. Oggi questi modelli:
- pesano **2-7 GB**;
- richiedono una GPU da computer;
- hanno spesso **licenze restrittive**.

Quindi:
1. **Subito:** per ogni voce c'è il **Look**, che rende colore, luce, grana e tratto senza inventare oggetti. È fedele allo spirito "conserva composizione e posa" dei prompt.
2. **In seguito:** la **Reinterpretazione IA** come pacchetto facoltativo per la webapp su computer (WebGPU). I prompt di Marco sono conservati in `docs/prompt/`, pronti; si sceglierà il modello aperto migliore con licenza compatibile al momento di farlo.

---

## 12. Font suggeriti senza appesantire

**Dove:**
- solo negli effetti di Poster, Fumetti e Cinema, in fondo ai parametri;
- un piccolo chip **"Aa · Nome font"**, mostrato in quel font.

**Al tocco:**
- si apre lo strumento Testo con quel font già scelto;
- se manca, compare la richiesta di scaricarlo, come per i pacchetti IA: tutti OFL, pochi KB ciascuno, una volta sola.

**Nient'altro:** nessun testo aggiunto alla foto, nessuna finestra in più. Chi non lo tocca non se ne accorge.

---

## 13. Ordine di lavoro proposto

1. **Riordino delle categorie esistenti** (§0), senza perdere nessun effetto, e le nuove famiglie che si fanno con le operazioni attuali (molte voci R di Cinema, Analogico, Poster).
2. **Parametri comuni:** bande cinema, bordo, livelli Supporto e Cornice, pallini di palette, chip del font.
3. **Nuove operazioni a gruppi:** prima Fumetto e stampa (§10), poi Specchi e illusioni, poi Arte.
4. **Social e piattaforme** (strumenti in Esporta e Testo).
5. **Animazioni generative.**
6. **Reinterpretazione IA** (quando sarà il momento giusto per i modelli generativi).
