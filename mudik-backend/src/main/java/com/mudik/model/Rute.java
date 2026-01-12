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
    public Long route_id;
    public String asal;
    public String tujuan;

    // INI YANG KURANG TADI:
    // Tambahkan field ini supaya 'rute.kuota_tersisa' tidak merah lagi
    public int kuota_tersisa;


}