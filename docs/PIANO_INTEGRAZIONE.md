# Zesty — analisi dei prototipi HTML e piano di integrazione

Documento di lavoro (10/11/2026). Riassume cosa c'è di valido nei sei prototipi, dove va nell'app,
come riorganizzare l'interfaccia e come aggiungere nuovi effetti in modo modulare.
I principi di interfaccia sono in [UI_PRINCIPLES.md](UI_PRINCIPLES.md).

---

## 1. Analisi dei prototipi

| File | Cosa contiene di valido | Dove va in Zesty | Priorità |
|---|---|---|---|
| **Transparent Studio PRO** | Rimozione sfondo con 4 motori (IA, sfondo uniforme dagli angoli, chroma key nero/bianco/verde/blu, riempimento dal punto toccato); rifinitura bordi (sfumatura, smussatura, erosione/dilatazione, decontaminazione del colore, margine); "fora interni"; ritaglio automatico al soggetto; bacchetta magica con tolleranza e contiguità; selezione inverti/espandi/contrai/sfuma; gomma per colore; anteprima su scacchiera/nero/bianco/colore; confronto prima/dopo con cursore; istogramma dell'alpha; export PNG 32 bit, WebP, PNG su colore, scala 1×–4×; più foto in coda | Nuovo strumento **Scontorna** che produce la **maschera del livello** (non distruttiva). I nostri strumenti Zona (pennello, forme, lazo, poligono) diventano la rifinitura manuale; si aggiungono **Bacchetta** e **Riempi da punto**. Export trasparente nel pannello **Esporta** | **Alta** |
| **Poster Vintage** | Palette ridotta con k-means, semplificazione, sfumature tonali, grana, carta invecchiata, margine; mostra la palette ottenuta | **Fatto ora**: effetto *Poster vintage* in Pop Art (operazioni `palette_quantize` e `frame`). Da aggiungere: mostrare la palette sotto i cursori | Fatto |
| **Splitta immagine** | Strisce verticali uguali per un carosello, anteprima combinata, download per pannello | Funzione di **Esporta → Dividi in pannelli** (non è un effetto: produce più immagini). Opzioni da aggiungere: righe × colonne (griglia 3×3), formato del singolo pannello (1:1, 4:5), sovrapposizione/margine, guide sulla tela, numerazione, ZIP | Media |
| **Pizzico Foto Lab** | Sviluppo globale (esposizione in EV, raddrizza ±8°, luci/ombre, temperatura/tinta, seppia, microcontrasto, pellicola, vignetta, grana); **più zone con regolazioni proprie**; confronto tenendo premuto; preset salvabili; progetto salvabile (regolazioni + maschere); "abbina i toni a un'altra foto"; **lavoro su anteprima ridotta ed export alla risoluzione originale** | Le zone multiple diventano **livelli di regolazione con maschera** (stessi strumenti Zona). Confronto tenendo premuto nella barra superiore. *I miei look*: un effetto salvato dall'utente è una ricetta JSON dello stesso catalogo. "Abbina toni" come operazione del motore. **Anteprima ridotta + export pieno** come architettura generale | **Alta** (anteprima/export), media il resto |
| **Studio — Design Canvas** (photoshoplite) e **Studio Pro** (photomix) | Trasformazioni con coordinate locali del livello (8 maniglie + rotazione), allineamento della selezione, operazioni booleane, ritaglio con proporzioni, editor curve, selettore colore a ruota HSV, finestra di export unica (PNG/SVG/PDF), salva/apri progetto, capovolgi, ruota di 90° | Le trasformazioni sono **fatte ora** (`TransformGeometry`, stesso modello di coordinate locali). Da portare: allineamento, selettore HSV a ruota (sostituisce i campioni fissi), finestra di export unica, salva/apri progetto. *Ho analizzato questi due file dall'elenco delle funzioni e dal codice delle trasformazioni, non riga per riga.* | Media |

### Cosa non conviene portare così com'è
- Le librerie scaricate a runtime da CDN (Tailwind, modello IA di imgly, MediaPipe): l'app deve funzionare offline. Su Android si usa un modello sul dispositivo (ML Kit *Subject Segmentation* o MediaPipe *Image Segmenter*); nella web app MediaPipe Tasks con il modello incluso nei file del progetto. Prima di integrarle vanno verificate le licenze.
- I pulsanti duplicati (export in 3 punti, "Applica raffinazione" quando l'anteprima è già in diretta).

---

## 2. Correzioni fatte in questa fase

1. **Avvisi**: niente più finestra con "Capito!". Ora c'è una riga discreta che sparisce da sola dopo 1,8 s. Tolti gli avvisi inutili (annulla, ripeti, zoom).
2. **Tela e foto**:
   - all'avvio si sceglie **Apri una foto** oppure una **tela vuota** con le sue dimensioni;
   - una foto aperta su un progetto vuoto **diventa la tela**: stesse proporzioni, nessun livello trasparente di dimensioni diverse sotto;
   - le foto aggiunte dopo diventano livelli.
   - Per restare fluida, la tela di lavoro ha al massimo 2048 px sul lato lungo (vedi §6, punto 1).
3. **Maniglie** (`TransformGeometry`), per le stesse cause che rendevano difficile agganciarle:
   - la tela leggeva la posizione vecchia del livello dopo il primo ridimensionamento;
   - l'area di tocco cresceva e si restringeva con lo zoom;
   - la rotazione era ignorata;
   - la manopola veniva disegnata in un punto e sentita in un altro;
   - il ✕ copriva la maniglia in alto a sinistra e non funzionava.

   Ora: geometria calcolata dall'inizio del gesto con l'angolo opposto fermo; area di tocco fissa di 24 dp; maniglie di dimensione costante a ogni zoom; maniglie dei lati nascoste sui livelli molto piccoli; rotazione agganciata ai multipli di 45°; ✕ funzionante e spostato fuori dall'angolo.

---

## 3. Riorganizzazione delle funzioni (niente doppioni)

Oggi la barra in basso (21 voci) e la griglia (16) ripetono gli stessi strumenti, a volte con nomi diversi:
"Bordo" e "Cornice", "Ritaglio Lib." e "Libero", "Masch. Forma" e "Ritaglia Forma", "Fluidifica" e "Clona" due volte.
Inoltre esistono **due sistemi di effetti** (gli "FX" classici e il catalogo).

Proposta: **7 famiglie**, ognuna con un solo punto d'accesso:

| Famiglia | Contiene |
|---|---|
| **Regola** | Regola, Curve, Bilanciamento, Raddrizza, Abbina toni |
| **Effetti** | Catalogo (268 effetti + *I miei look*) — gli FX classici confluiscono qui come effetti del catalogo |
| **Ritaglia** | Ritaglia/ruota/prospettiva, Ridimensiona tela, Estendi (fit), Cornice, Rifletti, **Dividi** |
| **Scontorna** | Rimuovi sfondo (auto), Bacchetta, Riempi da punto, Ritaglio libero e a forma, Rifinisci bordi |
| **Ritocca** | Clona, Fluidifica, Pennelli/Disegna, Dispersione |
| **Aggiungi** | Foto, Testo, Forme, Adesivi, Fumetti, Penna & booleane, Collage |
| **Esporta** | PNG/JPG/WebP/SVG/PDF, trasparente, scala, Dividi in pannelli, Condividi |

Livelli e Zona non sono famiglie: sono **trasversali**. I Livelli hanno un pannello sempre raggiungibile; la Zona è un'icona disponibile in ogni strumento che lavora sui pixel.

---

## 4. Interfaccia: basi di ergonomia e alternative

### Evidenze usate
- **Zona del pollice**: su smartphone circa metà degli utenti usa una mano sola e raggiunge comodamente la parte bassa e centrale dello schermo (Hoober, 2013). Le azioni frequenti vanno in basso, quelle rare o distruttive in alto.
- **Legge di Fitts** (Fitts, 1954): bersagli più grandi e più vicini si colpiscono più in fretta. Area di tocco ≥ 48 dp (Material 3) / 44 pt (Apple HIG); studi sul pollice indicano circa 9–10 mm (Parhi, Karlson, Bederson, 2006).
- **Legge di Hick** (Hick, 1952): il tempo di scelta cresce con il numero di opzioni. Meglio poche famiglie e poi il dettaglio, che 21 voci piatte.
- **Tempi di risposta** (Nielsen, 1993): sotto 0,1 s un'azione sembra istantanea, entro 1 s il flusso non si interrompe. Per questo serve l'anteprima in diretta, con anteprima ridotta durante il trascinamento sugli effetti pesanti.
- **Valutare i colori**: le norme per l'osservazione delle immagini (ISO 3664, ISO 12646) chiedono un contorno **neutro**, senza dominanti. Grandi superfici colorate vicino alla foto ne falsano la percezione (contrasto simultaneo). Quindi interfaccia grigia neutra, colore d'accento solo su piccoli elementi.
- **Tema scuro**: grigio molto scuro invece del nero puro (Material consiglia circa #121212). Riduce l'effetto alone del testo chiaro e lascia spazio alle ombre per separare i piani.
- **Contrasto** (WCAG 2.2): testo ≥ 4,5:1, elementi grafici e testo grande ≥ 3:1.

### Problemi misurati nei colori attuali
| Elemento | Attuale | Contrasto | Soglia |
|---|---|---|---|
| Testo secondario `#5C5C66` su pannello | 2,73:1 | ❌ | 4,5:1 |
| Testo bianco su rosa `#E0289A` (pulsanti) | 4,25:1 | ❌ | 4,5:1 |
| Fondo nero puro `#000000` | — | sconsigliato | — |

### Palette proposta "Grafite neutra" (stesso marchio, solo più leggibile)
| Ruolo | Colore | Contrasto misurato |
|---|---|---|
| Fondo | `#141417` | — |
| Superficie | `#1C1C21` | — |
| Superficie in rilievo | `#26262C` | — |
| Bordo | `#34343B` | — |
| Testo | `#EDEDF0` | 15,7:1 |
| Testo secondario | `#B4B4BE` | 8,3:1 |
| Testo terziario | `#8E8E99` | 5,2:1 |
| Accento (pulsanti pieni con testo) | `#C2187F` | 5,6:1 con testo bianco |
| Accento (icone, selezione) | `#FF4FB0` | 6,1:1 sul fondo |
| Maniglie e punti modificabili | `#8B5CF6` con bordo bianco | 4,3:1 sia su scuro sia su chiaro |

Variante "**Grigio fotografico**" (fondo `#2A2A2E`, testo 12,2:1): più neutra per chi lavora sul colore, meno "notte".

### Tre impostazioni possibili (stesse funzioni, cambia la disposizione)
**A — Barra inferiore a famiglie (consigliata su telefono).**
- In basso: 7 icone di famiglia e sopra il pannello dei parametri.
- **✕ e ✓ in basso**, nella zona del pollice.
- In alto: annulla, ripeti, confronta (tenendo premuto), Zona, ⋮.
- È lo schema degli editor fotografici mobili più diffusi: è familiare e lascia la foto più grande.

**B — Quella attuale, riordinata.**
- ✕/✓ restano in alto e la griglia diventa 7 famiglie.
- Costa meno, ma su telefoni grandi la conferma resta lontana dal pollice.

**C — Adattiva (consigliata insieme ad A).**
- **Telefono verticale**: A.
- **Telefono orizzontale, tablet e web**: barra verticale delle famiglie a sinistra, pannello parametri a destra, barra superiore con annulla, ripeti, confronta ed esporta. È lo schema dei tuoi prototipi Pizzico e Transparent Studio.
- Le soglie sono le classi di finestra di Material 3 (< 600 dp, 600–840 dp, > 840 dp) e le stesse valgono nel CSS della web app.
- Ordine delle sezioni, nomi, icone e componenti identici: cambia solo la posizione.

---

## 5. Come aggiungere un effetto nuovo (struttura modulare)

Ci sono tre livelli; si sceglie il più semplice che basta.

### Livello 1 — Ricetta (nessun codice)
Se l'effetto si ottiene combinando operazioni che esistono già, si aggiunge una voce in
`public/assets/fx/catalog/<categoria>.json`. Cursori, colori e scelte compaiono da soli,
sia in Android sia nella web app. Formato: [public/assets/fx/README.md](../public/assets/fx/README.md).

### Livello 2 — Nuova operazione + ricetta (come il Poster)
Se serve un algoritmo nuovo:
1. **Kotlin**: una funzione pura `FxImage -> FxImage` in un file `fx/FxOps<Tema>.kt`, registrata nella sua mappa `ops`. La mappa va aggiunta in `FxEngine` una sola volta per file. Esempio: [FxOpsPalette.kt](../app/src/main/java/com/example/fx/FxOpsPalette.kt).
2. **JavaScript**: la stessa operazione con lo stesso nome e gli stessi argomenti in `fx-engine.js` (web app).
3. **Ricetta JSON** che la usa ed espone i parametri.
4. Il test `FxCatalogTest` la prova da solo con valori minimi, predefiniti e massimi.

Regole dell'operazione:
- **Pura e deterministica**: stesso input, stesso output. La casualità passa da `seed`/`FxRand`, che dà gli stessi numeri in Kotlin e JS.
- **Distanze in unità `u`** (1% del lato corto): l'effetto è uguale su miniatura, anteprima ed export.
- **Non cambia le dimensioni** dell'immagine. Margini e cornici si disegnano dentro; il resize vero è uno strumento.
- **Argomenti tramite `FxArgs`**: accettano numeri, `$parametri`, espressioni e scelte.
- **Costo**: se è pesante (k-means, olio, sfocature grandi), l'anteprima durante il trascinamento usa la versione ridotta (§6, punto 1).

### Livello 3 — Strumento interattivo
Se l'effetto ha bisogno di tocchi sulla foto (scontorno con bacchetta, dividi in pannelli, testo su tracciato), non è un effetto ma uno **strumento**. Serve:
- un pannello con i componenti `Px*`;
- un'azione nella barra superiore;
- un risultato **non distruttivo**: una maschera, un livello o un'impostazione di export, mai pixel cancellati.

---

## 6. Prossime fasi consigliate

1. **Anteprima ridotta + export pieno**: si lavora su una copia ridotta (circa 2048 px, ancora meno durante i trascinamenti) e l'export ricalcola tutto sull'originale. È il passaggio che rende tutto fluido e toglie il limite di 2048 px della tela.
2. **Interfaccia A + C** con la palette Grafite neutra e le 7 famiglie: elimina i doppioni e unifica i due sistemi di effetti.
3. **Scontorna**: sfondo uniforme e chroma key subito (nessuna dipendenza), poi modello IA sul dispositivo; bacchetta e riempi da punto come strumenti Zona; rifinitura bordi in diretta.
4. **Motore web** (`fx-engine.js`) con lo stesso catalogo, poi la web app con la stessa architettura delle informazioni.
5. **Esporta**: finestra unica con formati, trasparenza, scala e Dividi in pannelli.
6. **Forme, figure, fumetti, pattern e font a licenza libera** (fasi già pianificate).

---

## 7. Decisioni dell'11/2026 e piano tecnico dei blocchi grandi

Decisioni:
- disposizione **B + C** (fatta);
- palette **Grafite agrumi** (fatta);
- **solo icone**, con il nome a pressione lunga (fatto);
- Dividi dentro **Ritaglia**;
- scontorno di livello professionale;
- **sistema per loghi** vettoriale.

### 7.1 Dividi (famiglia Ritaglia)
Uno strumento di Ritaglia con le guide disegnate sulla tela. Non tocca i pixel: è un'impostazione che si usa all'export.
- **Modalità**:
  - *Colonne × righe* uguali, da 1 a 12 per lato;
  - *Rapporto libero del pannello*: si sceglie la proporzione del singolo pannello (1:1, 4:5, 9:16, 3:2 o due numeri qualsiasi) e quanti ne servono; l'area di taglio si trascina e si ridimensiona sulla foto con le maniglie standard;
  - *Guide libere*: linee orizzontali e verticali da trascinare dove vuoi, con aggancio a terzi, metà, bordi e alle altre guide.
- **Opzioni**:
  - spazio fra i pannelli: rimosso (taglio netto) o aggiunto (riquadri staccati, con colore);
  - sovrapposizione in px per i caroselli che "scorrono";
  - numerazione;
  - ordine di export (righe o colonne);
  - formato e scala.
- **Preset**: carosello 2–10, griglia profilo 3×3 / 3×2 / 3×4, puzzle, poster su più fogli A4.
- **Uscita**:
  - tutti i pannelli in un ZIP o nella galleria;
  - ogni pannello singolarmente;
  - anteprima combinata.

  L'export avviene alla risoluzione piena (§6, punto 1).

### 7.2 Scontorna (precisione oltre la "bacchetta magica")
Ogni strumento produce la **maschera del livello** (non distruttiva) e si combina con ＋/− come la Zona.

| Strumento | Tecnica | Perché è più preciso |
|---|---|---|
| **Soggetto automatico** | Segmentazione sul dispositivo: MediaPipe *Image Segmenter* con il modello incluso nell'app, offline; in alternativa ML Kit *Subject Segmentation*. Web: MediaPipe Tasks (WASM) con il modello nei file del progetto | Un solo tocco; dà un'alpha morbida, non un bordo a scalini |
| **Lazo magnetico** | *Intelligent Scissors / Live-wire* (Mortensen e Barrett, 1995): il tracciato fra un punto fermo e il dito segue il bordo a costo minimo (gradiente, attraversamenti dello zero del Laplaciano, direzione del bordo), punti fermi automatici, parametri larghezza/contrasto/frequenza | Si aggancia al bordo vero anche con un tocco impreciso; si annulla punto per punto |
| **Selezione rapida** | Pennello che fa crescere la zona per somiglianza di colore e bordi, poi **GrabCut** (Rother, Kolmogorov, Blake, 2004: modelli di colore e taglio di grafo) per rifinire primo piano e sfondo | Capisce "oggetto contro sfondo" invece di "colore simile", che è il limite della bacchetta |
| **Bacchetta** | Tolleranza in **CIELAB** (ΔE percettivo, non distanza RGB), contigua o globale, campione 1/3×3/5×5, anti-alias | Le soglie seguono l'occhio, non i numeri RGB |
| **Rifinisci bordo** (capelli, pelo, foglie) | In una fascia attorno al bordo si stima l'alpha col **filtro guidato** usando la foto come guida (He et al.; il motore lo ha già); **decontaminazione** del colore: estende i colori interni sul bordo per togliere l'alone dello sfondo | Mantiene i dettagli sottili che una maschera binaria taglia |
| **Pulizia** | Espandi/contrai, sfuma, smussa, riempi i buchi, togli gli isolotti sotto una certa area, ritaglia al soggetto | Toglie le imperfezioni senza ripassare a mano |

Precisione al tocco:
- **lente d'ingrandimento** sopra il dito (2–4×, mirino), perché il dito copre il punto;
- cursore spostato rispetto al dito;
- zoom fino al livello del singolo pixel.

Visualizzazione:
- maschera su scacchiera, nero, bianco, colore, rosso semitrasparente o solo maschera;
- confronto prima/dopo.

Uscita: PNG/WebP trasparente, "ritaglia al soggetto", oppure nuovo livello col soggetto isolato.

### 7.3 Loghi: modulo vettoriale stile Illustrator
Serve un **modello vettoriale vero**: oggi i tracciati sono solo spezzate.
- **Dati** (formato JSON condiviso con la web app, come il catalogo effetti):
  - tracciati fatti di punti di ancoraggio, ognuno con le sue maniglie Bézier e un tipo (spigolo, morbido, simmetrico);
  - aperto o chiuso;
  - riempimento (pieno o sfumato), contorno (spessore, estremità, giunti, tratteggio);
  - gruppi, piano di lavoro.
- **Penna** con i gesti di Illustrator adattati al tocco:
  - tocco = punto spigolo; trascinamento = punto morbido con maniglie simmetriche;
  - pressione lunga su un punto = converti spigolo/morbido; tocco sul primo punto = chiudi;
  - tocco su un lato = aggiungi punto; pressione lunga su un punto = elimina;
  - "selezione diretta" per spostare punti e maniglie, con la lente di 7.2.
- **Forme parametriche**: rettangolo arrotondato, ellisse, poligono, stella, spirale, linea. Si convertono in tracciato quando serve modificarli a mano.
- **Operazioni booleane**: unione, sottrazione, intersezione, esclusione. Android ha `Path.op`; per avere lo stesso risultato sul web si usa una libreria con licenza MIT da includere nei file (da scegliere).
- **Precisione**:
  - aggancio a griglia, guide, punti, centri e angoli di 15°;
  - allinea e distribuisci;
  - valori numerici di posizione, dimensioni e rotazione.
- **Testo**: testo su tracciato e "converti in tracciati", per modificare le lettere come forme.
- **Export**: SVG e PDF vettoriali, PNG a più dimensioni (favicon 16–512, social), anteprima del logo su fondo chiaro e scuro.
- **Modelli di partenza**: monogramma, badge, emblema, logotipo con simbolo. Sono file dello stesso formato JSON e si aggiungono senza codice, come le ricette degli effetti.

### 7.4 Ordine consigliato
1. Anteprima ridotta + export a risoluzione piena (rende fluido tutto il resto).
2. Scontorna: bacchetta in CIELAB, rifinisci bordo, pulizia e lente subito; poi lazo magnetico, GrabCut e soggetto automatico.
3. Dividi.
4. Modulo vettoriale e loghi.
5. Motore e interfaccia web sullo stesso catalogo, sulle stesse famiglie e sugli stessi formati.
