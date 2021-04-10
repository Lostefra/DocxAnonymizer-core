package docxAnonymizer;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.*;
import javax.xml.bind.JAXBElement;

import org.docx4j.wml.Br;
import org.docx4j.wml.CTBookmark;
import org.docx4j.wml.CTCustomXmlElement;
import org.docx4j.wml.CTCustomXmlRun;
import org.docx4j.wml.CTFtnEdnRef;
import org.docx4j.wml.CTMarkup;
import org.docx4j.wml.CTMarkupRange;
import org.docx4j.wml.CTMoveBookmark;
import org.docx4j.wml.CTMoveFromRangeEnd;
import org.docx4j.wml.CTMoveToRangeEnd;
import org.docx4j.wml.CTObject;
import org.docx4j.wml.CTPerm;
import org.docx4j.wml.CTRel;
import org.docx4j.wml.CTRuby;
import org.docx4j.wml.CTRubyContent;
import org.docx4j.wml.CTSdtContentRun;
import org.docx4j.wml.CTSimpleField;
import org.docx4j.wml.CTSmartTagRun;
import org.docx4j.wml.CTTrackChange;
import org.docx4j.wml.CommentRangeEnd;
import org.docx4j.wml.CommentRangeStart;
import org.docx4j.wml.ContentAccessor;
import org.docx4j.wml.DelText;
import org.docx4j.wml.Drawing;
import org.docx4j.wml.FldChar;
import org.docx4j.wml.P;
import org.docx4j.wml.Pict;
import org.docx4j.wml.ProofErr;
import org.docx4j.wml.R;
import org.docx4j.wml.R.AnnotationRef;
import org.docx4j.wml.R.CommentReference;
import org.docx4j.wml.R.ContinuationSeparator;
import org.docx4j.wml.R.Cr;
import org.docx4j.wml.R.DayLong;
import org.docx4j.wml.R.DayShort;
import org.docx4j.wml.R.EndnoteRef;
import org.docx4j.wml.R.FootnoteRef;
import org.docx4j.wml.R.LastRenderedPageBreak;
import org.docx4j.wml.R.MonthLong;
import org.docx4j.wml.R.MonthShort;
import org.docx4j.wml.R.NoBreakHyphen;
import org.docx4j.wml.R.PgNum;
import org.docx4j.wml.R.Ptab;
import org.docx4j.wml.R.Separator;
import org.docx4j.wml.R.SoftHyphen;
import org.docx4j.wml.R.Sym;
import org.docx4j.wml.R.Tab;
import org.docx4j.wml.R.YearLong;
import org.docx4j.wml.R.YearShort;
import org.docx4j.wml.RangePermissionStart;
import org.docx4j.wml.RunDel;
import org.docx4j.wml.RunIns;
import org.docx4j.wml.RunTrackChange;
import org.docx4j.wml.SdtContent;
import org.docx4j.wml.SdtElement;
import org.docx4j.wml.SdtRun;
import org.docx4j.wml.Tc;
import org.docx4j.wml.Text;
import org.docx4j.wml.Tr;
import org.jvnet.jaxb2_commons.ppp.Child;

/**
 * @author lorenzo
 * 
 * Campi della classe: 
 *  - runNodes:           lista di nodi <w:r> da processare, ottenuti da un file .docx usando la libreria docx4j
 *  - persone:            lista di persone delle quali si vuole minimizzare i dati; se "null" i dati verrano individuati automaticamente
 *  - plainTexts:         struttura dati che contiene le stringhe da minimizzare ed i relativi riferimenti ai nodi Docx
 *  - runNodesElaborated: mappa che tiene traccia dell'hashcode dei nodi <w:r>, univoco per ogni oggetto istanziato nella JVM;
 *                        key: hashcode del nodo run, value: true => node gia' elaborato; false => nodo da elaborare
 *  - SEP_docx:	 	      stringa che identifica il valore testuale dei nodi docx che contengono separatori di testo (es: Br, Tab, Cr)
 *  					  inoltre segnala la fine e l'inizio di una tableCell <w:tc>
 *  - toKeepViaConfig   : espressioni critiche da non minimizzare, specificate dall'utente e caricate da file a default
 */
public class Elaborator {
	private List<R> runNodes;
	private List<Persona> persone, keepUnchanged;
	private PlainTexts plainTexts;
	private HashMap<Integer, Boolean> runNodesElaborated;
	private FileWriter outputFileAssociations;
	private HashMap<String, String> associationsMap;
	private final String SEP_docx = "\t";
	private String toKeepViaConfig;
	private boolean automatic = false;
	private HashSet<String> dictionary;
	public UtilsID utilsID;
	
	public Elaborator(List<Object> runNodes, String outputFileAssociations, List<Persona> persone, List<Persona> keepUnchanged) {
		this.runNodes = new ArrayList<>();
		this.plainTexts = new PlainTexts();
		this.runNodesElaborated = new HashMap<>();
		this.dictionary = new HashSet<>();
		this.utilsID = new UtilsID();
		this.associationsMap = new HashMap<>();
		try {
			this.outputFileAssociations = new FileWriter(outputFileAssociations);
		} catch(IOException e) {
			System.out.println("Eccezione durante la creazione del file di associazioni: " + outputFileAssociations);
			e.printStackTrace();
			System.exit(5);
		}

		R tmpRun;
		for (Object node : runNodes) {
			if (! (node instanceof R))
				throw new IllegalArgumentException("la lista 'runNodes' deve contenere solo nodi di tipo 'R', non " + lastName(node.getClass().getName()));
			tmpRun = (R) node;
			this.runNodes.add(tmpRun);
			this.runNodesElaborated.put(tmpRun.hashCode(), Boolean.FALSE);
		}
		this.persone = persone;
		this.keepUnchanged = keepUnchanged;
		
		if(!persone.isEmpty())
			Persona.updateOmonimi(persone);

		readConfig( "/" + "config" + "/" + "keep_unchanged.txt", true);
		initializePlainTexts(this.runNodes);
		
	}
	
	public Elaborator(List<Object> runNodes, String outputFileAssociations, List<Persona> persone, List<Persona> keepUnchanged,
			String keepUnchangedExprFile) {
		this(runNodes, outputFileAssociations, persone, keepUnchanged);
		readConfig(keepUnchangedExprFile.replace("\\", "/"), false);
	}

	private static String fixedLengthString(String string, int length) {
	    return String.format("%1$" + length + "s", string);
	}
	
	private static String lastName(String className) {
	    return className.split("\\.")[className.split("\\.").length - 1];
	}
	
	/**
	 * Unwrap da JAXB se necessario
	 * 
	 * @param node pre-unwrap
	 * @return node post-unwrap
	 */
	private static Object removeJAXB(Object node) {
		if (node instanceof JAXBElement)
			node = ((JAXBElement<?>) node).getValue();	
		return node;
	}
	
	/**
	 * Lettura di espressioni che possono essere scambiate per nominativi da non minimizzare
	 * 
	 * @param f_name filename contenente espressioni critiche da non minimizzare nel documento
	 */
	private void readConfig(String f_name, boolean fromResources) {
		StringBuilder sb = null;
		String toKeep;
		BufferedReader bf;
		try {
			if(fromResources)
				bf = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream(f_name)));
			else
				bf = new BufferedReader(new FileReader(f_name));
			toKeep = bf.readLine();
			if(toKeep != null)
				sb = new StringBuilder(toKeep.trim() + "|" + toKeep.trim().toUpperCase());
			while(toKeep != null) {
				toKeep = bf.readLine();
				if(toKeep != null)
					sb.append("|" + toKeep.trim() + "|" + toKeep.trim().toUpperCase());
			}
			bf.close();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(6);
		}
		toKeep = sb.toString();
		if(toKeepViaConfig != null)
			toKeepViaConfig = toKeepViaConfig + "|" + toKeep;
		else
			toKeepViaConfig = toKeep;	
	}
	
	/**
	 * Implementazione basata su delle gerarchie definite e ragionevoli, partendo dai nodi <w:r> (Run)
	 * Viene inizializzata la struttura dati plainTexts
	 * 
	 * @param runNodes lista di nodi R da elaborare
	 */
	private void initializePlainTexts(List<R> runNodes) { 
		Object parent, grandParent;
		List<Object> runBrothers;
		R run;
		
		if(App.debug) {
			System.out.println();
			System.out.println("=====================================================================");
	    	System.out.println("Navigazione dei nodi Docx");
		}
		
		int index_print = 0; // --- indice usato solo per stampe
		
		for(int i = 0; i < runNodes.size(); i++) {
			run = runNodes.get(i);
			//analizzo il nodo solo se non è stato già elaborato
			if(runNodesElaborated.get(run.hashCode()) == false) {
				
				if(App.debug) {
					System.out.println("---------------------------------------------------------------------");
					System.out.println("<" + index_print + ">");
					index_print = index_print + 1;
				}
					
				//lista che sara' popolata dai fratelli del nodo run, componenti un unica "famiglia"
				runBrothers = new ArrayList<>();
				
				/**
				 * bisogna inizialmente ricavare il padre, le classi in docx4j forniscono la primitiva "getParent()" per questo proposito
				 * 
				 * Si deve ricavare la classe specifica del nodo padre per poter risalire la gerarchia in caso di tabelle (nodi <w:tc>, tableCell).
				 * 
				 * smartTag, stdContent e customXML sono ritenuti dei nodi wrapper, non nodi padre: bisogna quindi risalire ai loro padri di livello  
				 * superiore per raggiungere il vero nodo padre del nodo run correntemente analizzato
				 */
				
				//recupero nodo padre
				parent = run.getParent();
				
				//rimozione di tutti i padri di livello superiore 'wrapper' (ossia smartTag, stdContent, customXML)
				parent = remove_upper_wrapper(parent, false);
				
				/**
				 *  
				 * Inizialmente, verifico la presenza di tabelle (ammesse per: P, RunDel, RunIns, RunTrackChange): 
				 * - serve che il padre di secondo livello sia <w:tc> e di terzo livello sia <w:tr> (a meno di wrapper in entrambi i casi)
				 * 
				 * osservazione:
				 * - in un tabella, il contenuto di celle adiacenti (ossia celle nella stessa <w:tr>) all'eventuale cella corrente <w:tc> e' da 
				 *   considerare nella stessa famiglia dei nodi contenuti nella cella <w:tc> corrente;
				 * in altre parole: le righe di una tabella si analizzano come testo contiguo
				 *   
				 * Successivamente, ottengo il contenuto di nodi fratelli:
				 * - se sono in una tabella: la lista nodi fratelli e' costituita dai figli di <w:tr>
				 * - altrimenti: la lista di nodi fratelli e' costituita dai figli del nodo padre diretto di <w:r>
				 * 
				 * osservazione: 
				 * - in docx4j non e' fornita un'interfaccia uniforme per ottenere i nodi figli di un dato nodo docx, di conseguenza sono 
				 *   necessarie le singole classi differenti per estrarre i nodi figli (ad es. "getContent()", "getAccOrBarOrBox()", etc.)
				 *  
				 */
				if (parent instanceof P) {
					P parent_casted = (P) parent;
					grandParent = remove_upper_wrapper(parent_casted.getParent(), true);
					if(grandParent instanceof Tc)
						runBrothers = run_nodes_from_table_row((Tc) grandParent);
					else
						remove_lower_wrapper(parent_casted.getContent(), runBrothers);
				}
				else if (parent instanceof RunDel) {
					RunDel parent_casted = (RunDel) parent;
					grandParent = remove_upper_wrapper(parent_casted.getParent(), true);
					if(grandParent instanceof Tc)
						runBrothers = run_nodes_from_table_row((Tc) grandParent);
					else
						remove_lower_wrapper(parent_casted.getCustomXmlOrSmartTagOrSdt(), runBrothers);
				}
				//classe instanziata per i tag 'rt' e 'rubyBase', mai figli di Tc
				else if (parent instanceof CTRubyContent) {
					CTRubyContent parent_casted = (CTRubyContent) parent;
					remove_lower_wrapper(parent_casted.getEGRubyContent(), runBrothers);
				}
				// mai figlio di Tc
				else if (parent instanceof P.Hyperlink) {
					P.Hyperlink parent_casted = (P.Hyperlink) parent;
					remove_lower_wrapper(parent_casted.getContent(), runBrothers);
				}
				// mai figlio di Tc
				else if (parent instanceof CTSimpleField) {
					CTSimpleField parent_casted = (CTSimpleField) parent;
					remove_lower_wrapper(parent_casted.getContent(), runBrothers);
				}
				else if (parent instanceof RunIns) {
					RunIns parent_casted = (RunIns) parent;
					grandParent = remove_upper_wrapper(parent_casted.getParent(), true);
					if(grandParent instanceof Tc)
						runBrothers = run_nodes_from_table_row((Tc) grandParent);
					else
						remove_lower_wrapper(parent_casted.getCustomXmlOrSmartTagOrSdt(), runBrothers);
				}
				//classe instanziata per i tag 'moveFrom' e 'moveTo'
				else if (parent instanceof RunTrackChange) {
					RunTrackChange parent_casted = (RunTrackChange) parent;					
					grandParent = remove_upper_wrapper(parent_casted.getParent(), true);
					if(grandParent instanceof Tc)
						runBrothers = run_nodes_from_table_row((Tc) grandParent);
					else
						remove_lower_wrapper(parent_casted.getAccOrBarOrBox(), runBrothers);
				}
				else {
					// Anomalo, in questo caso NON provo a risalire la gerarchia per cercare nodi di tipo <w:tc> e non individuo nodi fratelli
					System.out.println("Non ho identificato con nessuna classe il nodo padre: " + parent + ", di classe: " + lastName(parent.getClass().getName())
						+ ", proseguo l'esecuzione");
					runBrothers.add(run);
				}
				
				/**
				 * Check contenuto della variabile runBrothers
				 */
				if(App.debug) {
					System.out.println("fratelli:  [");
					for(Object bro : runBrothers) {
						System.out.print("            ");
						if(bro instanceof JAXBElement)
							System.out.println("(JAXB) " + lastName(((JAXBElement<?>) bro).getValue().getClass().getName()) + ", ");
						else
							System.out.println(lastName(bro.getClass().getName()) + ", ");
					}
					System.out.println("           ]");
				}
				
				/**
				 * Check padri del nodo Run corrente
				 */
				if(App.debug)
					check_padri_run(run);
				/**
				 * Proseguo MAIN:
				 * A questo punto bisogna navigare la lista dei nodi fratelli 'runBrothers' e aggiornare 'plainTexts'
				 * Nota:
				 *   - I nodi fratelli sono un'unica famiglia, non sono ne' divisi da separatori ulteriori ne' sono presenti nodi wrapper!
				 *   - I nodi fratelli sono separati da nodi "di altre famiglie" direttamente dalla struttura dati plainTexts
				 */
				for(Object run_bro : runBrothers) {
					analyse_run_brother(run_bro);
				}
				
				// segnalo alla struttura dati che la famiglia e' stata analizzata totalmente
				plainTexts.endFamily();
						
	    	}
			else {
				if(App.debug) {
					System.out.println("---------------------------------------------------------------------");
					System.out.print("Skipping a run node");
				}
			}
			if(App.debug)
		    	System.out.println();
		}
		
		if(App.debug)
			plainTexts.printPlainTexts(false);
		
	}

	
	/**
	 * "work" e' il metodo principale della classe Elaborator; il suo workflow:
	 *  - inizializza i dizionari se necessario
	 *  - effettua il processamento dei nodi docx per ottimizzare la minimizzazione, utilizzando una regex generale per
	 *    i nominativi
	 *  - effettua la minimizzazione dei dati personali
	 *  - aggiorna il contenuto dei nodi docx
	 */
	public void work() {
		String plain;
		StringBuilder sb;
		List<StringBuilder> s_preMinimization = new ArrayList<>(),
				s_postMinimization = new ArrayList<>();
		List<EntryPoint> docx_entryPoints, minimization_entryPoints, unchangeable, eps;
		List<List<EntryPoint>> allMatchingEps = new ArrayList<>();
		
		if(persone.isEmpty()) {
			automatic = true;
			enableDictionaries();
		}
		
		if(App.debug) {
			System.out.println("=====================================================================");
	    	System.out.println("Stampa del puro testo dopo il pre-processamento");
	    	System.out.println("---------------------------------------------------------------------");
		}
		
		// pre-processamento di plainTexts per ottimizzazione: segnalo i blocchi semantici che "verosimilmente" contengono nominativi
		// i plainText che non contengono nominativi sono sostituiti con StringBuilder vuoti
		int n = 0; // --- variabile usate solo per stampe
		for(int index = 0; index < plainTexts.getPlainTexts().size(); index++) {
			sb = plainTexts.getPlainTexts().get(index);
			eps = Persona.preprocess(sb.toString(), index);
			unchangeable = new ArrayList<>();
			for(Persona p: keepUnchanged) {
				plainTexts.markUnchangeableEntryPoints(unchangeable, p.getRegex(), index);
			}
			updateUnchangeableViaConfig(unchangeable, index);
			if(eps.size() > 0) {
				StringBuilder enlighted = enlightNominatives(sb, eps, unchangeable);
				eps = recomputeEps(enlighted, index);
				s_preMinimization.add(sb);
				allMatchingEps.add(eps);
				n++;
				if(App.debug)
					System.out.println(sb.toString());
			}
			else
				s_preMinimization.add(new StringBuilder(""));
		}
		
		if(App.debug) {
			System.out.println("=====================================================================");
	    	System.out.println("Minimizzazione di " + n + " nodi Docx su un totale di " + s_preMinimization.size() + " nodi");
	    	System.out.println("---------------------------------------------------------------------");
		}
		// analizzo tutte le sotto-stringhe in cui ho raccolto i nodi Docx, filtrate dal pre-processamento
		int index = 0;
		int index_allMatchingEps = 0;
		for(StringBuilder s : s_preMinimization) {
			// se il testo deve essere minimizzato
			if(!s.toString().equals("")) {
				int finalIndex = index; // copia necessaria per lambda expression
				docx_entryPoints = plainTexts.getEntryPoints()
						.stream()
						.filter(x -> x.getIndex_PlainText() == finalIndex)
						.collect(Collectors.toList());
				minimization_entryPoints = allMatchingEps.get(index_allMatchingEps);
				index_allMatchingEps++;
				// rimuovo le occorrenze dei nominativi di ogni persona
				plain = s.toString();
				if(automatic){
					for(EntryPoint e : minimization_entryPoints){
						String possibleNominative = plain.substring(e.getFrom(), e.getTo());
						boolean found = false;
						StringBuilder current_name = new StringBuilder();
						StringBuilder current_surname = new StringBuilder();
						// per ogni possibile nominativo salvo il relativo nome-cognome da inserire nel file di associazioni
						for(String termine : possibleNominative.split("\\s")){
							if (dictionary.contains(termine.toUpperCase())) {
								found = true;
								current_name.append(termine.toUpperCase()).append(" ");
							} else {
								current_surname.append(termine.toUpperCase()).append(" ");
							}
						}
						// se almeno uno dei termini e' un nome, allora anonimizzo
						if(found) {
							String curr_id = utilsID.getID(possibleNominative);
							Persona.updateDocxEntrypoints(docx_entryPoints, e.getFrom(), possibleNominative.length(), curr_id.length());
							Persona.propagateChanges(curr_id, possibleNominative, minimization_entryPoints);
							plain = plain.replaceFirst(possibleNominative, curr_id);
							// aggiungo alla mappa delle associazioni l'id anononimo e la relativa coppia nome-cognome
							if(!associationsMap.containsKey(curr_id)) {
								String value = current_surname.toString() + current_name.toString();
								associationsMap.put(curr_id, value.trim());
							}
						}
					}
				}
				// se nominativi espressi in input
 				else {
					for(EntryPoint e : minimization_entryPoints){
						String possibleNominative = plain.substring(e.getFrom(), e.getTo());
						for (Persona p : persone) {
							// se una persona fa match, allora anonimizzo
							if(p.match(possibleNominative)){
								if(p.getId().equals("0")){
									String curr_id = utilsID.getID(possibleNominative);
									Persona.updateDocxEntrypoints(docx_entryPoints, e.getFrom(), possibleNominative.length(), curr_id.length());
									Persona.propagateChanges(curr_id, possibleNominative, minimization_entryPoints);
									plain = plain.replaceFirst(possibleNominative, curr_id);
									p.setId(curr_id);
								}
								else {
									Persona.updateDocxEntrypoints(docx_entryPoints, e.getFrom(), possibleNominative.length(), p.getId().length());
									Persona.propagateChanges(p.getId(), possibleNominative, minimization_entryPoints);
									plain = plain.replaceFirst(possibleNominative, p.getId());
								}
								break;
							}
						}
					}
					// riempio mappa associazioni id-cognome nome
					for (Persona p : persone) {
						associationsMap.put(p.getId(), p.getNameSurnameAssociationsValue());
					}
				}
 				// inserisco stringa minimizzata
				s_postMinimization.add(new StringBuilder(plain));
			} 
			// se il testo non deve essere minimizzato
			else {
				// inserisco la stringa che non necessitava di minimizzazione nella lista temporanea "s_postMinimization"
				s_postMinimization.add(plainTexts.getPlainTexts().get(index));
			}
			index++;
		}
		
		// sostituisco la variabile plainTexts corrente con la sua versione equivalente post-minimizzazione
		plainTexts.setPlainTexts(s_postMinimization);
		
		if(App.debug)
			plainTexts.printPlainTexts(true);
		
		// aggiorno il contenuto dei nodi Docx
		updateEntryPoints();

		// creo il file di associazioni id - nome cognome ordinando alfabeticamente per cognome
		if(App.debug) {
			System.out.println("=====================================================================");
			System.out.println("Scrittura del file di associazioni id-cognome nome");
		}
		associationsMap.entrySet().stream()
				.sorted(Map.Entry.comparingByValue())
				.forEach(e -> {
					try {
						if(!e.getKey().equals("0")) {
							String association = e.getValue() + " -> " + e.getKey();
							outputFileAssociations.write(association.replaceAll("\\s+", " ") + "\n");
						}
					} catch (IOException ioException) {
						System.out.println("Eccezione durante la scrittura nel file di associazioni: " + outputFileAssociations);
						ioException.printStackTrace();
						System.exit(6);
					}
				});
		try {
			outputFileAssociations.close();
		} catch(IOException e) {
			System.out.println("Eccezione durante la chiusura del file di associazioni: " + outputFileAssociations);
			e.printStackTrace();
			System.exit(7);
		}
		
		if(App.debug) {
			System.out.println("=====================================================================");
	    	System.out.println("Work done!");
		}

	}

	private List<EntryPoint> recomputeEps(StringBuilder enlighted, int index) {
		List<EntryPoint> eps = new ArrayList<>();
		boolean seekFirst = true;
		int from = -1;
		for(int i = 0; i < enlighted.toString().length(); i++){
			if (seekFirst && enlighted.charAt(i) != '_') {
				from = i;
				seekFirst = false;
			}
			else if(!seekFirst && enlighted.charAt(i) == '_'){
				// salvo EntryPoint solo se c'e' ancora un'espressione anonimizzabile dopo aver aggiunto 'unchangeable'
				if (Persona.preprocess(enlighted.toString(), -1).size() > 0)
					eps.add(new EntryPoint(null, index, from, i));
				seekFirst = true;
			}
		}
		//caso in cui un EntryPoint con un nominativo comprenda l'ultimo carattere di 'enlighted'
		if(!seekFirst)
			eps.add(new EntryPoint(null, index, from, enlighted.toString().length()));
		return eps;
	}

	/**
	 * Lorenzo Amorosa mangia => Lorenzo Amorosa _____
	 *
	 * @param s
	 * @param eps
	 * @param unchangeable
	 * @return
	 */
	private StringBuilder enlightNominatives(StringBuilder s, List<EntryPoint> eps, List<EntryPoint> unchangeable) {
		StringBuilder res =  new StringBuilder();
		for(int i = 0; i < s.toString().length(); i++)
			res.append("_");
		for(EntryPoint e : eps)
			res.replace(e.getFrom(), e.getTo(), s.substring(e.getFrom(), e.getTo()));
		for(EntryPoint e : unchangeable)
			res.replace(e.getFrom(), e.getTo(), pad('_', e.getTo() - e.getFrom()));
		return res;
	}

	private String pad(char c, int dim) {
		StringBuilder res = new StringBuilder();
		for(int i = 0; i < dim; i++)
			res.append(c);
		return res.toString();
	}

	/**
	 * Inizializzazione di una lista di persone impiegando i dizionari di nomi
	 */
	private void enableDictionaries() {
		String nome;
		persone = new ArrayList<>();
		
		if(App.debug) {
			System.out.println("=====================================================================");
	    	System.out.println("Caricamento dei dizionari");
	    	System.out.println();
		}
		// ci sono 14590 nomi (IT + EN)
		try {
			String f_name = "/" + "dictionaries" + "/" + "IT.txt";
			BufferedReader bf = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream(f_name)));
			while((nome = bf.readLine()) != null)
				dictionary.add(nome);
			bf.close();
			f_name = "/" + "dictionaries" + "/" + "EN.txt";
			bf = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream(f_name)));
			while((nome = bf.readLine()) != null)
				dictionary.add(nome);
			bf.close();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(5);
		}

	}

	private void updateUnchangeableViaConfig(List<EntryPoint> unchangeable, int index) {
		plainTexts.markUnchangeableEntryPoints(unchangeable, toKeepViaConfig, index);
	}
	
	/**
	 * Aggiorno il contenuto di tutti i nodi Docx testuali (ossia Text e DelText) dopo la minimizzazione dei dati
	 */
	private void updateEntryPoints() {
		Object docxNode;
		String textMinimized;
		
		if(App.debug) {
			System.out.println("=====================================================================");
	    	System.out.println("Update dei nodi Docx");
		}
		
		for(EntryPoint e : plainTexts.getEntryPoints()){
			docxNode = e.getDocxNode();
			if(docxNode instanceof Text) {
				Text text = (Text) docxNode;
				textMinimized = plainTexts.getPlainTexts().get(e.getIndex_PlainText()).substring(e.getFrom(), e.getTo());
				text.setValue(textMinimized);
			}
			else if (docxNode instanceof DelText) {
				DelText text = (DelText) docxNode;
				textMinimized = plainTexts.getPlainTexts().get(e.getIndex_PlainText()).substring(e.getFrom(), e.getTo());
				text.setValue(textMinimized);
			}
		}
		
	}
	
	/**
	 * Check gerarchia dei padri nodo run
	 * @param run nodo run del quale si analizza la gerarchia
	 */
	private void check_padri_run(R run) {
		Object tmp = run;
		String pre_wrap = null, post_wrap = null;
		int level = 1;	
		
		do {
			if(tmp instanceof Child && tmp != null) {
				tmp = removeJAXB(((Child) tmp).getParent());
				
				if (tmp != null) {
					String nome = lastName(tmp.getClass().getName());
					pre_wrap = "padre di livello " + fixedLengthString("" + level, 2) + ": (ID = " + fixedLengthString("" + tmp.hashCode(), 10) + ") " + nome;				
				}
				tmp = remove_upper_wrapper(tmp, true);
				if (tmp != null) {
					String nome = lastName(tmp.getClass().getName());
					post_wrap = "padre di livello " + fixedLengthString("" + level, 2) + ": (ID = " + fixedLengthString("" + tmp.hashCode(), 10) + ") " + nome;
					level++;
					
					//gestione smart delle stampe: evito duplicati
					if(pre_wrap.equals(post_wrap))
						System.out.println("            " + post_wrap);
					else {
						System.out.println("(PRE-WRAP)  " + pre_wrap);
						System.out.println("(POST-WRAP) " + post_wrap);
					}
				}	
				
				// gestisco bug docx4j: capita che un nodo sia padre di se stesso (loop infinito)
				if(tmp != null && tmp == removeJAXB(((Child) tmp).getParent()))
					break;
				
			}
			else {
				if (tmp != null)
					System.out.println("Root: " + lastName(tmp.getClass().getName()));
				else
					System.out.println("Root is null");
			}		
			//la classe padre di tutti i nodi e' Document, ma questo e' vero solo nel main document (non in header, footer, etc.) !!!
		} while(tmp != null);	
	}

	private void analyse_run_brother(Object run_bro) {
		
		run_bro = removeJAXB(run_bro);
		
		if (run_bro instanceof R)
			analyse_run((R) run_bro);
		else if (run_bro instanceof TcSeparator)
			addText(run_bro, SEP_docx);
		else if(run_bro instanceof CTBookmark) // bookmarkStart
			ignore(run_bro);
		else if(run_bro instanceof CTMarkupRange) // bookmarkEnd
			ignore(run_bro);
		else if(run_bro instanceof CommentRangeStart) // CommentRangeStart
			ignore(run_bro);
		else if(run_bro instanceof CommentRangeEnd) // commentRangeEnd
			ignore(run_bro);
		else if(run_bro instanceof CTTrackChange) // customXmlDelRangeStart, customXmlInsRangeStart, customXmlMoveFromRangeStart, customXmlMoveToRangeStart
			block(run_bro);
		else if(run_bro instanceof CTCustomXmlRun) // customXml: gia' esplorato e segnato contenuto in runBrothers
			ignore(run_bro);
		else if(run_bro instanceof CTMarkup) // customXmlDelRangeEnd, customXmlInsRangeEnd, customXmlMoveFromRangeEnd, customXmlMoveToRangeEnd
			block(run_bro);
		else if(run_bro instanceof CTMoveBookmark) // moveFromRangeStart, moveToRangeStart
			block(run_bro);
		else if(run_bro instanceof CTMoveFromRangeEnd) // moveFromRangeEnd
			block(run_bro);
		else if(run_bro instanceof CTMoveToRangeEnd) // moveToRangeEnd
			block(run_bro);
		else if(run_bro instanceof RunTrackChange) // moveTo, moveFrom
			block(run_bro);
		else if(run_bro instanceof RangePermissionStart) // permStart
			ignore(run_bro);
		else if(run_bro instanceof CTPerm) // permEnd
			ignore(run_bro);
		else if(run_bro instanceof ProofErr) // proofErr
			ignore(run_bro);
		else if(run_bro instanceof CTRel) // subDoc
			block(run_bro);
		else if(run_bro instanceof SdtRun) // sdt: gia' esplorato e segnato contenuto in runBrothers
			ignore(run_bro);
		else if(run_bro instanceof CTSmartTagRun) // smartTag: gia' esplorato e segnato contenuto in runBrothers
			ignore(run_bro);
		else if(run_bro instanceof P.Hyperlink) // hyperlink
			block(run_bro);
		else if(run_bro instanceof RunDel) // del
			block(run_bro);
		else if(run_bro instanceof CTSimpleField) // fldSimple
			block(run_bro);
		else if(run_bro instanceof RunIns) // ins
			block(run_bro);
		else {
			block(run_bro);
			if(App.debug){
				System.out.println("Inserisco blocco, fratello di <w:r> non riconosciuto: " + lastName(run_bro.getClass().getName()));
			}
		}
		
	}

	/**
	 * 
	 * Gestione dei figli di un singolo nodo run
	 * Per ogni nodo contenuto in R:
	 *   - ne viene estratto il testo o
	 * 	 - viene ignorato o
	 * 	 - viene segnalata la fine del "blocco semantico"
	 * 
	 * @param run singolo nodo <w:r> del quale si analizza il contenuto, aggiornando plainTexts
	 * 
	 */
	private void analyse_run(R run) {
		List<Object> runSons = run.getContent();
		Object runSon;
		
		// se il nodo non e' gia' stato elaborato: aggiorno Hashmap; altrimenti: return
		if(runNodesElaborated.get(run.hashCode()) == false) 
			runNodesElaborated.replace(run.hashCode(), Boolean.valueOf(true));
		else
			return;
		
		for(int j = 0; j < runSons.size(); j++) {
			runSon = runSons.get(j);
			
			runSon = removeJAXB(runSon);
			
			if(runSon instanceof Text)
				addText(runSon, ((Text)runSon).getValue());
			else if(runSon instanceof DelText)
				addText(runSon, ((DelText) runSon).getValue());
			else if(runSon instanceof Br) 
				addText(runSon, SEP_docx);
			else if(runSon instanceof AnnotationRef)
				ignore(runSon);
			else if(runSon instanceof Ptab)
				ignore(runSon);
			else if(runSon instanceof DayLong)
				block(runSon);
			else if(runSon instanceof FootnoteRef)
				ignore(runSon);
			else if(runSon instanceof CommentReference)
				ignore(runSon);
			else if(runSon instanceof Sym)
				block(runSon);
			else if(runSon instanceof FldChar)
				block(runSon);
			else if(runSon instanceof YearShort)
				block(runSon);
			else if(runSon instanceof Cr)
				addText(runSon, SEP_docx);
			else if(runSon instanceof CTRuby) 
				block(runSon);
			else if(runSon instanceof ContinuationSeparator)
				ignore(runSon);
			else if(runSon instanceof MonthLong)
				block(runSon);
			else if(runSon instanceof MonthShort)
				block(runSon);
			else if(runSon instanceof CTFtnEdnRef) 
				ignore(runSon);
			else if(runSon instanceof CTObject) 
				block(runSon);
			else if(runSon instanceof LastRenderedPageBreak)
				ignore(runSon);
			else if(runSon instanceof Separator)
				block(runSon);
			else if(runSon instanceof Drawing)
				block(runSon);
			else if(runSon instanceof Tab)
				addText(runSon, SEP_docx);
			else if(runSon instanceof Pict)
				block(runSon);
			else if(runSon instanceof SoftHyphen)
				ignore(runSon);
			else if(runSon instanceof PgNum)
				block(runSon);
			else if(runSon instanceof EndnoteRef)
				ignore(runSon);
			else if(runSon instanceof NoBreakHyphen)
				block(runSon);
			else if(runSon instanceof DayShort)
				block(runSon);			
			else if(runSon instanceof YearLong)
				block(runSon);					
			else if(runSon instanceof Ptab)
				ignore(runSon);
			else {
				if(App.debug)
					System.out.println("Non ho identificato con nessuna classe il nodo: " + runSon + ", di classe: " + lastName(runSon.getClass().getName()) + ": BLOCCO");
				block(runSon);
			}		
		}
		
	}

	/**
	 * Questo metodo, da invocare su un padre diretto di <w:r>, esegue l'unwrapping dei fratelli di <w:r>, ossia rimuove da 'run_brothers'
	 * 'customXML', 'sdt' ed 'sdtContent', 'smartTag' e aggiunge a 'unwrapped_run_brothers' i loro figli. 
	 * I nodi run e fratelli vengono aggiunti in 'unwrapped_run_brothers'.
	 * L'analisi effettiva dei nodi viene delegata.
	 * 
	 * @param run_brothers pre-unwrapping
	 * @param unwrapped_run_brothers post-unwrapping
	 */
	static void remove_lower_wrapper(List<Object> run_brothers, List<Object> unwrapped_run_brothers) {
		List<Object> tmp_brothers;
		
		for(Object bro : run_brothers) {
			
			bro = removeJAXB(bro);
			
			if (bro instanceof SdtElement) {
				SdtElement bro_casted = (SdtElement) bro;
				if (bro_casted.getSdtContent() instanceof SdtContent) {
					SdtContent bro_casted_2 = (SdtContent) bro_casted.getSdtContent();				
					tmp_brothers = bro_casted_2.getContent();		
					remove_lower_wrapper(tmp_brothers, unwrapped_run_brothers);
				}
				else {
					System.out.println("Attenzione! Trovato un nodo di classe " + lastName(bro_casted.getSdtContent().getClass().getName()) 
							+ " come padre di un nodo di classe " + lastName(bro_casted.getClass().getName()));
				}
			}
			else if (bro instanceof CTCustomXmlElement) {
				CTCustomXmlElement bro_casted = (CTCustomXmlElement) bro;
				tmp_brothers = bro_casted.getContent();		
				remove_lower_wrapper(tmp_brothers, unwrapped_run_brothers);
			}
			else if (bro instanceof CTSmartTagRun) {
				CTSmartTagRun bro_casted = (CTSmartTagRun) bro;
				tmp_brothers = bro_casted.getContent();		
				remove_lower_wrapper(tmp_brothers, unwrapped_run_brothers);
			}
			// il nodo fratello 'bro' non e' un wrapper: lo aggiungo ad 'unwrapped_run_brothers'
			else {
				unwrapped_run_brothers.add(bro);
			}
		}
		return;
	}

	/**
	 * Da qui si inizia il recupero dei nodi fratelli di <w:r> contenuti tutti in una stessa riga di una stessa tabella
	 * Nelle gerarchie va tenuto conto dei wrapper (figli di tc), bisogna quindi eventualmente risalire la gerarchia
	 * 
	 * @param table_cell tableCell contenuta nella riga della quale si vuole ottenere il contenuto
	 * @return lista contenente tutti gli elementi presenti nella riga corrente
	 */
	static List<Object> run_nodes_from_table_row(Tc table_cell) {
		List<Object> tr_content, run_brothers = new ArrayList<>();
		Object parent = table_cell.getParent();
		
		parent = removeJAXB(parent);
		
		while(! (parent instanceof Tr)) {
			if(parent instanceof Child) {
				parent = ((Child) parent).getParent();
				parent = removeJAXB(parent);
			}
			else {
				System.out.println("Errore fatale: è stato trovato un elemento <w:tc> non contenuto in un <w:tr>");
				return null;
			}
		}
		
		tr_content = ((Tr) parent).getContent();
		
		for(Object tr_son : tr_content) {
			run_nodes_from_table_cell(tr_son, run_brothers);
		}
		
		return run_brothers;
	}

	/**
	 * 
	 * Dati i figli di una riga di una tabella <w:tr>, discendo la gerarchia cercando i nodi <w:r> e relativi fratelli
	 * 
	 * navigo i nodi: 
	 * - customXml (cell-level), conterra' un <w:tc>
	 * - sdt (-> sdtContent)
	 * - tc
	 * 
	 * estraggo i figli dai nodi:
	 * - del
	 * - ins
	 * - moveFrom
	 * - moveTo
	 * - P, etc.
	 * 
	 * @param tr_son nodo figlio di Tr da analizzare
	 * @param run_brothers lista gia' inizializzata di nodi R (e fratelli) contenuti complessivamente in Tr, 'live list' non 'snapshot'
	 */
	private static void run_nodes_from_table_cell(Object tr_son, List<Object> run_brothers) {
		List<Object> new_sons;
		
		tr_son = removeJAXB(tr_son);
		
		// Tc, figlio principale di Tr, possibile padre di <w:p>, <w:del> ... tutti possibili padri a loro volta di <w:r>
		if(tr_son instanceof Tc) {
			new_sons = ((Tc) tr_son).getContent();
			//separatore aggiunto manualmente per celle adiacenti di una tabella: aggiungo cosi' un SEP_docx
			run_brothers.add(new TcSeparator());
			for(Object son : new_sons)
				run_nodes_from_table_cell(son, run_brothers);
			//separatore aggiunto manualmente per celle adiacenti di una tabella: aggiungo cosi' un SEP_docx
			run_brothers.add(new TcSeparator());
		}
		// wrapper
		else if (tr_son instanceof CTCustomXmlElement){
			new_sons = ((CTCustomXmlElement) tr_son).getContent();
			for(Object son : new_sons)
				run_nodes_from_table_cell(son, run_brothers);
		}
		// wrapper
		else if (tr_son instanceof SdtElement){
			SdtContent sdt_content = ((SdtElement) tr_son).getSdtContent();	
			new_sons = sdt_content.getContent();
			for(Object son : new_sons)
				run_nodes_from_table_cell(son, run_brothers);
		}
		/**
		 *  in questo blocco ci si finisce con nodi NON wrapper, possono quindi contenere testo
		 *  qui segue l'analisi dei figli di Tc, tutti possibili padri di <w:r>, raggiunti ricorsivamente
		 *  notare che si finisce in questo blocco anche nell'analisi di figli di Tr ignorabili: trPr, proofErr, permStart...
		 *  il check sulla classe viene fatto direttamente nel metodo run_nodes_from_parent, per analizzare solo possibili padri di <w:r>
		 **/
		else {
			run_nodes_from_parent(tr_son, run_brothers);
		}
		
	}

	/**
	 * 
	 * @param initial_parent questo nodo e' padre DIRETTO di <w:r>, operazioni di 'unwrapping' gia' effettuate
	 * @param run_brothers lista gia' inizializzata di nodi R (e fratelli) contenuti complessivamente in Tr, 'live list' non 'snapshot'
	 * 
	 * osservazione: i nodi <w:p>, <w:del> etc. potrebbero contenere <w:r> direttamente ma, allo stesso tempo, ulteriori wrapper in-inline
	 *               contenenti a loro volta altri <w:r>. Il metodo di unwrapping 'get_all_direct_parents' gestisce opportunamente questa situazione.
	 *               
	 */
	private static void run_nodes_from_parent(Object initial_parent, List<Object> run_brothers) {
		List<Object> parents_unwrapped = new ArrayList<>(), tmp_sons = null;
		
		initial_parent = removeJAXB(initial_parent);		
		
		get_all_direct_parents(initial_parent, parents_unwrapped);
		
		/**
		 *   ora si continua qui facendo l'analisi dei figli dei 'parents_unwrapped' di <w:r> e
		 *   si tiene traccia dei fratelli di <w:r>, si delega l'analisi dei figli di <w:r> ad un altro metodo
		 *   
		 *   E' importante ricordare che ora ci si occupa solo del riempimento di 'run_brothers', non si analizzano
		 */
		boolean ignora;
		for(Object parent : parents_unwrapped) {
			
			parent = removeJAXB(parent);

			//'parent' contiene <w:r> e fratelli
			ignora = false;
			if(parent instanceof ContentAccessor)
				tmp_sons = ((ContentAccessor) parent).getContent();
			else if(parent instanceof SdtContent)
				tmp_sons = ((SdtContent) parent).getContent();
			else if(parent instanceof CTCustomXmlElement)
				tmp_sons = ((CTCustomXmlElement) parent).getContent();
			else if(parent instanceof RunDel)
				tmp_sons = ((RunDel) parent).getCustomXmlOrSmartTagOrSdt();
			else if(parent instanceof RunIns)
				tmp_sons = ((RunIns) parent).getCustomXmlOrSmartTagOrSdt();
			else if(parent instanceof RunTrackChange)
				tmp_sons = ((RunTrackChange) parent).getAccOrBarOrBox();
			else {
				if(App.debug)
					System.out.println("Trovato un nodo di classe " + lastName(parent.getClass().getName()) 
						+ ", fratello dei padri di un nodo di classe <w:r>, lo ignoro");
				ignora = true;
			}
			
			if(!ignora)
				for(Object son : tmp_sons)
					run_brothers.add(son);
		}	
		
	}


	/**
	 * rimozione dei figli 'wrapper' di livello inferiore (smartTag, stdContent, customXML)
	 * !check su 'finti wrapper': i nodi di cui sopra potrebbero essere indipendenti e non dei wrapper (ad esempio avendo figli <w:r>)!
	 *   
	 *  osservazione importante: questo metodo assomiglia molto a "remove_lower_wrapper", ma la semantica e' diversa:
	 *   - in "remove_lower_wrapper" se scendendo nella gerarchia incontro un nodo di tipo Block (es. P, MoveTo, etc.) interrompo l'esplorazione
	 *   - in "get_all_direct_parents" non interrompo mai la discesa della gerachia: questo metodo serve nella visita esaustiva delle tabelle
	 *   
	 * @param initial_parent nodo docx, possibile padre diretto di <w:r>; se non wrapper aggiunto direttamente alla lista parents_unwrapped
	 * @param parents_unwrapped lista di padri diretti di <w:r> ricorsivamente popolata, 'live list' non 'snapshot'
	 */
	private static void get_all_direct_parents(Object initial_parent, List<Object> parents_unwrapped) {
		
		List<Object> tmp_sons;
		
		initial_parent = removeJAXB(initial_parent);
		
		if (initial_parent instanceof SdtElement) {
			SdtElement parent_casted = (SdtElement) initial_parent;
			if (parent_casted.getSdtContent() instanceof SdtContent) {
				SdtContent parent_casted_2 = (SdtContent) parent_casted.getSdtContent();				
				tmp_sons = parent_casted_2.getContent();
				add_if_direct_parent(parent_casted_2, parents_unwrapped, tmp_sons);
			}
			else {
				System.out.println("Attenzione! Trovato un nodo di classe " + lastName(parent_casted.getSdtContent().getClass().getName()) 
						+ " come padre di un nodo di classe " + lastName(parent_casted.getClass().getName()));
			}
		}
		else if (initial_parent instanceof CTCustomXmlElement) {
			CTCustomXmlElement parent_casted = (CTCustomXmlElement) initial_parent;
			tmp_sons = parent_casted.getContent();		
			add_if_direct_parent(parent_casted, parents_unwrapped, tmp_sons);
		}
		else if (initial_parent instanceof CTSmartTagRun) {
			CTSmartTagRun parent_casted = (CTSmartTagRun) initial_parent;
			tmp_sons = parent_casted.getContent();
			add_if_direct_parent(parent_casted, parents_unwrapped, tmp_sons);
		}
		/** in questi 2 blocchi seguenti aggiungo a 'parents_unwrapped' tutti i padri di <w:r> raggiunti ricorsivamente, da cui sono stati rimossi 
		 * i wrapper (sia esterni, sia annidati all'interno)
		 * osservazione: CTRubyContent (figlio di CTRuby) e' raggiungibile SOLO da sotto (ossia risalendo da <w:r>), essendo figlio solo di <w:r>
		 */
		else if (initial_parent instanceof P ||  initial_parent instanceof P.Hyperlink || initial_parent instanceof CTSimpleField) {
			//ok per: P, P.Hyperlink, CTSimpleField
			//NON ok per: RunDel, RunIns, RunTrackChange: per loro non si puo' nemmeno sfruttare una super-classe ... :(
			ContentAccessor parent_casted = (ContentAccessor) initial_parent;
			tmp_sons = parent_casted.getContent();
			add_if_direct_parent(parent_casted, parents_unwrapped, tmp_sons);
		}
		else if (initial_parent instanceof RunDel) {	
			RunDel parent_casted = (RunDel) initial_parent;
			tmp_sons = parent_casted.getCustomXmlOrSmartTagOrSdt();
			add_if_direct_parent(parent_casted, parents_unwrapped, tmp_sons);
		}
		else if (initial_parent instanceof RunIns) {
			RunIns parent_casted = (RunIns) initial_parent;
			tmp_sons = parent_casted.getCustomXmlOrSmartTagOrSdt();
			add_if_direct_parent(parent_casted, parents_unwrapped, tmp_sons);
		}
		else if (initial_parent instanceof RunTrackChange) {
			RunTrackChange parent_casted = (RunTrackChange) initial_parent;
			tmp_sons = parent_casted.getAccOrBarOrBox();
			add_if_direct_parent(parent_casted, parents_unwrapped, tmp_sons);
		}
		else {		
			// si arriva in questo blocco se "initial_parent" e' di classe R (o un suo fratello): si ritorna senza fare nulla
			if(App.debug)
				System.out.println("Raggiunto un nodo " + lastName(initial_parent.getClass().getName()) 
					+ ", interrompo ricerca");
		}	
		
		return;
	}

	private static void add_if_direct_parent(Object initial_parent, List<Object> parents_unwrapped, List<Object> tmp_sons) {
		boolean direct_parent = false;
		for(Object son : tmp_sons) {
			
			son = removeJAXB(son);
			
			//check utile per eventuali 'finti wrapper', ossia nodi di classe ritenuta wrapper ma che contengono in realta' direttamente <w:r>
			if(!direct_parent && son instanceof R) {
				direct_parent = true;
				parents_unwrapped.add(initial_parent);
			}
			// si finisce nell "else" di "get_all_direct_parents" se "son" e' di classe R (o un suo fratello)
			get_all_direct_parents(son, parents_unwrapped);
		}
	}

	/**
	 * rimozione dei padri 'wrapper' di livello superiore (smartTag, stdContent, customXML)
	 * !check su 'finti wrapper': i nodi di cui sopra potrebbero essere indipendenti e non dei wrapper (ad esempio avendo padre <w:body>)!
	 * 
	 * @param parent padre pre-unwrapping
	 * @param tableCell boolean: true => cerco Tc; false => non cerco Tc
	 * @return padre post-unwrapping
	 */
	static Object remove_upper_wrapper(Object parent, boolean tableCell) {
		
		parent = removeJAXB(parent);
		
		Object final_parent = parent;
		
		if (parent instanceof CTSdtContentRun) {
			CTSdtContentRun parent_casted = (CTSdtContentRun) parent;
			if (parent_casted.getParent() instanceof SdtRun) {
				SdtRun parent_casted_2 = (SdtRun) parent_casted.getParent();
				final_parent = remove_upper_wrapper(parent_casted_2.getParent(), tableCell);
			}
			else {
				System.out.println("Attenzione! Trovato un nodo di classe " + lastName(parent_casted.getParent().getClass().getName()) 
						+ " come padre di un nodo di classe " + lastName(parent_casted.getClass().getName()));
			}
		}
		else if (parent instanceof CTCustomXmlRun) {
			CTCustomXmlRun parent_casted = (CTCustomXmlRun) parent;
			final_parent = remove_upper_wrapper(parent_casted.getParent(), tableCell);
		}
		else if (parent instanceof CTSmartTagRun) {
			CTSmartTagRun parent_casted = (CTSmartTagRun) parent;
			final_parent = remove_upper_wrapper(parent_casted.getParent(), tableCell);
		}
		
		//check su eventuale 'finto wrapper', ossia nodi di classe ritenuta wrapper ma che sono alla base della gerarchia
		//eventuale recupero di TableCell <w:tc>
		if (final_parent instanceof P || final_parent instanceof RunDel || final_parent instanceof CTRubyContent 
				|| final_parent instanceof P.Hyperlink || final_parent instanceof RunIns || final_parent instanceof RunTrackChange
				|| final_parent instanceof CTSimpleField || (tableCell && final_parent instanceof Tc))		
			return final_parent;
		else
			return parent;
	}

	private void block(Object obj) {
		if(App.debug)
			System.out.println(lastName(obj.getClass().getName()) + ": block");
		plainTexts.block(obj);
	}

	private void ignore(Object obj) {
		if (App.debug)
			System.out.println(lastName(obj.getClass().getName()) + ": ignore");
		plainTexts.ignore(obj);
	}

	private void addText(Object obj, String toAppend) {
		if (App.debug)
			System.out.println(lastName(obj.getClass().getName()) + ": " + toAppend);
		plainTexts.addText(obj, toAppend);
	}
	
}

class TcSeparator {
	// classe marker per segnalare l'inizio/fine di un nodo <w:tc>
}
