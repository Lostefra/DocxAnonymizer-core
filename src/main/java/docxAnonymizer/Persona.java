package docxAnonymizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author lorenzo
 * 
 * Campi della classe: 
 *  - cognome:            stringa rappresentante il cognome del nominativo da minimizzare
 *  - nomi:               lista di  stringhe rappresentanti i nomi del nominativo da minimizzare
 *  - id:                 stringa sostituita alle occorrenze del nominativo nel testo
 *  - regex:              regular expression che consente l'individuazione del nominativo nel testo
 *  - comboIndex:         struttura dati ausiliaria impiegata nel calcolo della regex del nominativo
 *  - automatic:	 	  variabile booleana: true => impiego di dizionari; false => input specificato dall'utente
 *  - hasOmonimi:         variabile booleana impiegata nella gestione delle omonimi tra nominativi
 *  
 *  Sono inoltre presenti una serie di variabili String final di ausilio per la costruzione delle regular expression
 *  
 */
public class Persona {
	private String cognome, id, regex;
	private List<String> nomi, comboIndexDuplicati, comboIndex;
	private boolean automatic, hasOmonimi;
	
	// regex formulate secondo le linee guida discusse nella tesi
	private final static String SEP_base = "\\s";
	private final static String SEP_regex = SEP_base + "+";
	private final static String EXTRA = "ĿŀČčŘřŠšŽžĲĳŒœŸŐőŰűḂḃĊċḊḋḞḟĠġṀṁṠṡṪṫĀāĒēĪīŌōŪūİıĞğŞşẀẁẂẃŴŵŶŷǾǿẞ";
	private final static String BASE = "[A-Z][a-zA-ZÀ-ÖØ-öø-ÿ" + EXTRA + "]";
	private final static String NOME = BASE + "+";
	private final static String PREP = "((?i)d((a|e)(l(l[aeo]?)?|i|gli?)?|i)?|(ne|a|su)(l(l[aeo]?)?|i|gli)?|l[aeo]?|co[iln]?|i[ln]?|gli|per)";
	private final static String COGNOME = "((" + BASE + "*|" + PREP + ")['‘’′´`" + SEP_base + "]+)?" + NOME;
	private final static String ANTE = "(?<=[^A-Za-zÀ-ÖØ-öø-ÿ" + EXTRA + "]|^)(";
	private final static String POST = ")(?=[^A-Za-zÀ-ÖØ-öø-ÿ" + EXTRA + "]|$)";
	private final static String PREPROCESS = ANTE + "(" + NOME + SEP_regex + ")?" + NOME + SEP_regex + COGNOME + 
			"|" + COGNOME + SEP_regex + NOME + "(" + SEP_regex + NOME + ")?" + POST; 
	private final static String BASE_user = "A-Za-zÀ-ÖØ-öø-ÿ" + EXTRA + "'‘’′´`";
	public final static String NOMINATIVO_USER = "^(!)?([" + BASE_user + "]+:){0,9}[" + BASE_user + "]+;[" + BASE_user + "\\s]+$";
	
	/**
	 * Costruttore usato quando i dati sono specificati dall'utente
	 * 
	 * @param cognome
	 * @param nomi
	 * @param id
	 */
	public Persona(String cognome, List<String> nomi, int id) {
		super();
		// imposto regex robusta rispetto agli apostrofi
		cognome = cognome.replaceAll("['‘’′´`]", "['‘’′´`] ?");
		// Es: A((?i)morosa)
		if(Character.isUpperCase(cognome.charAt(0)))
			this.cognome = cognome.charAt(0) + "((?i)" + cognome.substring(1, cognome.length()) + ')';
		// Es: (?i)de Rosa
		else
			this.cognome = "(?i)" + cognome;
		this.nomi = new ArrayList<>();
		for(String nome : nomi) {
			this.nomi.add(nome.charAt(0) + "((?i)" + nome.substring(1, nome.length()) + ')');
		}
		if(id > 0)
			this.id = "ID" + String.valueOf(id);
		else
			this.id = "KeepUnchanged";
		this.comboIndexDuplicati = new ArrayList<>();
		this.comboIndex = new ArrayList<>();
		this.automatic = false;
		this.hasOmonimi = false;
		calcolaRegex();
	}

	/**
	 * Costruttore usato quando i dati sono dedotti impiegando un dizionario
	 * 
	 * @param nome entry del dizionario
	 */
	public Persona(String nome, int id) {
		super();
		this.cognome = COGNOME;
		this.nomi = new ArrayList<>();
		this.nomi.add(nome.charAt(0) + "((?i)" + nome.substring(1, nome.length()) + ')');
		// secondo nome opzionale
		this.nomi.add("(" + SEP_base + "*" + NOME + SEP_base + "*" + ")?");
		// aggiungo una lettera per distinguere le persone omonime 
		this.id = "ID@" + String.valueOf(id);
		this.comboIndexDuplicati = new ArrayList<>();
		this.comboIndex = new ArrayList<>();
		this.automatic = true;
		// imposto la regex strict: il nome del dizionario deve necessariamente comparire
		this.hasOmonimi = true;
		calcolaRegex();	
	}

	@Override
	public String toString() {
		return "Persona [cognome=" + cognome + ", nomi=" + nomi + ", id=" + id + "]";
	}
	
	/**
	 * Funzione che decreta se una stringa di testo deve essere sottoposta alla minimizzazione dei dati o meno
	 * @param textValue stringa di testo contenuta in nodi Docx
	 * @return true: la stringa contiene "verosimilmente" dei nominativi; false: la stringa non contiene nominativi
	 */
	public static boolean preprocess(String textValue) {
		return Pattern.compile(PREPROCESS).matcher(textValue).find();
	}
	
	private boolean hasCharsDuplicated(String s) {
		for(int i = 0; i < s.length(); i++) {
			for(int j = i; j < s.length(); j++) {
				if(i != j && s.charAt(i) == s.charAt(j))
					return true;
			}
		}
		return false;
	}

	private void recurse(String inp, String  s)
    {
        if (s.length() == inp.length())
            return;
        for (int i = 0; i < inp.length(); i++) {
        	comboIndexDuplicati.add(s + inp.charAt(i));
            recurse(inp, s + inp.charAt(i));         
        }
        return;
    }
	
	/*
	 * if not hasOmonimi:
	 *     INPUT [0,1] ; OUPUT [0, 1, 10, 01]
	 *     INPUT [0,1,2] ; OUPUT [0, 1, 2, 10, 01, 21, 20, 12, 02, 012, 021, 120, 102, 210, 201]
	 * else:
	 *     INPUT [0,1] ; OUPUT [10, 01]
	 *     INPUT [0,1,2] ; OUPUT [012, 021, 120, 102, 210, 201]   
	 */
	private void calcolaComboIndici() {	
		StringBuilder s = new StringBuilder();
		this.comboIndexDuplicati = new ArrayList<>();
		this.comboIndex = new ArrayList<>();
		
		for(int i = 0; i < nomi.size(); i++) {
			s.append(i);
		}
		for (int i = 0; i < s.toString().length();i++) {
			comboIndexDuplicati.add(String.valueOf(s.toString().charAt(i)));
			recurse(s.toString(), "" + s.toString().charAt(i));
		}
		//Elimino le stringhe che contengono caratteri uguali (ES. 001, 221, ect.)
		for(String ind : comboIndexDuplicati) {
			if(!hasOmonimi) {
				if(!hasCharsDuplicated(ind))
					comboIndex.add(ind);
			}
			// gestione omonimie: riconosco nominativo solo se presenta tutti i suoi nomi
			else {
				if(!hasCharsDuplicated(ind) && ind.length() == nomi.size())
					comboIndex.add(ind);
			}
		}		
		//Ordino le stringhe per lunghezza: antepongo quelle con piu' nomi, altrimenti sbaglio la minimizzazione
		Collections.sort(comboIndex, new LengthComparator());
	}
	
	/** 
	 * Determino la regex che identifica la persona nel documento, qualora nomi e cognome siano forniti dall'utente:
	 * - case insensitive
	 * - il cognome compare sempre, o all'inizio o in fondo
	 * - almeno un nome deve comparire
	 * - i nomi possono comparire opzionalmente in qualunque ordine
	 * - i singoli termini di un nominativo sono divisi tra loro da sequenze di caratteri non visibili ("\\s+")
	 * - ogni nominativo e' necessariamente preceduto e seguito da un carattere non alfabetico e non accentato o risulta posizionato ad inizio/fine stringa 
	 * (per maggiori informazioni, consultare la tesi)
	 * 
	 * Esempio: Lorenzo,Mario;Amorosa
	 * - Lorenzo Mario Amorosa
	 * - Mario Lorenzo Amorosa
	 * - Amorosa Lorenzo Mario
	 * - Amorosa Mario Lorenzo
	 * - Lorenzo Amorosa
	 * - Mario Amorosa
	 * - Amorosa Lorenzo
	 * - Amorosa Mario
	 * 
	 * Osservazione: si fattorizzano le regex ponendo il cognome anteposto o postposto a tutte le combinazioni di nomi messe in "OR";
	 * 
	 * Esempio di regex dinamicamente ottenuta per il nominativo "Lorenzo:Mario;Amorosa":
	 * 
	   (?<=[^A-Za-zÀ-ÖØ-öø-ÿĿŀČčŘřŠšŽžĲĳŒœŸŐőŰűḂḃĊċḊḋḞḟĠġṀṁṠṡṪṫĀāĒēĪīŌōŪūİıĞğŞşẀẁẂẃŴŵŶŷǾǿẞ]|^)
	   ((L((?i)orenzo)\s+M((?i)ario)|M((?i)ario)\s+L((?i)orenzo)|L((?i)orenzo)|M((?i)ario))
	   \s+A((?i)morosa)
	   |
	   A((?i)morosa)\s+
	   (L((?i)orenzo)\s+M((?i)ario)|M((?i)ario)\s+L((?i)orenzo)|L((?i)orenzo)|M((?i)ario)))
	   (?=[^A-Za-zÀ-ÖØ-öø-ÿĿŀČčŘřŠšŽžĲĳŒœŸŐőŰűḂḃĊċḊḋḞḟĠġṀṁṠṡṪṫĀāĒēĪīŌōŪūİıĞğŞşẀẁẂẃŴŵŶŷǾǿẞ]|$)
	 *
	 * ricordando i costrutti: (?i) regex case insensitive, (?<=REGEX) positive look behind, (?=REGEX) positive look ahead	
	 */
	private void calcolaRegex() {
		calcolaComboIndici();															 
		StringBuilder pattern_nomi = new StringBuilder("(");
		
		for(String sequenza : comboIndex) {
			for(int i = 0; i < sequenza.length(); i++) {
				if(i != (sequenza.length() - 1)) {
					if(!automatic)
						pattern_nomi.append(nomi.get(Integer.parseInt(String.valueOf(sequenza.charAt(i)))) + SEP_regex);
					else
						pattern_nomi.append(nomi.get(Integer.parseInt(String.valueOf(sequenza.charAt(i)))));
				}
				else
					pattern_nomi.append(nomi.get(Integer.parseInt(String.valueOf(sequenza.charAt(i)))) + '|');
			}
		}
		
		//remove last pipe & add parenthesis
		pattern_nomi.deleteCharAt(pattern_nomi.length() - 1);
		pattern_nomi.append(")");
		
		//inizializzo la regex della persona 
		regex = ANTE + pattern_nomi.toString() + SEP_regex + this.cognome + "|" + this.cognome + SEP_regex + pattern_nomi.toString() + POST; 

		// stampo solo se non sto impiegando i dizionari
		if(App.debug && !automatic) {
			System.out.println(toString());
			System.out.println("Indici per permutazioni nomi: " + comboIndex);
			System.out.println("Regex: " + regex);
			System.out.println();
		}
		
	}
	
	public String getRegex() {
		return this.regex;
	}
	
	public void setHasOmonimi(boolean value) {
		if(value) {
			this.hasOmonimi = true;
			calcolaRegex();
		}
	}

	public static HashMap<String, String> getIdMapper() {
		return IdMapper.getInstance().getIdMap();
	}
	
	/**
	 * 
	 * Se sono impiegati dei dizionari, questo metodo garantisce che a nominativi distinti siano asseganti ID distinti
	 * 
	 * @param oldID
	 * @return newID
	 */
	private static String updateId(String oldID) {
		if(oldID.length() < 4)
			throw new IllegalArgumentException("L'ID deve essere nella forma 'IDA1', non " + oldID);
		char c = oldID.charAt(2);
		// 52 ID per ogni nome distinto sono sufficienti
		if(c >= 'z')
			return oldID;
		else {
			c = (char) (c + 1);
			return oldID.substring(0, 2) + c + oldID.substring(3);
		}
	}
	
	private static String getAsUniqueFlatString(List<String> terminiNominativo) {
		String nominativo = "";
		// ordino alfabeticamente i termini del nominativo
		Collections.sort(terminiNominativo);
		for(String termine : terminiNominativo)
			nominativo += termine;
		// mantengo solo i caratteri alfabetici	
		return nominativo.replaceAll("[^a-zA-ZÀ-ÖØ-öø-ÿ" + EXTRA + "]", "");
	}
	
	/**
	 * La funzione minimizza i dati presenti in in una stringa di testo contenuta in uno o piu' nodi Docx
	 * Nel caso siano impiegati i dizionari di nomi nella minimizzazione, sono asseganti id distinti per 
	 * coppie di nomi-cognome distinte.
	 * Si tiene conto delle parti di testo da non minimizzare
	 * 
	 * Nota:
	 * Ad es.: "Mario Rossi" e "Mario Bianchi" sono due persone diverse, meritano 2 ID distinti:
	 *   - per "Mario <cognome>" ID: 1234
	 * 	 - per "Mario Rossi"     ID: IDa1234
	 *   - per "Mario Bianchi"   ID: IDb1234
	 * Ho quindi 26 * 2 (52) ID distinti, sfruttando le lettere [A-Za-z];
	 * se arrivo a z (52esima lettera) fisso z per gli ID, duplicandoli
	 *
	 * @param textValue stringa da minimizzare
	 * @param entryPoints riferimenti ai nodi Docx che contengono porzioni della stringa da minimizzare
	 * @return stringa post minimizzazione
	 */
	public String minimizza(String textValue, List<EntryPoint> entryPoints, List<EntryPoint> unchangeable) {
		Matcher matcher;
		boolean continua;
		int charToRemove, charRemoved = 0, currentRemove, charToAdd, gap;
		EntryPoint nodoTrovato;
		String nominativo, idCheck = this.id; 
		
		do {
			continua = false;
			matcher = Pattern.compile(regex).matcher(textValue);
			if(matcher.find() && canChange(matcher, unchangeable)) {
				continua = true;			
				if(automatic) {
					// ottengono una rappresentazione univoca in stringa del nominativo				
					nominativo = getAsUniqueFlatString(Arrays.asList(matcher.group().split(SEP_regex))).toUpperCase();
					// ogni nome del dizionario puo' corrispondere a piu' nominativi, assegno ID univoci a differenti nominativi 
					idCheck = Persona.getIdMapper().get(nominativo);
					// se individuo il nominativo per la prima volta: assegno nuovo id; altrimenti: uso il suo id
					if(idCheck == null) {
						this.id = Persona.updateId(this.id);
						Persona.getIdMapper().put(nominativo, this.id);
						idCheck = this.id;
					}
				}				
				if(App.debug) {
			        System.out.print("       Start index: " + matcher.start());
			        System.out.print(", End index: " + matcher.end()); //lunghezza parola, ossia primo indice libero
			        System.out.println(", Found: " + matcher.group());
				}
				charToRemove = matcher.end() - matcher.start();
				charToAdd = String.valueOf(idCheck).length();
				if(App.debug) {
					if(charToRemove >= 0)
						System.out.print("       Rimossi " + charToRemove + " caratteri");
					else
						System.out.print("       Aggiunti " + charToRemove + " caratteri");
					System.out.println(". Assegnato l'ID: " + idCheck);
				}
				nodoTrovato = null;

				//cerco il nodo contenente il nominativo che sto per minimizzare
				for(EntryPoint e : entryPoints) {			
					//per il nodo contenente il nome da minimizzare reimposto gli indici
					if(matcher.start() >= e.getFrom() && matcher.start() < e.getTo() && nodoTrovato == null) {
						nodoTrovato = e;
						//calcolo i caratteri compresi tra l'inizio del match e la fine del nodo
						gap = e.getTo() - matcher.start();
						//il nodo contiene tutta la parola
						if(charToRemove <= gap) {
							charRemoved = charToRemove;
							charToRemove = 0;
							e.setTo(e.getTo() - charRemoved + charToAdd);
						}
						//la parola e' presente anche nei nodi successivi
						else {
							charRemoved = gap;
							charToRemove -= charRemoved;
							e.setTo(e.getTo() - charRemoved + charToAdd);
						}
					}
					//per tutti i nodi successivi a quello contenente il nome da minimizzare traslo gli indici
					if(nodoTrovato != null && e != nodoTrovato) {
						//traslo indietro gli indici, di un valore pari ai caratteri rimossi
						e.setFrom(e.getFrom() - charRemoved + charToAdd);
						e.setTo(e.getTo() - charRemoved + charToAdd);
						//verifico se rimuovere altri caratteri
						if(charToRemove != 0) {
							//calcolo il numero di caratteri contenuti in un nodo
							gap = e.getTo() - e.getFrom();
							//il nodo contiene tutta la parola
							if(charToRemove <= gap) {
								currentRemove = charToRemove;
								charRemoved += charToRemove;
								charToRemove = 0;
								e.setTo(e.getTo() - currentRemove);
							}
							//la parola e' presente anche nei nodi successivi
							else {
								currentRemove = gap;
								charRemoved += gap;
								charToRemove -= currentRemove;
								e.setTo(e.getTo() - currentRemove);
							}
						}
					}
				}
				if(nodoTrovato == null) {
					System.out.println("ASSURDO: matcher.find() ha trovato un match, ma nessun EntryPoint lo contiene");
				}
				// rimuovo un'occorrenza del nominativo alla volta per una gestione corretta degli entryPoints
				String old = textValue;
				textValue = matcher.replaceFirst(String.valueOf(idCheck));			
				// aggiorno boundaries dei nodi contenente testo da non modificare
				propagateChanges(textValue, old, unchangeable);				
			}
		} while(continua);
		
		return textValue;
	}
	
	/**
	 * 
	 * Funzione che verifica che il nodo da minimizzare non rientri tra quelli da lasciare invariati
	 * 
	 * @param matcher match della regex
	 * @param unchangeable lista di EntryPoint da lasciare invariate
	 * @return true => procedi con sostituzione; false => salta la sostituzione
	 */
	private boolean canChange(Matcher matcher, List<EntryPoint> unchangeable) {
		for(EntryPoint e : unchangeable) {
			if((matcher.start() >= e.getFrom() && matcher.start() < e.getTo()) ||
					(matcher.end() > e.getFrom() && matcher.end() <= e.getTo())) {
				if(App.debug) {
			        System.out.print("[SKIP] Start index: " + matcher.start());
			        System.out.print(", End index: " + matcher.end()); //lunghezza parola, ossia primo indice libero
			        System.out.println(", Found: " + matcher.group());
				}
				return false;
			}
		}
		return true;
	}
	
	/**
	 * Dopo ogni inserimento di ID nel testo, vengono opportunamente propagati i boundaries degli EntryPoints
	 * 
	 * @param tmp la stringa post-sostituzione
	 * @param old la stringa pre-sostituzione
	 * @param unchangeable lista di EntryPoint da aggiornare
	 */
	private void propagateChanges(String tmp, String old, List<EntryPoint> unchangeable) {
		int diff = tmp.length() - old.length();
		int disparityIndex = 0, upperBound = diff > 0 ? old.length() : tmp.length();
		// la stringa e' nuova solo da un certo indice in poi
		for(int i = 0; i < upperBound; i++) {
			if(old.charAt(i) != tmp.charAt(i))
				break;
			disparityIndex += 1;
		}
		// aggiorno i boundaries solo se nella zona di aggiornamento della stringa
		for(EntryPoint e : unchangeable) {
			if(e.getFrom() >= disparityIndex) {
				e.setFrom(e.getFrom() + diff);
				e.setTo(e.getTo() + diff);
			}
		}		
	}
	
	/**
	 * Per ogni persona p1 che ha in comune almeno un termine del proprio nominativo con una persona p2 viene calcolata una nuova regex piu' severa
	 * 
	 * @param persone
	 */
	public static void updateOmonimi(List<Persona> persone) {
		List<String> termini_p1, termini_p2;
		
		if(App.debug) {
			System.out.println();
			System.out.println("=====================================================================");
			System.out.println("Check omonimie\n");
		}
		
		for(int i = 0; i < persone.size() - 1; i++) {
			Persona p1 = persone.get(i);
			termini_p1 = getTermini(p1);
			for(int j = i + 1; j < persone.size(); j++) {
				Persona p2 = persone.get(j);
				termini_p2 = getTermini(p2);
				if(!Collections.disjoint(termini_p1, termini_p2)) {
					if(App.debug) {
						System.out.println("Omonimi: " + p1.toString() + " - " + p2.toString());
						System.out.println("Update delle relative strutture dati:\n");
					}
					p1.setHasOmonimi(true);
					p2.setHasOmonimi(true);
				}
			}
		}
		
		if(App.debug)
			System.out.println("Check omonimie completato");
		
	}

	private static List<String> getTermini(Persona p) {
		List<String> termini = new ArrayList<>();
		termini.add(p.cognome);
		for(String t : p.nomi)
			termini.add(t);
		return termini;
	}
	
}

class IdMapper { 
    // static variable single_instance of type IdMapper 
    private static IdMapper single_instance = null;
    // mappa per gestione delle associzioni nominativi-id; key: nominativo; value: id
 	private static HashMap<String, String> idMap; 

    // private constructor restricted to this class itself 
    private IdMapper() { 
       idMap = new HashMap<>();
    } 
  
    public HashMap<String, String> getIdMap() {
		return idMap;
	}

	// static method to create instance of Singleton class 
    public static IdMapper getInstance() { 
        if (single_instance == null) 
            single_instance = new IdMapper(); 
  
        return single_instance; 
    } 
    
} 

class LengthComparator implements java.util.Comparator<String> {
    public int compare(String s1, String s2) {
        return s2.length() - s1.length();
    }
}
