# DocxAnonymizer
Stand-alone Java tool to anonymize OOXML Documents (DOCX)


Possibili sviluppi progetto Scala: Docx Anonymizer.

 - Cruciale: architettura distribuita per il processamento dei documenti -

1) Dati N server, M nomi del dizionario: si da' a ogni server la responsabilita di processare solo M/N nomi del dizionario. [HARD]
   - Variante correlata: se N >> 1, si puo' fare load balancing nella distribuzioni di documenti/ porzioni di un singolo documento [HARD]
2) Implementare i dizionari con database (non file) in uno scenario di processamento distribuito dei dati: 
   - tenere conto del numero di occorrenze dei nomi nei documenti per ottimizzazione: saranno processati prima i nodi piu' frequenti [MEDIUM]
   - ampliamento dinamico del dizionario con nuovi nomi mediante uno schema a "candidatura" [MEDIUM]
3) Ampliare il parco di dati personali da minimizzare (date e luoghi di nascita, codici fiscali, indirizzi, email, numeri di telefono, sesso, dati documenti di identita', etc.). Nota: non tutte le date e non tutte le citta vanno minimizzate [EASY]

 - Da decidere: refactor in scala di quali parti del codice? -

N SERVER:
- 1 documento => suddiviso in N parti;

== Ipotesi 1 ==
- Ogni server ha i suoi nomi; processa e minimizza una parte di documento; scambia la propria parte con un altro server che non ha ancora processato quella porzione di documento

== Ipotesi 2 ==
- Tutti i nominativi vengono individuati in un primo momento dai server, successivamente in parallelo gli N server minimizzano tenendo conto di tutti nominativi individuati
