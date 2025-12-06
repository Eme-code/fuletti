package it.eme.fuletti.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "parametro")
public class Parametro {

    @Id
    @Column(name = "nome", nullable = false)
    private String nome;

    @Column(name = "valore_testo", nullable = true)
    private String valoreTesto;

    @Column(name = "valore_intero", nullable = true)
    private Integer valoreIntero;
}