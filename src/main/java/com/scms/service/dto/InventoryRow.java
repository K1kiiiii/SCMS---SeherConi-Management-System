package com.scms.service.dto;

import java.math.BigDecimal;

public class InventoryRow {
    private int materialId;
    private String materialCode; // "Šifra / Artikl" can be material code or id
    private String name;
    private String unit;
    private double inflow; // Ulaz (količina)
    private double outflow; // Izlaz (količina)
    private double balance; // Stanje
    // monetary values for inflow/outflow
    private double inflowValue; // value of imports (currency)
    private double outflowValue; // value of exports (currency)

    public InventoryRow() {}

    public int getMaterialId() { return materialId; }
    public void setMaterialId(int materialId) { this.materialId = materialId; }

    public String getMaterialCode() { return materialCode; }
    public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public double getInflow() { return inflow; }
    public void setInflow(double inflow) { this.inflow = inflow; }

    public double getOutflow() { return outflow; }
    public void setOutflow(double outflow) { this.outflow = outflow; }

    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }

    public double getInflowValue() { return inflowValue; }
    public void setInflowValue(double inflowValue) { this.inflowValue = inflowValue; }

    public double getOutflowValue() { return outflowValue; }
    public void setOutflowValue(double outflowValue) { this.outflowValue = outflowValue; }
}
