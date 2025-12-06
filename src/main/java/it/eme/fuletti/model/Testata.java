package it.eme.fuletti.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "testata")
public class Testata {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "TESTATA_SEQ")
    @SequenceGenerator(name="TESTATA_SEQ",sequenceName="seq_id_testata", allocationSize = 1)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "nome", nullable = false)
    private String nome;

    @Column(name = "personaggio", nullable = true)
    private String personaggio;

    @Column(name = "frequenza", nullable = true)
    private int frequenza;

    @Column(name = "url_sbe", nullable = true)
    private String url_sbe;

    @Column(name = "attivo", nullable = false)
    private boolean attivo;
}