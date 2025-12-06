package it.eme.fuletti.repository;

import it.eme.fuletti.model.Testata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestataRepository extends JpaRepository<Testata, Integer> {

    @Query(value = "select t.* from testata t where t.attivo order by t.id", nativeQuery = true)
    List<Testata> findAttivi();
}
