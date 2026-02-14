package com.mudik.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;


@Entity
public class Terminal extends PanacheEntity {
    public String nama;
    public String kota;
    public Double latitude;
    public Double longitude;


}