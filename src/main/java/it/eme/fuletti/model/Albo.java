package it.eme.fuletti.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Entity
@Table(name = "albo")
public class Albo {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ALBO_SEQ")
    @SequenceGenerator(name="ALBO_SEQ",sequenceName="seq_id_albo", allocationSize = 1)
    @Column(name = "id", nullable = false)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "id_testata")
    private Testata testata;

    @Column(name = "numero", nullable = true)
    private int numero;

    @Column(name = "titolo", nullable = true)
    private String titolo;

    @Column(name = "uscita", nullable = true)
    private Timestamp uscita;

    @Column(name = "prezzo", nullable = true)
    private BigDecimal prezzo;

    @Column(name = "url_copertina", nullable = true)
    private String url_copertina;

    @Column(name = "preso", nullable = true)
    private boolean preso;

    public boolean isUgualee(Albo altro) {
        return (this.testata.equals(altro.getTestata()) && this.numero == altro.getNumero() && this.titolo.equalsIgnoreCase(altro.getTitolo()));
    }
}