package it.eme.fuletti.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import it.eme.fuletti.model.Albo;
import it.eme.fuletti.model.Testata;
import it.eme.fuletti.repository.AlboRepository;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

@NoArgsConstructor
@AllArgsConstructor
public class Parser {
	
	private ArrayList<Testata> testate;
    AlboRepository alboRepository;

	public List<Albo> trovaAlbiTutti() {
		List<Albo> albiTutti = new ArrayList<Albo>();
		for (Testata testata : this.testate) {
            String nomeTestata = testata.getNome();
            List<String> indirizziAlbi = new ArrayList<String>();
            Document documentoTestata = null;
            try {
                documentoTestata = connettiDocumento(testata.getUrl_sbe());
            } catch (IOException e) { e.printStackTrace(); }
            Elements elementiTutti = documentoTestata.getElementsByClass("product-title-link");
            System.out.println("Trovati "+ elementiTutti.size()+" albi per la testata "+nomeTestata+" con id "+testata.getId());
            Set titoliPresenti = tuttiTitoliTestata(testata);
            System.out.println("Già presenti "+titoliPresenti.size()+ " albi per "+nomeTestata);
            //scansionaElementi(documentoTestata, "issue-number");
            for (Element elementoTestata : elementiTutti) {
                String titolo = elementoTestata.text().replace('’', '\'');
                System.out.println("Leggo albo index "+elementiTutti.indexOf(elementoTestata)+"/"+elementiTutti.size()+" "+titolo);
                if (!titoliPresenti.contains(titolo)) {
                    Albo albo = new Albo();
                    albo.setTestata(testata);
                    albo.setTitolo(titolo);
                    System.out.println("Elemento da elementi testata: " + nomeTestata);
                    System.out.println(elementoToString(elementoTestata));
                    String urlAlbo = elementoTestata.attr("href");
                    Document documentoAlbo = null;
                    try {
                        documentoAlbo = connettiDocumento(urlAlbo);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    Elements elementiMetaItem = documentoAlbo.getElementsByClass("meta-item mb-2");
                    System.out.println("Trovati " + elementiMetaItem.size() + " elementi di classe meta-item mb-2. Url=" + urlAlbo);
                    for (Element elementoMetaItem : elementiMetaItem) {
                        System.out.println("Elemento di classe meta-item: " + elementoToString(elementoMetaItem));
                        String testo = elementoMetaItem.text().trim();
                        if (testo.startsWith("Numero:")) albo.setNumero(Integer.parseInt(testoToValore(testo)));
                        else if (testo.startsWith("Data di uscita:"))
                            albo.setUscita(testoToTimestamp(testoToValore(testo)));
                    }
                    albo.setPrezzo(estraiPrezzo(documentoAlbo.getElementsByClass("price-row")));
                    albo.setUrl_copertina(estraiUrlCopertina(documentoAlbo.getElementsByClass("thumbnail-item")));
                    System.out.println("Albo completo: " + albo.toString());
                    albiTutti.add(albo);
                }
            }
		}
		System.out.println("Trovati in totale "+albiTutti.size()+" albi");
		return albiTutti;
	}

	private List<String> trovaIndirizziAlbi(String indirizzoTestata) {
		List<String> indirizziAlbi = new ArrayList<String>();
		Document documento = null;
		try {
			documento = connettiDocumento(indirizzoTestata);
		} catch (IOException e) { e.printStackTrace(); }
		Elements elementiIndirizzi = documento.getElementsByClass("product-card");
		System.out.println("Trovati indirizzi di "+elementiIndirizzi.size()+" albi");
		for (Element elementoIndirizzo : elementiIndirizzi) {
			String indirizzo = elementoIndirizzo.child(0).attr("href");
			if (!indirizzo.contains("shop.sergiobonelli.it")) indirizzo = "https://shop.sergiobonelli.it/"+indirizzo;
			indirizziAlbi.add(indirizzo);
		}
		return indirizziAlbi;
	}

	private Document connettiDocumento(String indirizzo) throws IOException {
		return Jsoup.connect(indirizzo).data("query", "Java").userAgent("Mozilla").cookie("auth", "token").timeout(60000).post();
	}

    private Set<String> tuttiTitoliTestata(Testata testata) {
        Set<String> titoli = new HashSet<String>();
        List<Albo> albi = alboRepository.findByTestata(testata);
        for (Albo albo : albi) {
            titoli.add(albo.getTitolo());
        }
        return titoli;
    }

    //Usare per esaminare la struttura della pagina
    private void scansionaElementi(Document documento, String nomeClasse) {
        Elements elementi = documento.getElementsByClass(nomeClasse);
        System.out.println("Scansiono "+elementi.size()+" elementi di classe "+nomeClasse);
        for (Element elemento : elementi) {
            System.out.println(elementoToString(elemento));
        }
    }
	
    private BigDecimal estraiPrezzo(Elements elementi) {
        if (elementi.isEmpty()) return BigDecimal.ZERO;
        String testo = elementi.first().text().trim();
        String stringa = testo.substring(0, testo.indexOf(" ")).replace(',', '.');
        return BigDecimal.valueOf(Double.valueOf(stringa));
    }

    private String estraiUrlCopertina(Elements elementi) {
        String testo = elementi.first().html();
        int inizioUrl = testo.indexOf("\"")+1;
        int fineUrl = testo.indexOf("\"", inizioUrl);
        return testo.substring(inizioUrl, fineUrl);
    }

    private Timestamp testoToTimestamp(String testo) {
        SimpleDateFormat formatoData = new SimpleDateFormat("dd/MM/yyyy");
        Date data = new Date();
        try {
            data = formatoData.parse(testo);
        } catch (ParseException e) { throw new RuntimeException(e); }
        long millis = data.getTime();
        return new Timestamp(millis);
    }

    private String testoToValore(String testo) {
        return testo.substring(testo.indexOf(':')+1).trim();
    }

    private String elementoToString(Element elemento) {
        return "Elemento "+elemento.text()+" nodo nome='"+elemento.nodeName()+"', nodo valore trimmato='"+elemento.nodeValue().trim()+"', classe='"+elemento.className()+"', attr href="+elemento.attr("href")+", text='"+elemento.text()+"', baseUri="+elemento.baseUri()+", data="+elemento.data()+", html="+elemento.html();
    }
}