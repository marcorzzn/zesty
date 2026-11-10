# Zesty — principi minimi di interfaccia

Valgono per l'app Android e per la web app. Ogni nuova schermata o pannello deve rispettarli;
quando un pannello esistente viene toccato, lo si porta a questi principi.

## 1. Tutto si vede in diretta
- Ogni modifica (cursore, colore, pennello, forma, gomma) si vede **subito sulla foto**, mentre la si fa.
- Nessun pulsante "Applica" per vedere un risultato: la conferma (✓) serve solo a chiudere lo strumento.
- Le operazioni pesanti non bloccano l'anteprima: si calcola sempre l'ultimo stato, senza buttare via il lavoro già fatto.

## 2. Ogni zona ha un compito
| Zona | Contiene | Non contiene |
|---|---|---|
| **Barra superiore** | Chiudi (✕), Annulla, Ripeti, al massimo **2 azioni contestuali** a icona, menu ⋮ per il resto, Conferma (✓) | parametri, testi lunghi |
| **Pannello inferiore** | parametri dello strumento: una scelta principale, poi le regolazioni | azioni globali (chiudi, conferma, annulla) |
| **Tela** | foto, maniglie, contorni | pulsanti |

## 3. Righe e scorrimento
- Scorrono in orizzontale **solo le collezioni** (categorie, miniature dei filtri, forme, colori).
- Le **azioni non scorrono mai**: se non entrano nella larghezza, vanno nel menu ⋮.
- Una scelta fra 2–4 opzioni è un **selettore segmentato** a larghezza piena, non una fila di chip.
- Il pannello inferiore occupa al massimo il **40% dell'altezza**; se i parametri sono di più, scorre in verticale.

## 4. Misure fisse
- Margine laterale **16 dp**, spazio fra elementi **8 dp**, fra gruppi **12 dp**.
- Altezza dei controlli **36 dp**; area di tocco minima **44 dp**.
- Icone **20 dp** nella barra, **18 dp** nei controlli.
- Testo: titolo 13 sp grassetto, etichette e suggerimenti 11 sp (minimo assoluto), suggerimenti su una riga.

## 5. Colore e stato — palette "Grafite calda"
Stile: il calore e le forme morbide di *Bottega*, i comandi raccolti su un'isola come in *Isola*,
il contorno neutro della foto di *Banco ottico*. Le superfici hanno un calore appena percettibile;
il contorno della foto è grigio medio quasi neutro, perché non deve falsarne i colori (ISO 3664/12646).

| Superficie | Colore |
|---|---|
| Contorno della foto e fondo dello schermo | `#2E2C29` (zone trasparenti: scacchiera `#34322F`, filo attorno alla foto `#45423D`) |
| Isola, barre e pannelli | `#1F1D1A` |
| Superficie interna (selettori, chip) | `#2A2722` |
| Superficie in rilievo | `#35322D` |
| Bordi dei controlli e cursore vuoto | `#77716A` (3,5:1, minimo per i controlli 3:1) |
| Separatori decorativi | `#36332E` |

Tre agrumi, ognuno con un solo ruolo:

| Ruolo | Colore | Contrasto misurato |
|---|---|---|
| **Arancio**: azione principale (✓) e voce selezionata | `#FF8A1F`, contenuto `#1E1407` sopra | 7,7:1 |
| **Limone**: stato attivo, famiglia aperta, valori, cursore | `#F7D774` | 12:1 sull'isola |
| **Lime**: solo maniglie e punti modificabili sulla foto | `#9EE05A` con anello scuro | anello 18:1 su foto chiare |

Testo neutro: primario `#F1ECE4` (14,3:1), secondario `#BDB6AA` (8,4:1), terziario `#A39C90` (6,2:1; 4,7:1 sul rilievo).
Sopra arancio, limone e lime il contenuto è sempre scuro (il bianco si fermerebbe a 2,4:1).
Nessun testo sotto 11 sp. Lo stato si mostra col colore o con l'icona, **mai con testo** tipo "ON/OFF".

## 5a. L'interfaccia non copre mai la foto
- Barra superiore e isola stanno **fuori** dalla foto. La foto occupa lo spazio che resta in mezzo, intera e con le sue proporzioni.
- Quando l'isola si allarga (strumento aperto), la tela si restringe con un'animazione e la foto viene riadattata: non viene mai coperta.
- Sulla foto ci sono solo maniglie, contorni e zone. Zoom e avvisi stanno al centro della barra superiore.
- Solo l'utente può far uscire la foto dall'area visibile, ingrandendo; un tocco sull'indicatore di zoom la riporta intera.

## 5b. Solo icone
- Pulsanti e strumenti sono **icone senza scritte sotto**.
- Il nome compare con la **pressione lunga** (o il passaggio del mouse sul web) ed è sempre nella descrizione accessibile.
- Le scritte restano solo dove sono contenuto: nomi dei filtri sotto le miniature, etichette dei cursori.

## 5c. Disposizione B + C
- **Telefono verticale (< 600 dp)**: in alto ✕ · annulla · ripeti · zoom · azioni · ✓; in basso l'**isola** (angoli 18 dp, staccata 8 dp dai bordi) con la barra fissa delle 7 famiglie e, sopra, gli strumenti della famiglia oppure il pannello dello strumento aperto.
- **Tablet, orizzontale e web (≥ 600 dp)**: barra superiore uguale; a sinistra la colonna delle famiglie e accanto i loro strumenti; a destra il pannello dei parametri (340 dp). Entrambi sono isole staccate dai bordi, con la foto in mezzo.
- Le famiglie sono definite una volta sola (`TOOL_FAMILIES` in `Navigation.kt`) e la web app usa lo stesso elenco.

## 6. Parole
- Etichette di 1–2 parole, in italiano, senza maiuscole forzate né simboli decorativi.
- Ogni icona ha una descrizione accessibile (contentDescription) e un'etichetta breve dove c'è spazio.
- Un suggerimento appare solo quando serve a capire un gesto, in una riga.

## 7. Coerenza
- Stessi componenti ovunque: `PxSegmented`, `PxChip`, `PxSlider`, `PxIconAction`, `PxOverflowMenu`
  (`app/src/main/java/com/example/ui/DesignSystem.kt`; equivalenti CSS nella web app).
- Lo stesso gesto fa la stessa cosa in ogni strumento: trascinare il centro sposta, gli angoli ridimensionano,
  il pallino viola ruota, i punti viola si trascinano.
- Aggiungere/togliere è un'unica modalità (＋/−) valida per pennello, forme, lazo e poligono.
