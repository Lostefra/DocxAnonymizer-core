package docxAnonymizer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.xml.bind.JAXBException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.docx4j.jaxb.XPathBinderAssociationIsPartialException;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.CommentsPart;
import org.docx4j.openpackaging.parts.WordprocessingML.EndnotesPart;
import org.docx4j.openpackaging.parts.WordprocessingML.FooterPart;
import org.docx4j.openpackaging.parts.WordprocessingML.FootnotesPart;
import org.docx4j.openpackaging.parts.WordprocessingML.HeaderPart;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.openpackaging.parts.relationships.Namespaces;
import org.docx4j.openpackaging.parts.relationships.RelationshipsPart;
import org.docx4j.relationships.Relationship;

/**
 * 
 * @author lorenzo
 *
 * Classe contenente il Main di Docx Anonymizer.
 * 
 * Parametri di invocazione:
 * -i <inputFile>   [Obbligatorio, file Docx del quale si vogliono minimizzare i dati contenuti]
 * -o <outputFile>  [Facoltativo, file Docx prodotto da Docx Anonymizer; se non espresso impostato a default]
 * -m <minimize>    [Facoltativo, file contenente riga per riga i nominativi da minimizzare, nella forma: "<nome1>:<nome2>:[...]:<nomeN>;<cognome>"]
 *                  [  Nota bene: qualora il file fosse assente Docx Anonymizer impieghera' i dizionari nella ricerca dei nominativi               ]
 * -kn <keepNames>  [Facoltativo, file contenente riga per riga i nominativi da NON minimizzare, nella forma di cui sopra]
 * -ke <keepExpr>   [Facoltativo, file contenente riga per riga delle espressioni da NON minimizzare (non nominativi)]
 *                  [  Nota bene: alcune espressioni sono gia' impostate a default in config/keep_unchanged.txt      ]
 * -d <debug>       [Facoltativo, se tale opzione e' impostata sono stampate a video informazioni sull'esecuzione]
 * 
 * In alternativa, i nominativi da minimizzare possono essere forniti direttamente da linea di comando nello stesso formato indicato precedentemente
 * Cio' e' possibile anche per i nominativi da NON minimizzare, andra' in tal caso anteposto al primo nome un punto esclamativo '!'
 *
 */
public class App {
	
	//debug=true attiva alcune stampe utili
	public static boolean debug = false;
	//log4jPrints=false disattiva stampe di routine usate da docx4j
	public static final boolean log4jPrints = false;
	private static String inputFile;
	private static String outputFile;
	private static String outputFileAssociations;
	private static String minimizeFile;
	private static String keepUnchangedNamesFile;
	private static String keepUnchangedExprFile;
    //elenco persone di cui minimizzare i dati
    private static List<Persona> persone = new ArrayList<Persona>();
    //persone di cui NON minimizzare i dati
    private static List<Persona> keepUnchanged = new ArrayList<Persona>();
	
    //check input and inizializzazione variabili
	private static void parametersCheck(String[] args) {
    	CommandLine commandLine;
    	Option option_i = Option.builder("i")
                .required(true)
                .desc("The docx input file to anonymize")
                .longOpt("input-file")
                .hasArg()
                .build();
        Option option_o = Option.builder("o")
        		.required(false)
        		.desc("The docx output file generated")
        		.longOpt("output-file")
        		.hasArg()
        		.build();
        Option option_m = Option.builder("m")
        		.required(false)
        		.desc("The file with names and surnames to minimize. "
        				+ "It must contain one expression per line. "
        				+ "Names are separated by ':' between them and by ';' from the surname")
        		.longOpt("minimize")
        		.hasArg()
        		.build();
        Option option_kn = Option.builder("kn")
        		.required(false)
        		.desc("The file with names and surnames to keep unchanged (no minimization). "
        				+ "It must contain one expression per line. "
        				+ "Names are separated by ':' between them and by ';' from the surname")
        		.longOpt("keep-names")
        		.hasArg()
        		.build();  
        Option option_ke = Option.builder("ke")
        		.required(false)
        		.desc("The file with those expressions to be kept unchanged")
        		.longOpt("keep-expressions")
        		.hasArg()
        		.build(); 
        Option option_d = Option.builder("d")
                .required(false)
                .desc("set debug mode")
                .longOpt("debug")
                .build();
        Options options = new Options();
        CommandLineParser parser = new DefaultParser();
        options.addOption(option_i);
        options.addOption(option_o);
        options.addOption(option_m);
        options.addOption(option_kn);
        options.addOption(option_ke);
        options.addOption(option_d);
        
        try {
            commandLine = parser.parse(options, args);
            
            if(commandLine.hasOption('d')) {
            	debug = true;
            	System.out.println("Modalita' debug: ON");
            }
            	
            if(debug) {
	            System.out.print("Input file: ");
	            System.out.println(commandLine.getOptionValue("i"));
            }
            
            inputFile = commandLine.getOptionValue("i");
            if(!inputFile.endsWith(".docx"))  {
            	ParseException pe = new ParseException("ERRORE: l'input file '" + inputFile + "' deve avere estensione \".docx\"");
            	throw pe;
            }

            /* attribuisco nome di default a docx generato in output se non specificato dall'utente */
            String defaultOutput = commandLine.getOptionValue("i").replaceAll("\\.docx$", "-result\\.docx");
            if(debug) {
	            System.out.print("Output file: ");      
	            System.out.println(commandLine.getOptionValue("o", defaultOutput));
            }
            outputFile = commandLine.getOptionValue("o", defaultOutput);
			outputFileAssociations = outputFile.replaceAll("\\.docx$", "-associations\\.txt");
            
            keepUnchangedExprFile = commandLine.getOptionValue("ke");
            
            /* 
             * Verifico che tutti i nominativi passati come argomento siano ben formati: 
             * - Possono essere presenti da 1 a 10 nomi ed 1 cognome obbligatoriamente
             * - I nomi sono separati tra loro da ':' e dal cognome da ';'
             * - I nomi possono contenere lettere minuscole e maiuscole (anche accentate) e l'apostrofo 
             * - I cognomi contengono gli stessi caratteri dei nomi ed anche spazi bianchi e separatori non visibili
             * 
             * Nota bene:
             *  - Un nominativo preceduto da "!" non deve essere minimizzato
             * 
             * Esempi di nominativi validi:
             * - "Lorenzo;Amorosa"
             * - "Lorenzo:Mario;Amorosa"
             * - "Lorenzo:Mario;De Amorosa"
             * - "L'òrénzò;D'Amorosa sa sa"
             * - "!Lorenzo;Amorosa"
             *  
             */
            
            // I nominativi sono o solo specificati via file o solo specificati via args
            if(commandLine.getArgs().length > 0 && (commandLine.hasOption('m') || commandLine.hasOption("kn"))) {
            	ParseException pe = new ParseException("ERRORE: i nominativi devono essere o solo specificati via file o solo specificati via args");
            	throw pe;
            }
            
            List<String> nominativi = new ArrayList<>();
            if(commandLine.getArgs().length > 0) {
            	nominativi = Arrays.asList(commandLine.getArgs())
            		.stream()
            		.filter(x -> x.charAt(0) != '!')
            		.collect(Collectors.toList());
            }
            else {
            	try {
            		minimizeFile = commandLine.getOptionValue("m");
            		if(minimizeFile != null) {
            			String toMinimize;
	        			BufferedReader bf = new BufferedReader(new FileReader(minimizeFile));
	        			toMinimize = bf.readLine();
	        			if(toMinimize != null)
	        				nominativi.add(toMinimize.trim());
	        			while(toMinimize != null) {
	        				toMinimize = bf.readLine();
	        				if(toMinimize != null)
	        					nominativi.add(toMinimize.trim());
	        			}
	        			bf.close();
            		}
        		} catch (IOException e) {
        			e.printStackTrace();
        			System.exit(7);
        		}
            }

            if(debug) {
            	System.out.println();
            	if(nominativi.size() > 0)
	                System.out.println("Numero persone di cui minimizzare i dati: " + nominativi.size());
            	else
            		System.out.println("Riconoscimento automatico dei nominativi");
            	System.out.println();
            }
            
            String cognome, nomiString;
        	List<String> nomi;
        	int id = 0;
            for (String argument : nominativi) {
                if(argument.matches(Persona.NOMINATIVO_USER)) {
            		nomiString = argument.split(Pattern.quote(";"))[0];
            		cognome = argument.split(Pattern.quote(";"))[1];
            		nomi = Arrays.asList(nomiString.split(Pattern.quote(":")));
            		if(nomi.size() > 10) {
                    	System.out.println("WARNING: sono inseribili un massimo di 10 nomi per persona, non minimizzo i dati" +
                    	" della persona con cognome: " + cognome + ". Procedo con l'elaborazione.");
                    	break;
            		}         	
                	//aggiungo la persona alla lista
                	persone.add(new Persona(cognome, nomi, id));
                }
                else {
                	ParseException pe = new ParseException(argument + ": input non ben formato");
                	throw pe;
                }
            }
            
            List<String> keepChanged_string = new ArrayList<>();
            if(commandLine.getArgs().length > 0) {
            	keepChanged_string = Arrays.asList(commandLine.getArgs())
            		.stream()
            		.filter(x -> x.charAt(0) == '!')
            		.collect(Collectors.toList());
            }
            else {
            	try {
            		keepUnchangedNamesFile = commandLine.getOptionValue("kn");
            		if(keepUnchangedNamesFile != null) {
            			String toKeep;
	        			BufferedReader bf = new BufferedReader(new FileReader(keepUnchangedNamesFile));
	        			toKeep = bf.readLine();
	        			if(toKeep != null)
	        				keepChanged_string.add(toKeep.trim());
	        			while(toKeep != null) {
	        				toKeep = bf.readLine();
	        				if(toKeep != null)
	        					keepChanged_string.add(toKeep.trim());
	        			}
	        			bf.close();
            		}
        		} catch (IOException e) {
        			e.printStackTrace();
        			System.exit(8);
        		}
            }
            
            if(debug) {
            	if(keepChanged_string.size() > 0) 
	                System.out.println("Numero persone di cui NON minimizzare i dati: " + keepChanged_string.size());
            	else
            		System.out.println("Minimizzazione di tutti i dati individuati");
            	System.out.println();
            }

            id = -1;
            for (String argument : keepChanged_string) {
                if(argument.matches(Persona.NOMINATIVO_USER)) {
            		nomiString = argument.split(Pattern.quote(";"))[0].replaceAll("!", "");
            		cognome = argument.split(Pattern.quote(";"))[1];
            		nomi = Arrays.asList(nomiString.split(Pattern.quote(":")));
            		if(nomi.size() > 10) {
                    	System.out.println("WARNING: sono inseribili un massimo di 10 nomi per persona, non minimizzo i dati" +
                    	" della persona con cognome: " + cognome + ". Procedo con l'elaborazione.");
                    	break;
            		}         	
                	//aggiungo la persona alla lista
                	keepUnchanged.add(new Persona(cognome, nomi, id)); 
                }
                else {
                	ParseException pe = new ParseException(argument + ": input non ben formato");
                	throw pe;
                }
            }
            
        }
        catch (ParseException exception) {
            System.out.print("Parse error: ");
            System.out.println(exception.getMessage());
            System.exit(1);
        }
        if(debug) {
        	System.out.println("Input validato, inizio processamento"); 
        }
       
	}

	
    public static void main(String[] args) {

    	/*
    	 * inizializzazione log4j
    	 * la variabile "log4jPrints" a false disabilita stampe verbose di logging inerenti ad apertura file .docx (di cui si occupa interamente docx4j)
    	 */
    	BasicConfigurator.configure();
    	
    	if(!log4jPrints) { 
			@SuppressWarnings("unchecked")
			List<Logger> loggers = Collections.<Logger>list(LogManager.getCurrentLoggers());
	    	loggers.add(LogManager.getRootLogger());
	    	for (Logger logger : loggers) {
	    	    logger.setLevel(Level.OFF);
	    	}
	    	disableDocxWarning();
    	}
    	
    	parametersCheck(args);
    	
    	File doc = new File(inputFile);
    	WordprocessingMLPackage wordMLPackage = null;
		try {
			//carico il documento .docx
			wordMLPackage = WordprocessingMLPackage.load(doc);
		} catch (Docx4JException e) {
			System.out.println("Eccezione in WordprocessingMLPackage.load: caricamento fallito di " + inputFile);
			e.printStackTrace();
			System.exit(2);
		}
		
		//ottengo document.xml che contiene i dati di interesse
    	MainDocumentPart mainDocumentPart = wordMLPackage.getMainDocumentPart();
    	RelationshipsPart rp = wordMLPackage.getMainDocumentPart().getRelationshipsPart();
    	// XPath: https://www.w3.org/TR/xpath-30/
    	String runNodesXPath = "//w:r";
    	List<Object> runNodes = null, tmp_runs;
		try {
			//ottengo tutti i nodi contenenti il testo visibile nel documento
			// Esempi: https://github.com/plutext/docx4j/search?q=getJAXBNodesViaXPath+in%3Afile&type=Code
			runNodes = mainDocumentPart.getJAXBNodesViaXPath(runNodesXPath, true);
			//ottengo tutti i rimanenti nodi contenenti testo presenti in altri file xml
			for (Relationship r : rp.getRelationships().getRelationship()) {	
				switch(r.getType()) {
					case Namespaces.HEADER:
						tmp_runs = ((HeaderPart) (rp.getPart(r))).getJAXBNodesViaXPath(runNodesXPath, true);
						break;
					case Namespaces.FOOTER:
						tmp_runs = ((FooterPart) (rp.getPart(r))).getJAXBNodesViaXPath(runNodesXPath, true);
						break;
					case Namespaces.ENDNOTES:
						tmp_runs = ((EndnotesPart) (rp.getPart(r))).getJAXBNodesViaXPath(runNodesXPath, true);
						break;
					case Namespaces.FOOTNOTES:
						tmp_runs = ((FootnotesPart) (rp.getPart(r))).getJAXBNodesViaXPath(runNodesXPath, true);
						break;
					case Namespaces.COMMENTS:
						tmp_runs = ((CommentsPart) (rp.getPart(r))).getJAXBNodesViaXPath(runNodesXPath, true);
						break;
					default:
						tmp_runs = null;
				}
				
				// unifico le strutture dati, blocchi semantici distinti sono separati successivamente con approccio bottom-up
				if(tmp_runs != null) 
					for(Object t : tmp_runs)
						runNodes.add(t);
				
			}
		} catch (XPathBinderAssociationIsPartialException | JAXBException e) {
			System.out.println("Eccezione in mainDocumentPart.getJAXBNodesViaXPath, lettura fallita da " + inputFile);
			e.printStackTrace();
			System.exit(3);
		}
		
		//elaboro i nodi contenuti in document.xml e minimizzo i dati personali contenuti
		Elaborator elab = null;
		if (keepUnchangedExprFile != null)
			elab = new Elaborator(runNodes, outputFileAssociations, persone, keepUnchanged, keepUnchangedExprFile);
		else
			elab = new Elaborator(runNodes, outputFileAssociations, persone, keepUnchanged);
		elab.work();
 	
    	//salvataggio nuovo file docx modificato
    	File exportFile = new File(outputFile);
        try {  	
			wordMLPackage.save(exportFile);
		} catch (Docx4JException e) {
			System.out.println("Eccezione in wordMLPackage.save, salvataggio fallito di " + outputFile);
			e.printStackTrace();
			System.exit(4);
		} 
        
        if(debug)
        	System.out.println("Success! Output file: " + outputFile);
        return;       
    }

	/*
     * Per disabilitare il Warning:
     * 
     * WARNING: An illegal reflective access operation has occurred
	   WARNING: Illegal reflective access by com.sun.xml.bind.v2.runtime.reflect.opt.Injector$1 (file:/home/lorenzo/.m2/repository/com/sun/xml/bind/jaxb-impl/2.2.7/jaxb-impl-2.2.7.jar) to method java.lang.ClassLoader.defineClass(java.lang.String,byte[],int,int)
       WARNING: Please consider reporting this to the maintainers of com.sun.xml.bind.v2.runtime.reflect.opt.Injector$1
       WARNING: Use --illegal-access=warn to enable warnings of further illegal reflective access operations
       WARNING: All illegal access operations will be denied in a future release
     * 
     */
    private static void disableDocxWarning() {
        System.err.close();
        System.setErr(System.out);
    }   
		
}
