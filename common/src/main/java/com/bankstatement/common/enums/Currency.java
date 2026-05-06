package com.bankstatement.common.enums;

public enum Currency {
    BRL("Real Brasileiro", "R$", 2),
    USD("Dólar Americano", "$", 2),
    EUR("Euro", "€", 2),
    GBP("Libra Esterlina", "£", 2),
    JPY("Iene Japonês", "¥", 0),
    ARS("Peso Argentino", "$", 2),
    CLP("Peso Chileno", "$", 0),
    COP("Peso Colombiano", "$", 2),
    MXN("Peso Mexicano", "$", 2),
    BTC("Bitcoin", "₿", 8);

    private final String description;
    private final String symbol;
    private final int decimalPlaces;

    Currency(String description, String symbol, int decimalPlaces) {
        this.description = description;
        this.symbol = symbol;
        this.decimalPlaces = decimalPlaces;
    }

    public String getDescription() { return description; }
    public String getSymbol() { return symbol; }
    public int getDecimalPlaces() { return decimalPlaces; }
}
