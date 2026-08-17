package it.eme.fuletti.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.eme.fuletti.model.Albo;
import it.eme.fuletti.model.Testata;
import it.eme.fuletti.repository.AlboRepository;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
public class Parser {
	
	private ArrayList<Testata> testate;
    AlboRepository alboRepository;

	public List<Albo> trovaAlbiTutti() {
		List<Albo> albiTutti = new ArrayList<>();
		for (Testata testata : this.testate) {
            Set titoliPresenti = tuttiTitoliTestata(testata);
            List<Albo> albiTuttiTestata = trovaAlbiRest(testata, titoliPresenti);
            albiTutti.addAll(albiTuttiTestata);
            /*System.out.println("Test apostrofo: L’Uomo");
            System.out.println("Test accentata: à è ì ò ù");
            System.out.println("Test Unicode: € — …");
            System.out.println("Charset default = " + java.nio.charset.Charset.defaultCharset());
            System.out.println("System.out = " + System.out);
            System.err.println("ERR apostrofo: L’Uomo");
            System.err.println("ERR accentate: à è ì ò ù");
            System.err.println("ERR Unicode: € — …");*/
		}
		System.out.println("Trovati in totale "+albiTutti.size()+" albi");
		return albiTutti;
	}

    private List<Albo> trovaAlbiRest(Testata testata, Set<String> tuttiTitoli) {
        List<Albo> albi = new ArrayList<>();
        String urlTestata = testata.getUrl_sbe();
        String slug = urlTestata.substring(urlTestata.lastIndexOf('=') + 1);
        String url = "https://www.sergiobonelli.it/wp-json/wc/store/v1/products";
        try {
            HttpClient client = HttpClient.newHttpClient();
            String query = "?attributes[0][attribute]=pa_collana"+"&attributes[0][slug]="+URLEncoder.encode(slug, StandardCharsets.UTF_8)+"&per_page=100"+"&_fields=id,name,attributes,prices,images";
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url + query))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36")
                    .header("Accept", "application/json").header("Accept-Language", "it-IT,it;q=0.9,en;q=0.8").GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) {
                String riprovaDopo = response.headers().firstValue("Retry-After").orElse(null);
                System.out.println("HTTP 429. Riprova dopo " + riprovaDopo);
            }
            //TODO Applicare attesa per i secondi dati da riprovaDopo
            //String body = response.body();
            /*System.out.println("Content-Type = " + response.headers().firstValue("Content-Type").orElse("non presente"));
            System.out.println("Titolo grezzo = " + body.substring(0, Math.min(1000, body.length())));
            System.out.println("URL REST = " + url + query);*/
            System.out.println("Status = " + response.statusCode());
            //response.headers().map().forEach((nome, valori) -> System.out.println(nome + " = " + valori));
            //System.out.println("Body = " + response.body());
            if (response.statusCode() != 200) throw new RuntimeException("Errore REST: HTTP " + response.statusCode());
            ObjectMapper mapper = new ObjectMapper();
            JsonNode prodotti = mapper.readTree(response.body());
            for (JsonNode prodotto : prodotti) {
                String titoloCodificato = prodotto.get("name").asText();
                String titolo = org.jsoup.parser.Parser.unescapeEntities(titoloCodificato, false).replaceAll("’", "'");
                /*System.out.println("titoloCodificato = " + titoloCodificato);
                System.out.println("titolo           = " + titolo);
                System.out.println("titolo con apostrofo ASCII = " + titolo.replace('\u2019', '\''));*/
                if (!tuttiTitoli.contains(titolo) && !titolo.contains("? Variant") && !titolo.contains("1 Variant") && !titolo.contains("– Variant")) {
                    Albo albo = new Albo();
                    albo.setTestata(testata);
                    albo.setTitolo(titolo);
                    // Attributi
                    JsonNode attributes = prodotto.get("attributes");
                    for (JsonNode attribute : attributes) {
                        String nome = attribute.get("name").asText();
                        JsonNode terms = attribute.get("terms");
                        if (terms == null || !terms.isArray() || terms.isEmpty()) continue;
                        String valore = terms.get(0).get("name").asText();
                        if ("Numero".equals(nome)) albo.setNumero(Integer.parseInt(valore));
                        else if ("Data di uscita".equals(nome)) {
                            Timestamp uscita = testoToTimestamp(testoToValore(valore));
                            albo.setUscita(uscita);
                            System.out.println("Titolo "+titolo+" nuovo uscito il "+uscita.toString());
                        }
                    }
                    // Prezzo
                    JsonNode prices = prodotto.get("prices");
                    if (prices != null && prices.has("price")) {
                        String prezzoStr = prices.get("price").asText();
                        BigDecimal prezzo = new BigDecimal(prezzoStr);
                        prezzo = prezzo.divide(new BigDecimal(100)).setScale(2, BigDecimal.ROUND_DOWN);
                        albo.setPrezzo(prezzo);
                    }
                    // Copertina
                    JsonNode images = prodotto.get("images");
                    if (images != null && images.isArray() && !images.isEmpty()) {
                        String urlCopertina = images.get(0).get("src").asText();
                        albo.setUrl_copertina(urlCopertina);
                    }
                    albo.setPreso(false);
                    albi.add(albo);
                } else System.out.println("Titolo "+titolo+" già presente");
            }
        } catch (IOException | InterruptedException e) { throw new RuntimeException("Errore durante interrogazione REST", e); }
        System.out.println("Trovati " + albi.size() +" albi per la testata " + testata.getNome());
        return albi;
    }

    private Set<String> tuttiTitoliTestata(Testata testata) {
        Set<String> titoli = new HashSet<String>();
        List<Albo> albi = alboRepository.findByTestata(testata);
        for (Albo albo : albi) {
            titoli.add(albo.getTitolo());
        }
        return titoli;
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
}