package it.eme.fuletti.repository;

import it.eme.fuletti.model.Albo;
import it.eme.fuletti.model.Testata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Repository
public interface AlboRepository extends JpaRepository<Albo, Integer> {

    @Query(value = "select albo.id as id_albo, * from albo inner join testata on albo.id_testata = testata.id where EXTRACT(year FROM uscita) >= (select valore_intero from parametro where nome = 'primo anno') order by uscita desc, preso desc", nativeQuery = true)
    ArrayList<Albo> ultimi();

    @Query("select a from Albo a where a.uscita > ?1 order by uscita desc, preso desc")
    List<Albo> findByUscitaAfter(Timestamp uscita);

    List<Albo> findByTestata(Testata testata);

    List<Albo> findByNumeroAndTitoloAndTestata(Integer numero, String titolo, Testata testata);
}