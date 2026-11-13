# Zesty — roadmap generale

Documento di lavoro (12/11/2026, registro aggiornato il 13/11/2026). Raccoglie tutto ciò che è stato deciso finora: prestazioni, intelligenza artificiale sul telefono, nuove funzioni, webapp.
Ogni fase si chiude solo con tre verifiche: test automatici, tempi misurati sul telefono di riferimento (Redmi Note 13 Pro 5G) e un commit separato.
I principi di interfaccia sono in [UI_PRINCIPLES.md](UI_PRINCIPLES.md); l'analisi dei prototipi è in [PIANO_INTEGRAZIONE.md](PIANO_INTEGRAZIONE.md).

---

## 0. Principi fissi

- **Tutto sul dispositivo.** Niente API, abbonamenti o server di terzi: i modelli IA girano sul telefono (ONNX Runtime) o nel browser (ONNX Runtime Web).
- **Codice nostro.** Il materiale di terzi è solo d'ispirazione (cartella `materiale terzi/`, esclusa da git). I modelli IA di terzi sono ammessi solo con licenza verificata, citata in `app/src/main/assets/licenses`.
- **Mai più lento.** Ogni funzione nuova si carica solo quando serve e non rallenta il resto: cursori fluidi, tocco reattivo, avvio rapido.
- **Interfaccia.** La tela non viene mai coperta dai pannelli; icone chiare e una riga di spiegazione; colori agrumi solo come colori.
- **Onestà sui limiti.** Quando un risultato è una stima (colori di una foto in bianco e nero, parti nascoste di un oggetto), l'app lo dice.

---

## 1. Registro: fatto finora

Aggiornato al 13/11/2026. L'ultima versione (`c9fefda`) è installata sul telefono di riferimento.

### 1.1 Prestazioni (fase 0 e fase 1)
| Commit | Cosa |
|---|---|
| `5d73b8e` | Scontorna (bacchetta CIELAB, sfondo uniforme, rifinitura), Soggetto automatico (GrabCut nostro + IS-Net + MODNet), poster, divisione, prospettiva |
| `82ca091`, `ddca0dd` | Misure di riferimento sul telefono; IA di Scontorna da 1,6 GB a 77 MB di memoria, analisi anticipata all'apertura |
| `514c56a` | Gesti fluidi (tela a 1080 px, effetto a 720 px), versione ottimizzata con R8 |
| `589f502` | Maniglie fluide con un effetto attivo, selezione al tocco, tocco a vuoto, nuovo pannello livelli |
| `1cf1383` | Tela caricata sulla GPU in sottofondo, profilo di avvio (avvio 0,42–0,58 s) |
| `25fee00`, `f671d67` | Tela fuori dal thread dell'interfaccia (più correzione della chiusura all'avvio) |

Fluidità misurata nelle prove a mano: fotogrammi a scatti dal 7,0% al 3,6%; caricamenti lenti sulla GPU dall'1,8% allo 0,2%.

### 1.2 Funzioni e IA
| Commit | Cosa |
|---|---|
| `5b461cf` | Nuovo motore dei pennelli, tipi e parametri di colore |
| `68a6f8d` | Pacchetti IA scaricati al primo uso (registro unico `assets/ai/registry.json`, impronta SHA-256, download ripreso) e Colorizza con DDColor-tiny |
| `4aaad17` | Colorizza: colori fedeli e nuovi controlli |
| `31be960`, `ddb2091` | Cancella oggetti con LaMa (famiglia Ritocca); il modello si prepara all'apertura dello strumento |
| `de2d5ca` | Nome Zesty ovunque, nuova icona, licenza "tutti i diritti riservati", README; materiale di terzi solo in locale |
| `c9fefda` | Esporta → Dividi e salva: colonne, righe e pannelli fino a 20 (fino a 400 file); i numeri sui pannelli stretti si rimpiccioliscono per non coprire la foto |

### 1.3 Effetti (dettaglio in [EFFETTI.md](EFFETTI.md))
Oggi il catalogo ha **410 effetti in 16 categorie**, tutti con motore nostro.
| Commit | Cosa |
|---|---|
| `b02ec47`, `f824029` | Nuovo ordinamento del catalogo e 87 effetti nuovi |
| `c424e4b` | Navigazione degli effetti più chiara, miniature uniformi, foto trascinabile fra le bande |
| `72109e0` | 5 tecniche di disegno e stampa (retino, campiture, tratteggio, incisione/xilografia, fuori registro); icone più chiare |
| `e019f29` | Pennellate vere (impressionismo, vorticose, olio, pastello, espressionismo), linee cinetiche, carta strappata, infrarosso a colori; via i cursori doppi o inutili; pennellate dipinte in parallelo su tutti i core |
| `348f623` | Ricorsione e spirale infinita, bolla di cristallo, vetro in frantumi, low-poly, persiane, pixel sort |
| `c9fefda` | Mandala, 3 tassellazioni, frattali di Julia, disco di Poincaré, inversione circolare, op-art (righe, onde, cerchi, Vasarely), prisma, scie d'arcobaleno, aura elettrica e di fuoco, Notturna 800 (alone da pellicola) |

### 1.4 Ancora da verificare sul telefono
- Tempi delle pennellate e degli effetti ottici: le misure sono pronte (`ZestyDeviceBench.paintEffects` e `opticEffects`), ma non ancora eseguite. Sul computer ogni effetto nuovo sta sotto il secondo a 640×480; le pennellate restano le più pesanti.
- Resa degli effetti nuovi su foto vere: finora sono stati controllati solo sulla scena di prova.

---

## 1bis. Cosa manca ancora (in ordine di priorità)

1. **Effetti** (priorità attuale: risultati belli e caratteristici, chiarezza e ordine di funzioni e icone).
   - Tecniche ancora da scrivere (EFFETTI.md §10): `rim_light`, `long_shadow`, `paper_folds`, `brush_edge`, `zigzag_edge`, `plaster`, `anamorphic`, `concentric`, `cloth`, op-art (serpenti rotanti, griglia di Hermann, muro del caffè), `memphis`, `moire`, `hybrid`.
   - Parametri comuni: bordo, i tre livelli dei Poster, scelta del carattere.
   - Eventuale pacchetto di trasferimento di stile con reti neurali (licenza da verificare).
   - Altro lavoro su chiarezza e ordine di categorie, gruppi e icone.
2. **Misure sul telefono** di tutti gli effetti nuovi, con ottimizzazione di quelli lenti.
3. **Scontorna e Cancella:** MobileSAM (tocchi un oggetto e viene selezionato), MI-GAN per l'anteprima veloce di Cancella, BiRefNet-lite per i bordi migliori, le tre scelte grandi all'apertura (§3.1).
4. **Oggetti nella foto:** Sposta soggetto, Allarga, Adatta formato (§3.2, §3.4, §3.7).
5. **Colorizza:** colore dai campioni (Zhang) e oggetto di riferimento (§3.5); poi il resto di Restauro (Ripara, Toni, Rumore, Nitidezza, Ingrandisci).
6. **Studio luce** (§3.3) e Profondità/Volto/Miglioramento automatico (§3.6).
7. **Webapp alla pari dell'APK** (§2.2): oggi i nuovi effetti, Colorizza, Cancella e Dividi esistono solo nell'APK.
8. **Grafica vettoriale e loghi** (§3.7).

---

## 2. Base comune (prima delle nuove funzioni)

1. **Pacchetti IA.**
   - Registro dei modelli unico per APK e web: file, impronta SHA-256, licenza, preparazione dell'immagine.
   - Nell'APK resta solo il nucleo; gli altri modelli si scaricano una volta al primo uso, con download ripreso se si interrompe e impronta verificata.
   - Un modello pesante alla volta, liberato dopo l'uso.
   - ✅ Fatto (`68a6f8d`): i file si scaricano dalle fonti ufficiali (Hugging Face) e si accettano solo con l'impronta giusta.
2. **Webapp = stessa app.** Oggi la webapp (`public/`) è un programma separato e più vecchio. Proposta:
   - motori in **Kotlin Multiplatform**, che girano anche nel browser come WebAssembly;
   - interfaccia con **Compose Multiplatform per il web**, da verificarne la maturità prima di partire;
   - IA con ONNX Runtime Web (WebGPU, oppure WASM se manca).
3. **Pubblicazione della webapp:** Cloudflare Pages (gratis, va bene anche con repository privato, permette le intestazioni COOP/COEP necessarie al calcolo parallelo nel browser).
4. **Analizza una volta, usa tante volte.** Profondità, soggetto, "impronta" per il tocco sugli oggetti, punti del volto: si calcolano una volta per foto e li riusano tutte le funzioni.

---

## 3. Funzioni, in ordine

### 3.1 Scontorna più chiaro e completo
- **All'apertura tre scelte grandi:** *Togli lo sfondo* (un tocco), *Prendi un oggetto* (tocchi ciò che vuoi), *Rifinisci a mano*.
- **Spiegazioni:** una riga sotto ogni strumento quando è selezionato; la pressione lunga mostra un mini prima/dopo.
- **Pagina automatica per prima;** la parte tolta si vede subito su uno sfondo di prova colorato.
- **Modelli:**
  - **MobileSAM** (Apache 2.0): tocchi un oggetto qualsiasi e viene selezionato;
  - **BiRefNet-lite** (MIT): massima qualità del bordo.

### 3.2 Oggetti dentro la foto: sposta, ridimensiona, prospettiva, luce, angolazione
- **Sposta soggetto.** Un tocco sull'oggetto:
  1. lo si isola (Scontorna);
  2. diventa un livello con le maniglie;
  3. il buco dietro si riempie da solo (LaMa).
- **Ridimensiona e ruota sul piano:** maniglie esistenti.
- **Prospettiva dell'oggetto:** deformazione a 4 angoli su qualsiasi livello (la geometria esiste già nello strumento Prospettiva).
- **Angolazione**, cioè far "girare" un oggetto:
  - *piccole rotazioni* (circa ±15°): con la profondità stimata (Depth Anything V2 Small) l'oggetto si deforma in 2,5D, in modo credibile;
  - *rotazioni grandi:* servono modelli che ricostruiscono l'oggetto in 3D (es. TripoSR, licenza MIT, circa 1,6 GB). Le parti mai viste vengono **inventate**. Troppo pesante per il telefono: eventuale pacchetto facoltativo per la webapp su computer.
- **Ombra e luce che seguono l'oggetto spostato:** si ricalcolano con lo Studio luce (3.3).

### 3.3 Studio luce (composizione e illuminazione)
Esempio guida: persona scontornata, stanza buia, sfondo con fumo o nebbia, luci e ombre da più angoli.

- **Sfondi:**
  - neutri (tinta unita, sfumatura, vignetta "da studio");
  - foto;
  - stanza buia;
  - sfondo allargato o sfocato dalla foto originale.
- **Atmosfera** (generata dal nostro motore, si muove e si modifica):
  - nebbia a strati;
  - fumo (rumore frattale animabile, densità, direzione, colore);
  - raggi di luce volumetrici;
  - pulviscolo e grana.

  Si può mettere **davanti o dietro** il soggetto, usando la profondità.
- **Luci virtuali:** fino a 4 luci (puntiforme, faretto, luce di contorno, ambiente), ognuna con direzione, colore, intensità e morbidezza.
  - Rilievo stimato dall'IA: profondità (Depth Anything V2 Small), da cui si ricavano le normali, cioè l'orientamento di ogni punto della superficie.
  - Il soggetto riceve luce diffusa e riflessi nelle zone rivolte verso la lampada; la luce di contorno ne disegna il bordo.
  - Le luci si trascinano direttamente sulla tela.
- **Ombre:**
  - *proiettata a terra:* la sagoma del soggetto viene deformata in prospettiva secondo la luce, poi sfumata con la distanza;
  - *di contatto:* scura e stretta sotto i piedi o la base;
  - *portata sullo sfondo* (muro dietro).
- **Armonizzazione:** colori e luminosità del soggetto si adattano allo sfondo; la luce di una lampada colorata tinge soggetto e nebbia.
- **Dove:** nuova famiglia **"Studio"**, oppure strumento in "Scontorna → Componi" (da decidere).
- **Limiti:** la luce sul soggetto è un'approssimazione da una sola foto (niente modello 3D); funziona bene per luci morbide e laterali, meno per controluce netti.

### 3.4 Cancella oggetti e Allarga
- **Cancella oggetti:** tocchi l'oggetto (MobileSAM) o lo cerchi con pennello o lazo; **MI-GAN** (MIT, circa 30 MB) in anteprima, **LaMa** (Apache 2.0) per il risultato finale.
- **Allarga:** nella famiglia Ritaglia trascini i bordi verso l'esterno; LaMa riempie in modo credibile fino a circa il 25–30% per lato su cieli, muri e texture.
  - Allargamenti grandi con scene inventate richiederebbero un modello generativo da 1–2 GB: non sul telefono.

### 3.5 Restauro e Colorizza
Nuova famiglia **"Restauro"**:
- Colorizza
- Ripara (graffi e macchie: diffusione per i piccoli, LaMa per i grandi)
- Toni (livelli automatici)
- Rumore
- Nitidezza
- Ingrandisci (Real-ESRGAN general-x4v3, BSD-3)

**Colorizza, i modelli scelti:**
| Ruolo | Modello | Licenza | Peso | Note |
|---|---|---|---|---|
| Colore automatico | **DDColor-tiny** (ICCV 2023) | Apache 2.0 | circa 130 MB (fp16, ingresso 512×512) | Il più realistico tra i modelli aperti adatti al telefono; versione grande per la webapp su computer |
| Colore dai tuoi campioni | **Zhang et al. 2017** (SIGGRAPH, "guidata dall'utente") | BSD-2 | da verificare | Accetta direttamente punti colorati come istruzione e li diffonde in modo coerente |
| Riserva leggera | DeOldify | MIT | circa 61 MB quantizzato | Solo se DDColor non gira |
| Oggetto di riferimento | motore nostro + MobileSAM | — | — | Vedi sotto |

**Come funziona:**
1. **Automatico:** DDColor calcola il colore a 512 px; si tiene solo il colore (canali a/b in LAB) e lo si allarga sulla foto a piena risoluzione seguendone i contorni (filtro guidato). Luci, ombre e dettagli restano quelli originali.
2. **Campioni:** tocchi un punto e scegli il colore dalla tavolozza, con il contagocce ovunque o da una foto di riferimento. I punti guidano il modello di Zhang e si diffondono seguendo i contorni veri.
3. **Oggetto di riferimento:** carichi una foto a colori dell'oggetto, lo tocchi lì e nella foto in bianco e nero (MobileSAM lo isola in entrambe). Zesty trasferisce i colori **per livello di luminosità**: le parti chiare prendono i colori delle parti chiare, le ombre quelli delle ombre.
4. **Ritocchi:** intensità, saturazione, caldo/freddo, "epoca" (resa pellicola tramite LUT), confronto prima/dopo.

Il risultato è un livello di colore modificabile: i campioni restano in un elenco e si possono cambiare o togliere.

### 3.6 Profondità, volto, miglioramento automatico
- **Profondità:** Depth Anything V2 **Small** (Apache 2.0; *Base e Large sono solo per uso non commerciale*). Effetti collegati:
  - bokeh realistico;
  - tilt-shift;
  - nebbia secondo la distanza;
  - nuova luce;
  - foto 3D in parallasse.
- **Volto:** MediaPipe Face Landmarker (Apache 2.0, da convertire in ONNX): ritocco della pelle, occhi, denti, piccole correzioni di forma; guida più precisa per Scontorna.
- **Miglioramento automatico:** LUT 3D adattive (Apache 2.0, meno di 1 MB).

### 3.7 Grafica vettoriale, loghi, formati
1. **Nucleo vettoriale (Kotlin Multiplatform):**
   - penna e modifica dei nodi;
   - forme parametriche e booleane (ispirate a Clipper2, licenza Boost);
   - testo trasformato in forme;
   - esportazione SVG e PDF.
2. **Studio Logo:**
   - da immagine a vettore (motore nostro: niente potrace, che è GPL);
   - monogrammi, griglie, palette;
   - controlli automatici (leggibile a 16 px, bianco/nero, contrasto, linee sottili);
   - pacchetto marchio.
3. **Scomponi:** una grafica diventa livelli modificabili (testi con PaddleOCR mobile, colori, forme, sfondo ricostruito).
4. **Adatta formato:** un progetto diventa post, storia, banner, presentazione o documento in un tocco.
   - Ruoli dei livelli riconosciuti in automatico (sfondo, soggetto, testo, logo, decorazioni) e regole per ciascuno:
     - lo sfondo si allarga o si sfoca;
     - il soggetto resta nell'area sicura;
     - i testi si ridimensionano;
     - il logo va in un angolo.
   - Una versione semplice per le foto arriva prima del modulo grafico completo.
5. **Lato Canva e Figma:** modelli, kit del marchio, componenti con varianti, disposizione automatica.

---

## 4. Trappole di licenza da evitare

| Da evitare | Motivo |
|---|---|
| YOLO di Ultralytics | AGPL |
| potrace | GPL |
| RMBG di BRIA | non commerciale |
| Depth Anything Base e Large | non commerciale |
| CtrlColor e simili | spesso con licenze non commerciali |
| Stable Diffusion | restrizioni d'uso e troppo pesante |

L'app oggi non è in vendita, ma si sceglie comunque il modello con licenza libera: così non si rifà nulla se un giorno lo diventerà.

---

## 5. Fonti dei modelli (da riverificare prima di ogni download)

- DDColor: <https://github.com/piddnad/DDColor> · versione per telefono: <https://huggingface.co/edgetools/ddcolor>
- Colorizzatori a confronto (DDColor, DeOldify, Zhang): <https://github.com/Makistos/colorize>
- IS-Net: <https://huggingface.co/xrds/isnet-general-onnx-int8> · MODNet: <https://huggingface.co/Xenova/modnet>
