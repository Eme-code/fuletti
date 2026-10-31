package it.eme.fuletti.service;

import it.eme.fuletti.model.Albo;
import it.eme.fuletti.model.Testata;
import it.eme.fuletti.repository.AlboRepository;
import it.eme.fuletti.repository.ParametroRepository;
import it.eme.fuletti.repository.TestataRepository;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class FulettiService {

    @Autowired
    AlboRepository alboRepository;

    @Autowired
    TestataRepository testataRepository;

    @Autowired
    ParametroRepository parametroRepository;

    public List<Albo> albiDaDb() {
        Integer primoAnno = parametroRepository.findInteroByNome("primo anno");
        Timestamp primoStamp = anno2Timestamp(primoAnno);
        List<Albo> albi = alboRepository.findByUscitaAfter(primoStamp);
        return albi;
    }

    public void invertiPreso(int id) {
        Albo albo = alboRepository.findById(id).get();
        boolean preso = albo.isPreso();
        albo.setPreso(!preso);
        alboRepository.save(albo);
    }

    public void aggiornaDb() {
        //long millisIniz = System.currentTimeMillis();
        long tempoInizio = System.nanoTime();
        List<Albo> albi = albiDaSito();
        //long durata = System.currentTimeMillis() - millisIniz;
        //System.out.println("Trovati "+albi.size()+" albi in "+durata+" ms. Scrivo su db.");
        long durataSecondi = (System.nanoTime() - tempoInizio) / 1_000_000_000L;
        System.out.print("Finita ricerca albi su sito. Trovati "+albi.size()+" albi. ");
        System.out.printf("Durata totale: %d ore, %d minuti e %d secondi%n"+". Scrivo su db.", durataSecondi / 3600, (durataSecondi % 3600) / 60, durataSecondi % 60);
        alboRepository.saveAll(albi);
        System.out.println(" Db aggiornato.");
    }

    public void test() {
        System.out.println("Inizio test");
        ArrayList<Testata> testateTutte = (ArrayList<Testata>) testataRepository.findAttivi();
        Parser parser = new Parser(testateTutte, alboRepository);
        parser.test();
        System.out.println("Test finito");
    }

    private List<Albo> albiDaSito() {
        System.out.println("Cerco albi su sito");
        ArrayList<Testata> testateTutte = (ArrayList<Testata>) testataRepository.findAttivi();
        Parser parser = new Parser(testateTutte, alboRepository);
        List<Albo> albiTutti = parser.trovaAlbiTutti();
        return albiTutti;
    }

    private boolean alboPresente(Albo albo) {
        //System.out.println("Cerco albo n. "+albo.getNumero()+" di "+albo.getTestata().getNome());
        List<Albo> trovati = alboRepository.findByNumeroAndTitoloAndTestata(albo.getNumero(), albo.getTitolo(), albo.getTestata());//alboRepository.findByNumeroAndTestata(albo.getNumero(), albo.getTestata());//alboRepository.albiPresenti(albo.getNumero(), albo.getTestata().getNome());
        return !trovati.isEmpty();
    }

    private Timestamp anno2Timestamp(int anno) {
        String dataStr = anno+"/01/01";
        SimpleDateFormat formatoData = new SimpleDateFormat("yyyy/MM/dd");
        Date data = new Date();
        try {
            data = formatoData.parse(dataStr);
        } catch (ParseException e) { throw new RuntimeException(e); }
        long primiMillis = data.getTime();
        Timestamp stamp = new Timestamp(primiMillis);
    return stamp;
    }
}