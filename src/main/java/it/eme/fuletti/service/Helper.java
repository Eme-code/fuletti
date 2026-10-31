package it.eme.fuletti.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

public class Helper {
    //Qui ci sono i metodi fatti da ChatGPT

    private static final String URL_REST_PRODOTTI = "https://www.sergiobonelli.it/wp-json/wp/v2/product";
    private long ultimoAvvioRichiestaNanos = 0L;

    public long getUltimoAvvioRichiestaNanos() { return ultimoAvvioRichiestaNanos; }

    private HttpResponse<String> bonelliGet(HttpClient client, String url, String accept) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(40)).header("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64) " + "AppleWebKit/537.36 (KHTML, like Gecko) " + "Chrome/137.0.0.0 Safari/537.36").header("Accept", accept).header("Accept-Language", "it-IT,it;q=0.9,en;q=0.8").GET().build();
        HttpResponse<String> response = null;
        int tentativo = 0;
        while (response == null && tentativo < 4) {
            tentativo++;
            try {
                ultimoAvvioRichiestaNanos = System.nanoTime();
                response = client.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                if (tentativo >= 4) throw e;
                long attesaSecondi = 1L << tentativo;
                System.err.println("Connessione interrotta durante la richiesta a " + request.uri() + ". Tentativo " + tentativo + " fallito." + " Nuovo tentativo tra " + attesaSecondi + " secondi.");
                Thread.sleep(attesaSecondi * 1000L);
            }
        }
        if (response.statusCode() == 429) {
            String retryAfter = response.headers().firstValue("Retry-After").orElse(null);
            if (retryAfter == null || !retryAfter.matches("\\d+")) throw new IOException("HTTP 429 da " + url + "; Retry-After assente o non numerico: " + retryAfter);
            long secondi = Long.parseLong(retryAfter) + 1;
            System.out.println("HTTP 429. Attendo " + secondi + " secondi prima di riprovare: " + url);
            Thread.sleep(secondi * 1000L);
            ultimoAvvioRichiestaNanos = System.nanoTime();
            response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
        if (response.statusCode() != 200) {
            String retryAfter = response.headers().firstValue("Retry-After").orElse("non indicato");
            throw new IOException("Errore HTTP " + response.statusCode() + " da " + url + "; Retry-After: " + retryAfter);
        }
        return response;
    }

    public List<String> trovaUrlProdottiTestata(String urlTestata, HttpClient client) throws IOException, InterruptedException {
        Set<String> pagineVisitate = new LinkedHashSet<>();
        Set<String> prodotti = new LinkedHashSet<>();
        String urlPagina = urlTestata;
        while (urlPagina != null && pagineVisitate.add(urlPagina)) {
            String html = bonelliGet(client, urlPagina, "text/html,application/xhtml+xml,*/*;q=0.8").body();
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(html, urlPagina);
            org.jsoup.select.Elements schede = doc.select("div.product-card");
            if (schede.isEmpty()) throw new IOException("Nessuna scheda product-card trovata in " + urlPagina);
            for (org.jsoup.nodes.Element scheda : schede) {
                org.jsoup.nodes.Element link = scheda.selectFirst("a[href*='/prodotto/']");
                if (link != null) {
                    String urlProdotto = link.absUrl("href");
                    if (!urlProdotto.isBlank()) prodotti.add(urlProdotto);
                }
            }
            urlPagina = null;
            for (org.jsoup.nodes.Element link : doc.select("a.pagination-arrow")) {
                String etichetta = link.attr("aria-label").toLowerCase(Locale.ROOT);
                if (etichetta.contains("successiva") || "next".equalsIgnoreCase(link.attr("rel"))) {
                    String successiva = link.absUrl("href");
                    if (!successiva.isBlank() && !pagineVisitate.contains(successiva)) urlPagina = successiva;
                    break;
                }
            }
        }
        if (prodotti.isEmpty()) throw new IOException("Nessun URL prodotto trovato nella testata " + urlTestata);
        System.out.println("Individuati " + prodotti.size() + " prodotti in " + pagineVisitate.size() + " pagine su "+urlTestata);
        return new ArrayList<>(prodotti);
    }

    public String slugDaUrl(String url) {
        String path = URI.create(url).getRawPath();
        path = path.replaceAll("/+$", "");
        return path.substring(path.lastIndexOf('/') + 1);
    }

    public Map<String, JsonNode> recuperaProdottiRest(List<String> urlProdotti, HttpClient client, ObjectMapper mapper) throws IOException, InterruptedException {
        Map<String, JsonNode> prodottiPerSlug = new LinkedHashMap<>();
        // Ricava gli slug dagli URL, eliminando eventuali duplicati.
        Set<String> slugUnici = new LinkedHashSet<>();
        for (String url : urlProdotti) {
            slugUnici.add(slugDaUrl(url));
        }
        List<String> slugs = new ArrayList<>(slugUnici);
        // Recupera i prodotti in gruppi di massimo 15.
        final int dimensioneBatch = 100;//15;
        String campi = "id,slug,title,link,featured_media,acf.id_articolo_legacy,acf.occhiello,acf.sottotitolo,acf.ribbon,product_cat,product_brand,_links,_embedded";
        for (int inizio = 0; inizio < slugs.size(); inizio += dimensioneBatch) {
            int fine = Math.min(inizio + dimensioneBatch, slugs.size());
            List<String> batch = slugs.subList(inizio, fine);
            StringBuilder query = new StringBuilder("?");
            for (String slug : batch) {
                if (query.length() > 1) query.append("&");
                query.append(URLEncoder.encode("slug[]", StandardCharsets.UTF_8)).append("=").append(URLEncoder.encode(slug, StandardCharsets.UTF_8));
            }
            query.append("&per_page=100");
            query.append("&_embed=1");
            query.append("&_fields=");
            query.append(URLEncoder.encode(campi, StandardCharsets.UTF_8));
            HttpResponse<String> response = bonelliGet(client,URL_REST_PRODOTTI + query,"application/json");
            JsonNode prodotti = mapper.readTree(response.body());
            if (prodotti == null || !prodotti.isArray()) throw new IOException("La REST WordPress non ha restituito un array di prodotti.");
            for (JsonNode prodotto : prodotti) {
                JsonNode slugNode = prodotto.get("slug");
                if (slugNode == null || slugNode.isNull()) continue;
                prodottiPerSlug.put(slugNode.asText(),prodotto);
            }
            // Breve pausa fra i batch, per limitare le richieste ravvicinate.
            if (fine < slugs.size()) Thread.sleep(1000);
        }
        System.out.println("Slug richiesti alla REST: " + slugs.size());
        System.out.println("Prodotti restituiti dalla REST: " + prodottiPerSlug.size());
        return prodottiPerSlug;
    }

    private JsonNode estraiProductJsonLd(String html, ObjectMapper mapper) throws IOException {
        org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(html);
        for (org.jsoup.nodes.Element script : doc.select("script[type]")) {
            String tipo = script.attr("type").split(";")[0].trim();
            if (!"application/ld+json".equalsIgnoreCase(tipo)) continue;
            String contenuto = script.data();
            if (contenuto == null || contenuto.isBlank()) contenuto = script.html();
            try {
                JsonNode radice = mapper.readTree(contenuto);
                JsonNode product = cercaNodoProduct(radice);
                if (product != null) return product;
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                // Uno script JSON-LD non valido non deve impedire di esaminare gli script successivi.
            }
        }
        return null;
    }

    private JsonNode cercaNodoProduct(JsonNode nodo) {
        if (nodo == null || nodo.isNull()) return null;
        if (nodo.isObject()) {
            JsonNode tipo = nodo.get("@type");
            if (tipo != null) {
                if (tipo.isTextual() && "Product".equals(tipo.asText())) return nodo;
                if (tipo.isArray()) {
                    for (JsonNode elementoTipo : tipo) {
                        if (elementoTipo.isTextual()&& "Product".equals(elementoTipo.asText())) return nodo;
                    }
                }
            }
            for (JsonNode figlio : nodo) {
                JsonNode trovato = cercaNodoProduct(figlio);
                if (trovato != null) return trovato;
            }
        } else if (nodo.isArray()) {
            for (JsonNode elemento : nodo) {
                JsonNode trovato = cercaNodoProduct(elemento);
                if (trovato != null) return trovato;
            }
        }
        return null;
    }

    public String estraiProprietaProduct(JsonNode product, String nomeProprieta) {
        if (product == null) return null;
        JsonNode proprieta = product.path("additionalProperty");
        if (!proprieta.isArray()) return null;
        for (JsonNode elemento : proprieta) {
            if (!nomeProprieta.equals(elemento.path("name").asText())) continue;
            JsonNode valore = elemento.get("value");
            if (valore != null && !valore.isNull()) {
                String testo = valore.asText();
                if (!testo.isBlank()) return testo;
            }
        }
        return null;
    }

    public String estraiPrezzoProduct(JsonNode product) {
        if (product == null) return null;
        JsonNode offers = product.get("offers");
        if (offers == null || offers.isNull()) return null;
        if (offers.isArray()) {
            offers = offers.isEmpty() ? null : offers.get(0);
        }
        if (offers == null || !offers.isObject()) return null;
        JsonNode prezzo = offers.get("price");
        if (prezzo == null || prezzo.isNull()) return null;
        String valore = prezzo.asText();
        return valore.isBlank() ? null : valore;
    }

    //Metodi test
    public void verificaJsonLd() {
        HttpClient client = HttpClient.newHttpClient();
        ObjectMapper mapper = new ObjectMapper();
        String url = "https://www.sergiobonelli.it/prodotto/messaggeri-delle-tenebre/";
        String html = null;
        try {
            html = bonelliGet(client,url,"text/html,application/xhtml+xml,*/*;q=0.8").body();
            JsonNode product = estraiProductJsonLd(html, mapper);
            if (product == null) {
                System.out.println("JSON-LD Product non trovato.");
                return;
            }
            System.out.println("Collana: " + estraiProprietaProduct(product, "pa_collana"));
            System.out.println("Numero: " + estraiProprietaProduct(product, "pa_numero"));
            System.out.println("Data uscita: " + estraiProprietaProduct(product, "pa_data-di-uscita"));
            System.out.println("Prezzo: " + estraiPrezzoProduct(product));
        } catch (IOException e) { throw new RuntimeException(e);
        } catch (InterruptedException e) { throw new RuntimeException(e);
        }
    }

    public Map<Long, String> recuperaCopertineRest(Map<String, JsonNode> prodottiPerSlug, HttpClient client, ObjectMapper mapper) throws IOException, InterruptedException {
        Map<Long, String> copertine = new LinkedHashMap<>();
        List<Long> ids = new ArrayList<>();
        for (JsonNode prodotto : prodottiPerSlug.values()) {
            long id = prodotto.path("id").asLong(0);
            if (id > 0 && !ids.contains(id)) ids.add(id);
        }
        final int dimensioneBatch = 15;
        for (int inizio = 0; inizio < ids.size(); inizio += dimensioneBatch) {
            int fine = Math.min(inizio + dimensioneBatch, ids.size());
            String include = ids.subList(inizio, fine).stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
            String campi = "id,_links,_embedded";
            String url = URL_REST_PRODOTTI+"?include="+URLEncoder.encode(include,StandardCharsets.UTF_8)+"&per_page=100&_embed=1&_fields="+URLEncoder.encode(campi,StandardCharsets.UTF_8);
            HttpResponse<String> response = bonelliGet(client, url, "application/json");
            JsonNode prodotti = mapper.readTree(response.body());
            if (!prodotti.isArray()) throw new IOException("La REST non ha restituito un array durante il recupero copertine.");
            for (JsonNode prodotto : prodotti) {
                long id = prodotto.path("id").asLong(0);
                String urlCopertina = prodotto.path("_embedded").path("wp:featuredmedia").path(0).path("source_url").asText("");
                if (id > 0 && !urlCopertina.isBlank()) copertine.put(id, urlCopertina);
            }
            if (fine < ids.size()) Thread.sleep(1000);
        }
        System.out.println("Copertine REST recuperate: " + copertine.size() + " su " + ids.size());
        return copertine;
    }

    public JsonNode recuperaProductJsonLd(String url, HttpClient client, ObjectMapper mapper)throws IOException, InterruptedException {
        String html = bonelliGet(client,url,"text/html,application/xhtml+xml,*/*;q=0.8").body();
        return estraiProductJsonLd(html, mapper);
    }
}