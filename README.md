# DocxAnonymizer
Stand-alone Java tool to anonymize OOXML Documents (docx). This software helps to make docx documents compliant to [General Data Protection Regulation 2016/679 (**GDPR**)](https://eur-lex.europa.eu/legal-content/IT/TXT/?uri=uriserv:OJ.L_.2016.119.01.0001.01.ITA&toc=OJ:L:2016:119:TOC).

The suffix *base* helps to distinguish it from [DocxAnonymizer](https://github.com/Lostefra/DocxAnonymizer). Both software perform same tasks, but [DocxAnonymizer-base](https://github.com/Lostefra/DocxAnonymizer-base) can work only on a single machine, whereas [DocxAnonymizer](https://github.com/Lostefra/DocxAnonymizer) can work either locally or on a cluster with Apache Spark.

## Workflow

Given a **complex** and **rich-formatted docx** file, the program extrapolates all the text, it **anonymizes** its content and then it saves the new docx **without altering its structure**.

**Nominatives** (i.e. sequences of names and surnames) are **replaced** with anonymous **IDs** and multiple occurrences of the same nominatives are replaced with the same ID. 

The **detection** of the nominatives can be **either on demand or automatic**. In fact, the user can express as input the sequences of names-surname to anonymize; in case these sequences are not given, the program automatically starts searching for nominatives in the document using dictionaries of Italian and English names. A pattern-based approach is adopted to detect nominatives. [Here my thesis for further details](https://github.com/Lostefra/DocxAnonymizer-base/blob/master/docs/TESI_Lorenzo_Mario_Amorosa.pdf).

In brief, the [program](https://github.com/Lostefra/DocxAnonymizer-base/blob/4b7a2aa461b80a935c0066c71dd222028a9348b1/src/main/java/docxAnonymizer/App.java#L76) accepts the following options:
```sh
  -i  <inputFile>  [the docx input file to anonymize]
  -o  <outputFile> [the docx output file generated, if not expressed given by default]
  -m  <minimize>   [the file with names and surnames to minimize. It must contain one expression per line of the form: "<name1>:<name2>:[...]:<nameN>;<surname>", if not expressed the program will perform automatic detection of nominatives]
  -kn <keepNames>  [the file with names and surnames to keep unchanged (no minimization). It must contain one expression per line of the form: "<name1>:<name2>:[...]:<nameN>;<surname>"]
  -ke <keepExpr>   [the file with those expressions to be kept unchanged (not nominatives)]
  -d  <debug>      [increase verbosity]
```
  
## Notes

In this repository you can also find an useful [bash script](https://github.com/Lostefra/DocxAnonymizer-base/tree/master/tools) to view and modify XML parts of a docx file and a [summary of docx standard ECMA-376 documentation](https://github.com/Lostefra/DocxAnonymizer-base/blob/master/docs/WordML) easy to navigate in a web browser after you have downloaded it (its [index.html](https://github.com/Lostefra/DocxAnonymizer-base/blob/master/docs/WordML/index.html)). 

DocxAnonymizer leverage on the [docx4j](https://www.docx4java.org/trac/docx4j) library.

This project was the case study of my bachelor's thesis.
