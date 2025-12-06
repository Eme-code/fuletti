package it.eme.fuletti.repository;

import it.eme.fuletti.model.Parametro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ParametroRepository extends JpaRepository<Parametro, String> {

    Parametro findByNome(String nome);

    @Query(value = "select valore_intero from parametro where nome = ?1", nativeQuery = true)
    Integer findInteroByNome(String nome);
}
