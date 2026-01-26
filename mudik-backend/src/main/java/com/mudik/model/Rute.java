package com.mudik.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Rute extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long rute_id;
    public String asal;
    public String tujuan;
    public int kuota_tersisa;

    public static Rute findById(Long id) {
        return find("rute_id", id).firstResult();
    }
}