package docxAnonymizer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * @author lorenzo
 * 
 * Campi della classe: 
 *  - plainTexts:         lista di StringBuilder contenenti porzioni di testo da processare insieme
 *  - entryPoints:        lista di riferimenti ai nodi Docx, ognuno relativo a un unico StringBuilder di plainTexts e con propri boundaries                    
 *  - addNew:         	  variabile che rappresenta lo stato di elaborazione della struttura dati
 */
public class PlainTexts {
	
	private List<StringBuilder> plainTexts;
	private List<EntryPoint> entryPoints;
	private boolean addNew;

	public PlainTexts() {
		this.plainTexts = new ArrayList<>();
		this.entryPoints = new ArrayList<>();
		this.addNew = true;
	}
	
	public List<StringBuilder> getPlainTexts() {
		return plainTexts;
	}
	
	public void setPlainTexts(List<StringBuilder> plainTextsMinimized) {
		plainTexts = plainTextsMinimized;
	}

	public List<EntryPoint> getEntryPoints() {
		return entryPoints;
	}

	public void block(Object docxNode) {
		addNew = true;
	}
	
	public void endFamily() {
		addNew = true;
	}

	public void ignore(Object docxNode) {
		// do nothing
	}

	// docxNode solitamente instanza di: Text, Br, Cr, Tab
	public void addText(Object docxNode, String toAppend) {
		int from;
		// nuovo stato, nuovo StringBuilder
		if(addNew) {
			from = 0;
			plainTexts.add(new StringBuilder(toAppend));
		} else {
			from = plainTexts.get(plainTexts.size() - 1).toString().length();
			plainTexts.get(plainTexts.size() - 1).append(toAppend);
		}
		entryPoints.add(new EntryPoint(docxNode, plainTexts.size() - 1, from, from + toAppend.length()));
		addNew = false;
	}
	
	public void printPlainTexts(boolean minimizzato) {
		System.out.println("=====================================================================");
		if(!minimizzato)
			System.out.println("Stampa dell'insieme di plain texts ottenuti prima della minimizzazione");
		else
			System.out.println("Stampa dell'insieme di plain texts ottenuti dopo la minimizzazione");
		
		for(StringBuilder s: plainTexts) {
			int curr_index = plainTexts.indexOf(s);
			// Supplier<> => interfaccia per riusare lo stesso stream piu' volte
			Supplier<Stream<EntryPoint>> curr_entryPoints = 
					() -> entryPoints.stream().filter(x -> x.getIndex_PlainText() == curr_index);
			System.out.println("---------------------------------------------------------------------");
			System.out.println("[" + curr_index + "]");
			System.out.println("text value             : " + s.toString());
			System.out.println("entry points number    : " + curr_entryPoints.get().count());
			System.out.print  ("entry points (from, to): | "); 
			curr_entryPoints.get().forEach(x -> System.out.print("(" + x.getFrom() + ", " + x.getTo() +") | "));
			System.out.println();
		}
	}

	/**
	 * Metodo che consente di markare come non modificabili dei nodi Docx e il relativo contenuto
	 * 
	 * @param unchangeable lista di entryPoint che non deve essere processata da Elaborator
	 * @param regex espressione con cui determinare gli entryPoint da aggiungere a unchangeable
	 * @param index l'indice dello StringBuilder che contiene la porzione di testo relativa alla lista unchangeable
	 */
	public void markUnchangeableEntryPoints(List<EntryPoint> unchangeable, String regex, int index) {
		String text = this.getPlainTexts().get(index).toString();
		int from = 0;
		boolean continua;
		Matcher matcher;
		
		do {
			continua = false;
			matcher = Pattern.compile(regex).matcher(text);
			if(matcher.find(from)) {
				continua = true;
				unchangeable.add(new EntryPoint(null, index, matcher.start(), matcher.end()));
				from = matcher.end() - 1;
			}
		} while(continua);	
		
	}
	
}

class EntryPoint{

	private Object docxNode; 
	private int index_PlainText;
	private int from; 
	private int to;
	
	public EntryPoint(Object docxNode, int index_PlainText, int from, int to) {
		super();
		this.docxNode = docxNode;
		this.index_PlainText = index_PlainText;
		this.from = from;
		this.to = to;
	}
	
	public Object getDocxNode() {
		return docxNode;
	}

	public int getIndex_PlainText() {
		return index_PlainText;
	}

	public int getFrom() {
		return from;
	}

	public int getTo() {
		return to;
	}
	
	public void setFrom(int from) {
		this.from = from;
	}

	public void setTo(int to) {
		this.to = to;
	}
	
}