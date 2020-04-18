#!/bin/bash
# Lorenzo Amorosa

# Funzione per aprire il file word/document.xml contenuto in un docx, modificarlo, poi aggiornare il docx e aprirlo con libreoffice
#TODO: modificare comportamento se file di input passato come path del tipo "./file.docx" o "demoXML/file.docx"
if [[ "$#" -ne 1 || ! -e "$1" || ! "$1" =~ docx$ || "$1" =~ ^\. || "$1" =~ ^[a-zA-Z0-9]+\/[a-zA-Z0-9]+ || -d /tmp/word ]]; then
	if [[ "$#" -ne 1 ]]; then
		echo "Passare un solo argomento in input"
	fi
	if ! [[ -e "$1" ]]; then
		echo "Il file $1 non esiste"
	fi
	if ! [[ "$1" =~ docx$ ]]; then
		echo "Il file $1 non ha estensione docx"
	fi
	if [[ "$1" =~ ^\. || "$1" =~ ^[a-zA-Z0-9]+\/[a-zA-Z0-9]+ ]]; then
		echo "Il file $1 deve essere passato con path assoluto o con il solo filename. In future versioni il file sara' accettato anche se fornito con path relativo"
	fi
	if [[ -d /tmp/word ]]; then
		echo "Non posso eseguire unzip: la cartella /tmp/word e' esistente. Rimuoverla per eseguire docxedit"
	fi
	echo "Usage: docxedit file.docx"
else
	#variabile usata per tornare a fine script nella cartella di provenienza
	curdir=$(pwd)
	#DOCX restera' uguale all'argomento passato se si lancia docxedit nella cartella che contiene il documento docx
	DOCX=$1
	#P restera' uguale al path corrente se si lancia docxedit nella cartella che contiene il documento docx
	P=$(pwd)
	#il seguente blocco serve per trattare docx passati con path
	if [[ $DOCX =~ \/ ]]; then
		P=$(echo $DOCX | awk 'BEGIN{FS=OFS="/"}{NF--; print}')			
		DOCX=$(echo $DOCX | awk -F '/' '{print $NF}')
		echo "P: $P" 
		echo "DOCX: $DOCX" 
		cd $P
	fi
	unzip "$DOCX" "word/document.xml" -d /tmp 
	cd /tmp
	gedit "word/document.xml" && zip --update "$P/$DOCX"  "word/document.xml" 
	# remove this line to just keep overwriting files in /tmp
	rm -f "word/document.xml" # or remove -f if you want to confirm
	rmdir "word"
	cd "$curdir"
	libreoffice -o "$P/$DOCX"
fi

