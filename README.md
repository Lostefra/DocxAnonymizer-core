# DocxAnonymizer
Stand-alone Java tool to anonymize OOXML Documents (DOCX). This sotware implements [TODO GDPR]

[TODO spiegare nome base -> new project to increase performance, based on Apache Spark with same functionalities]

Given a **complex** and **rich-formatted DOCX** file, the program extrapolates all the text, it **anonymizes** its content and then it saves the new DOCX **without altering its structure**.

**Nominatives** (i.e. sequences of names and surnames) are **replaced** with anonymous **IDs** and multiple occurrences of the same nominatives are replaced with the same ID. 

The **detection** of the nominatives can be **either on demand or automatic**. In fact, the user can express as input the sequences of names-surname to anonymize; in case these sequences are not given, the program automatically starts searching for nominatives in the document using dictionaries of Italian and English names. A pattern-based approach is adopted to detect nominatives.

In brief, the [program](https://github.com/Lostefra/DocxAnonymizer-base/blob/4b7a2aa461b80a935c0066c71dd222028a9348b1/src/main/java/docxAnonymizer/App.java#L76) accepts the following options ([here my thesis for further details](https://github.com/Lostefra/DocxAnonymizer-base/blob/master/docs/TESI_Lorenzo_Mario_Amorosa.pdf)):
[TODO]
-i <inputFile>   [Obbligatorio, file Docx del quale si vogliono minimizzare i dati contenuti]
-o <outputFile>  [Facoltativo, file Docx prodotto da Docx Anonymizer; se non espresso impostato a default]
-m <minimize>    [Facoltativo, file contenente riga per riga i nominativi da minimizzare, nella forma: "<nome1>:<nome2>:[...]:<nomeN>;<cognome>"]
                 [  Nota bene: qualora il file fosse assente Docx Anonymizer impieghera' i dizionari nella ricerca dei nominativi               ]
-kn <keepNames>  [Facoltativo, file contenente riga per riga i nominativi da NON minimizzare, nella forma di cui sopra]
-ke <keepExpr>   [Facoltativo, file contenente riga per riga delle espressioni da NON minimizzare (non nominativi)]
                 [  Nota bene: alcune espressioni sono gia' impostate a default in config/keep_unchanged.txt      ]
-d <debug>       [Facoltativo, se tale opzione e' impostata sono stampate a video informazioni sull'esecuzione]

In alternativa, i nominativi da minimizzare possono essere forniti direttamente da linea di comando nello stesso formato indicato precedentemente
Cio' e' possibile anche per i nominativi da NON minimizzare, andra' in tal caso anteposto al primo nome un punto esclamativo '!'

 */

[accennare a docx4j, license]

This project was the case study of my bachelor's thesis.
