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
        long millisIniz = System.currentTimeMillis();
        List<Albo> albi = new ArrayList<Albo>();
        ArrayList<Albo> albiTutti = (ArrayList<Albo>) albiDaSito();
        for (Albo albo : albiTutti) {
            System.out.println("Albo "+albo.getTitolo()+" presente="+alboPresente(albo));
            if (!alboPresente(albo)) albi.add(albo);
        }
        long durata = System.currentTimeMillis() - millisIniz;
        System.out.println("Filtrati da "+albiTutti.size()+" a "+albi.size()+" albi");
        System.out.println("Impiegati "+durata+" ms. Scrivo su db.");
        alboRepository.saveAll(albi);
    }

    private List<Albo> albiDaSito() {
        System.out.println("Cerco albi su sito");
        ArrayList<Testata> testateTutte = (ArrayList<Testata>) testataRepository.findAttivi();
        Parser parser = new Parser(testateTutte, alboRepository);
        List<Albo> albiTutti = parser.trovaAlbiTutti();
        return albiTutti;
    }

    private boolean alboPresente(Albo albo) {
        System.out.println("Cerco albo n. "+albo.getNumero()+" di "+albo.getTestata().getNome());
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