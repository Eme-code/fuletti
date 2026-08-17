package it.eme.fuletti.controller;

import it.eme.fuletti.model.Albo;
import it.eme.fuletti.service.FulettiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/")
public class FulettiController {

    private List<Albo> albi = new ArrayList<Albo>();

    @Autowired
    FulettiService service;

    @GetMapping
    public String main(Model modello) {
        //modello.addAttribute("message", "Hello Thymeleaf");
        System.out.println("Sono nel controller e il modello è "+modello.toString());
        return "index";
    }

    @GetMapping("/inizia")
    public String inizia(Model modello) {
        albi = service.albiDaDb();
        int quantiDaPrendere = 0;
        BigDecimal prezzoTotale = new BigDecimal(0);
        for (Albo albo : albi) {
            if (!albo.isPreso() && albo.getUscita().before(new Timestamp(System.currentTimeMillis()))) {
                quantiDaPrendere ++;
                prezzoTotale = prezzoTotale.add(albo.getPrezzo()); //+= albo.getPrezzo();
            }
        }
        modello.addAttribute("listaAlbi", albi);
        modello.addAttribute("quantiDaPrendere", quantiDaPrendere);
        modello.addAttribute("prezzoTotale", prezzoTotale);
        return "principale";
    }

    @GetMapping("/invertiPreso")
    public String invertiPreso(@RequestParam(name="id", required=false, defaultValue="") int id, Model modello) {
        service.invertiPreso(id);
        return "redirect:/inizia";
    }

    @GetMapping("/aggiornaDb")
    public String aggiornaDb(Model modello) {
        System.out.println("Aggiorno db");
        service.aggiornaDb();
        albi = service.albiDaDb();
        modello.addAttribute("listaAlbi", albi);
        return "redirect:/";
    }

    @GetMapping("/prova")
    public String prova() {
        return "Prova";
    }

    //TODO Endpoint e relativi metodi per correggere url delle copertine
}